package com.universalremote.adapters.vidaa

import com.universalremote.adapters.common.TofuTrustManager
import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.PairingRequest
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.network.wol.WakeOnLanSender
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Hisense VIDAA: el TV expone un broker MQTT sobre TLS en 36669 con credenciales de fábrica.
 * La primera vez el TV muestra un código de 4 dígitos que se publica en `.../authenticationcode`.
 *
 * Limitación: los firmwares VIDAA más recientes (2023+) exigen certificado de cliente y contraseña
 * dinámica; con ellos la conexión falla con un error claro en lugar de quedarse colgada.
 */
class VidaaAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("VidaaAdapter requiere DeviceTransport.Lan")

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private val clientId = "UniversalRemote-" + initialProfile.id.filter { it.isLetterOrDigit() }.take(12)
    private val topics = VidaaTopics(clientId)
    private var trust = TofuTrustManager(initialProfile.pinnedCertificateSha256)
    private var client: MqttClient? = null

    @Volatile
    private var authResult: CompletableDeferred<String>? = null

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.NAVIGATION, RemoteCapability.MEDIA,
    )

    override fun supports(command: RemoteCommand): Boolean =
        command.capability in capabilities && (command == RemoteCommand.PowerOn || VidaaKeys.keyFor(command) != null)

    override suspend fun connect() = mutex.withLock {
        val current = _state.value
        if (current == ConnectionState.Connected || current is ConnectionState.AwaitingPairing) return@withLock
        try {
            connectLocked()
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    override suspend fun submitPairingCode(code: String) = mutex.withLock {
        val mqtt = client ?: throw RemoteError.NotConnected()
        val pin = code.trim()
        if (pin.length != PIN_LENGTH || !pin.all(Char::isDigit)) throw RemoteError.InvalidCode("El código son 4 dígitos")
        val answer = CompletableDeferred<String>().also { authResult = it }
        val accepted = try {
            publish(mqtt, topics.authCode, VidaaTopics.authPayload(pin))
            VidaaTopics.isAuthAccepted(withTimeout(AUTH_TIMEOUT_MS) { answer.await() })
        } catch (e: TimeoutCancellationException) {
            throw RemoteError.Timeout(e)
        } catch (t: Throwable) {
            throw fail(t)
        } finally {
            authResult = null
        }
        if (!accepted) throw RemoteError.InvalidCode("Código incorrecto; revisa el que muestra el TV")
        updateProfile(
            profile.copy(
                pairingToken = clientId,
                pinnedCertificateSha256 = trust.seenFingerprint ?: profile.pinnedCertificateSha256,
            ),
        )
        _state.value = ConnectionState.Connected
    }

    override suspend fun disconnect() = mutex.withLock {
        closeClient()
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            wakeAndConnect()
            if (command == RemoteCommand.PowerOn) return@withLock
        }
        val mqtt = client?.takeIf { _state.value == ConnectionState.Connected } ?: throw RemoteError.NotConnected()
        val key = VidaaKeys.keyFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)
        try {
            publish(mqtt, topics.sendKey, key)
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    // ------------------------------------------------------------------ sesión

    private suspend fun connectLocked() {
        closeClient()
        _state.value = ConnectionState.Connecting
        trust = TofuTrustManager(profile.pinnedCertificateSha256)
        val mqtt = withContext(Dispatchers.IO) {
            MqttClient("ssl://${lan.host}:${lan.port}", clientId, MemoryPersistence()).apply {
                setCallback(callback)
                connect(
                    MqttConnectOptions().apply {
                        userName = USERNAME
                        password = PASSWORD.toCharArray()
                        socketFactory = trust.sslContext().socketFactory
                        isHttpsHostnameVerificationEnabled = false
                        isCleanSession = true
                        connectionTimeout = CONNECT_TIMEOUT_S
                        keepAliveInterval = KEEP_ALIVE_S
                    },
                )
                subscribe(arrayOf(topics.authResult, topics.tvState), intArrayOf(0, 0))
            }
        }
        client = mqtt
        if (profile.pairingToken == null) {
            // Con un cliente desconocido el TV responde a gettvstate mostrando el código en pantalla.
            publish(mqtt, topics.getTvState, "")
            _state.value = ConnectionState.AwaitingPairing(
                PairingRequest(PairingRequest.Method.Pin(PIN_LENGTH), "Escribe el código de 4 dígitos que muestra el TV Hisense"),
            )
        } else {
            _state.value = ConnectionState.Connected
        }
    }

    private suspend fun wakeAndConnect() {
        val direct = runCatching { connectLocked() }.onFailure { if (it is CancellationException) throw it }
        if (direct.isSuccess) return
        val mac = profile.macAddress ?: throw fail(direct.exceptionOrNull()!!)
        _state.value = ConnectionState.Waking
        wakeOnLan.wake(mac, lastKnownHost = lan.host)
        for (attempt in 1..WAKE_POLL_ATTEMPTS) {
            delay(WAKE_POLL_INTERVAL_MS)
            if (runCatching { connectLocked() }.onFailure { if (it is CancellationException) throw it }.isSuccess) return
        }
        throw fail(RemoteError.Timeout())
    }

    private val callback = object : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            if (_state.value == ConnectionState.Connected) {
                _state.value = ConnectionState.Failed(RemoteError.Network(cause ?: MqttException(MqttException.REASON_CODE_CONNECTION_LOST.toInt())))
            }
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            if (topic == topics.authResult) authResult?.complete(String(message.payload, Charsets.UTF_8))
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    }

    private suspend fun publish(mqtt: MqttClient, topic: String, payload: String) = withContext(Dispatchers.IO) {
        mqtt.publish(topic, payload.toByteArray(Charsets.UTF_8), 1, false)
    }

    private fun fail(t: Throwable): RemoteError {
        val error = when (t) {
            is MqttException -> when (t.reasonCode.toShort()) {
                MqttException.REASON_CODE_FAILED_AUTHENTICATION, MqttException.REASON_CODE_NOT_AUTHORIZED ->
                    RemoteError.UnsupportedDevice("HISENSE_VIDAA_NUEVO (el TV exige certificado de cliente)")
                else -> RemoteError.from(t.cause ?: t, lan.host).let { if (it is RemoteError.Protocol) RemoteError.Unreachable(lan.host, t) else it }
            }
            else -> RemoteError.from(t, lan.host)
        }
        closeClient()
        _state.value = ConnectionState.Failed(error)
        return error
    }

    private fun closeClient() {
        client?.let { c ->
            runCatching { if (c.isConnected) c.disconnectForcibly(DISCONNECT_TIMEOUT_MS) }
            runCatching { c.close() }
        }
        client = null
    }

    private fun updateProfile(updated: DeviceProfile) {
        if (updated != profile) {
            profile = updated
            _events.tryEmit(AdapterEvent.ProfileUpdated(updated))
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): VidaaAdapter
    }

    private companion object {
        // Credenciales de fábrica del broker de VIDAA (públicas, iguales en todos los TV).
        const val USERNAME = "hisenseservice"
        const val PASSWORD = "multimqttservice"
        const val PIN_LENGTH = 4
        const val AUTH_TIMEOUT_MS = 10_000L
        const val CONNECT_TIMEOUT_S = 5
        const val KEEP_ALIVE_S = 30
        const val DISCONNECT_TIMEOUT_MS = 500L
        const val WAKE_POLL_ATTEMPTS = 20
        const val WAKE_POLL_INTERVAL_MS = 1_000L
    }
}
