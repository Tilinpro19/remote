package com.universalremote.adapters.roku

import com.universalremote.adapters.common.PointerAccumulator
import com.universalremote.core.di.LanHttpClient
import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.network.http.await
import com.universalremote.network.wol.WakeOnLanSender
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
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
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParserException

/**
 * Roku External Control Protocol: HTTP plano en el puerto 8060.
 *
 * ECP es sin estado; "conectado" significa que `/query/device-info` respondió. OkHttp reutiliza la
 * conexión keep-alive, de modo que las pulsaciones sucesivas no repiten el handshake TCP.
 */
class RokuEcpAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val client: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("RokuEcpAdapter requiere DeviceTransport.Lan")

    private val baseUrl: HttpUrl = HttpUrl.Builder().scheme("http").host(lan.host).port(lan.port).build()

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private val pointer = PointerAccumulator()

    @Volatile
    private var isTv = true

    override val capabilities: Set<RemoteCapability>
        get() = buildSet {
            addAll(RemoteCapability.entries)
            // Los reproductores Roku (no TV) no tienen sintonizador.
            if (!isTv) remove(RemoteCapability.CHANNELS)
        }

    override fun supports(command: RemoteCommand): Boolean = when (command) {
        is RemoteCommand.TouchDelta, is RemoteCommand.Scroll, is RemoteCommand.Text -> true
        else -> command.capability in capabilities && RokuKey.of(command) != null
    }

    override suspend fun connect() = mutex.withLock { connectLocked() }

    override suspend fun disconnect() = mutex.withLock {
        pointer.reset()
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            powerOnWithWake()
            return@withLock
        }
        when (command) {
            is RemoteCommand.Text -> {
                var offset = 0
                while (offset < command.text.length) {
                    val codePoint = command.text.codePointAt(offset)
                    keypress(RokuKey.literal(codePoint))
                    offset += Character.charCount(codePoint)
                }
            }
            is RemoteCommand.TouchDelta ->
                pointer.accumulate(command.dx, command.dy).forEach { keypress(RokuKey.of(it)) }
            is RemoteCommand.Scroll ->
                pointer.accumulate(0f, command.dy).forEach { keypress(RokuKey.of(it)) }
            else -> keypress(RokuKey.of(command) ?: throw RemoteError.UnsupportedCommand(command.wireName))
        }
    }

    // ------------------------------------------------------------------ sesión

    private suspend fun connectLocked() {
        if (_state.value == ConnectionState.Connected) return
        _state.value = ConnectionState.Connecting
        try {
            applyDeviceInfo(fetchDeviceInfo())
            _state.value = ConnectionState.Connected
        } catch (e: CancellationException) {
            _state.value = ConnectionState.Idle
            throw e
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            _state.value = ConnectionState.Failed(error)
            throw error
        }
    }

    /**
     * Encendido robusto: si el equipo responde se envía `PowerOn`; si no, Magic Packet con la MAC
     * aprendida y sondeo de `/query/device-info` hasta que la pila de red despierte.
     */
    private suspend fun powerOnWithWake() {
        val reachable = runCatching { applyDeviceInfo(fetchDeviceInfo()) }
            .onFailure { if (it is CancellationException) throw it }
            .isSuccess
        if (!reachable) {
            val mac = profile.macAddress ?: run {
                val error = RemoteError.Unreachable(lan.host)
                _state.value = ConnectionState.Failed(error)
                throw error
            }
            _state.value = ConnectionState.Waking
            wakeOnLan.wake(mac, lastKnownHost = lan.host)
            waitUntilReachable()
        }
        _state.value = ConnectionState.Connected
        keypress(RokuKey.POWER_ON)
    }

    private suspend fun waitUntilReachable() {
        repeat(WAKE_POLL_ATTEMPTS) { attempt ->
            delay(WAKE_POLL_INTERVAL_MS)
            try {
                applyDeviceInfo(fetchDeviceInfo())
                return
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // El equipo aún está arrancando su pila Wi-Fi; se reintenta hasta agotar la ventana.
                if (attempt == WAKE_POLL_ATTEMPTS - 1) {
                    val error = RemoteError.Timeout(t)
                    _state.value = ConnectionState.Failed(error)
                    throw error
                }
            }
        }
    }

    private fun applyDeviceInfo(info: RokuDeviceInfo) {
        isTv = info.isTv
        val updated = profile.copy(
            macAddress = info.macAddress ?: profile.macAddress,
            model = info.model ?: profile.model,
            serialNumber = info.serialNumber ?: profile.serialNumber,
        )
        if (updated != profile) {
            profile = updated
            _events.tryEmit(AdapterEvent.ProfileUpdated(updated))
        }
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun fetchDeviceInfo(): RokuDeviceInfo {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("query/device-info").build())
            .get()
            .build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw RemoteError.Protocol("device-info respondió HTTP ${response.code}")
            }
            val body = response.body ?: throw RemoteError.Protocol("device-info sin cuerpo")
            return try {
                RokuDeviceInfo.parse(body.byteStream())
            } catch (e: XmlPullParserException) {
                throw RemoteError.Protocol("device-info con XML inválido", e)
            }
        }
    }

    private suspend fun keypress(key: RokuKey) {
        val url = baseUrl.newBuilder()
            .addPathSegment("keypress")
            .addEncodedPathSegment(key.path)
            .build()
        val request = Request.Builder().url(url).post(EMPTY_BODY).build()
        try {
            client.newCall(request).await().use { response ->
                when {
                    response.isSuccessful -> Unit
                    response.code == 403 -> throw RemoteError.Protocol(
                        "Roku rechazó el comando: activa Ajustes › Sistema › Configuración avanzada › " +
                            "Control por aplicaciones móviles › Acceso a la red: Predeterminado",
                    )
                    else -> throw RemoteError.Protocol("keypress/$key respondió HTTP ${response.code}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            if (error !is RemoteError.Protocol) {
                // Fallo de transporte: se marca la sesión caída para que el gestor reconecte.
                _state.value = ConnectionState.Failed(error)
            }
            throw error
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): RokuEcpAdapter
    }

    private companion object {
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        const val WAKE_POLL_ATTEMPTS = 20
        const val WAKE_POLL_INTERVAL_MS = 1_000L
    }
}
