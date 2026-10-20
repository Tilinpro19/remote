package com.universalremote.adapters.vizio

import com.universalremote.adapters.common.TofuTrustManager
import com.universalremote.adapters.common.getJson
import com.universalremote.adapters.common.putJson
import com.universalremote.core.di.LanHttpClient
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
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vizio SmartCast: API REST sobre HTTPS (7345; 9000 en firmwares antiguos) con certificado
 * autofirmado (TOFU). Emparejamiento por PIN de 4 dígitos que muestra el TV; el `AUTH_TOKEN`
 * resultante viaja en la cabecera `AUTH` y se guarda en [DeviceProfile.pairingToken].
 */
class VizioAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val baseClient: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("VizioAdapter requiere DeviceTransport.Lan")

    private val baseUrl = "https://${lan.host}:${lan.port}"

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private val trust = TofuTrustManager(initialProfile.pinnedCertificateSha256)
    private val client: OkHttpClient by lazy { tlsClient() }

    /** Token de la solicitud de emparejamiento en curso (lo devuelve `/pairing/start`). */
    private var pairingRequestToken: Int? = null

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.CHANNELS,
        RemoteCapability.NAVIGATION, RemoteCapability.MEDIA,
    )

    override fun supports(command: RemoteCommand): Boolean =
        command.capability in capabilities && VizioKeys.codeFor(command) != null

    override suspend fun connect() = mutex.withLock {
        val current = _state.value
        if (current == ConnectionState.Connected || current is ConnectionState.AwaitingPairing) return@withLock
        _state.value = ConnectionState.Connecting
        try {
            val token = profile.pairingToken
            if (token == null || !isTokenValid(token)) {
                startPairing()
                return@withLock
            }
            _state.value = ConnectionState.Connected
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    override suspend fun submitPairingCode(code: String) = mutex.withLock {
        val requestToken = pairingRequestToken ?: throw RemoteError.NotConnected()
        val pin = code.trim()
        val response = try {
            client.putJson(
                "$baseUrl/pairing/pair",
                JSONObject()
                    .put("DEVICE_ID", profile.id)
                    .put("CHALLENGE_TYPE", CHALLENGE_TYPE)
                    .put("RESPONSE_VALUE", pin)
                    .put("PAIRING_REQ_TOKEN", requestToken),
            )
        } catch (t: Throwable) {
            throw fail(t)
        }
        if (result(response) != SUCCESS) {
            // Se queda en AwaitingPairing: el TV sigue mostrando el PIN y se puede reintentar.
            throw RemoteError.InvalidCode("PIN incorrecto; revisa el código que muestra el TV")
        }
        val authToken = response.optJSONObject("ITEM")?.optString("AUTH_TOKEN").orEmpty()
        if (authToken.isEmpty()) throw RemoteError.Protocol("El TV Vizio no devolvió token")
        pairingRequestToken = null
        updateProfile(
            profile.copy(
                pairingToken = authToken,
                pinnedCertificateSha256 = trust.seenFingerprint ?: profile.pinnedCertificateSha256,
            ),
        )
        _state.value = ConnectionState.Connected
    }

    override suspend fun disconnect() = mutex.withLock {
        pairingRequestToken = null
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            powerOnWithWake()
            return@withLock
        }
        if (_state.value != ConnectionState.Connected) throw RemoteError.NotConnected()
        val token = profile.pairingToken ?: throw RemoteError.NotConnected()
        val (codeset, code) = VizioKeys.codeFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)
        try {
            keypress(token, codeset, code)
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            if (error !is RemoteError.Protocol) _state.value = ConnectionState.Failed(error)
            throw error
        }
    }

    /** `true` encendido, `false` en reposo; `null` si no se pudo consultar. */
    override suspend fun isPoweredOn(): Boolean? {
        val token = profile.pairingToken ?: return null
        return runCatching { powerMode(token) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }

    // ------------------------------------------------------------------ sesión

    private fun tlsClient(): OkHttpClient = baseClient.newBuilder()
        .sslSocketFactory(trust.sslContext().socketFactory, trust)
        .hostnameVerifier { _, _ -> true }
        .build()

    private suspend fun startPairing() {
        val response = client.putJson(
            "$baseUrl/pairing/start",
            JSONObject().put("DEVICE_ID", profile.id).put("DEVICE_NAME", DEVICE_NAME),
        )
        if (result(response) != SUCCESS) {
            throw RemoteError.Protocol("El TV Vizio rechazó iniciar el emparejamiento (${result(response)})")
        }
        pairingRequestToken = response.optJSONObject("ITEM")?.optInt("PAIRING_REQ_TOKEN")
            ?: throw RemoteError.Protocol("El TV Vizio no devolvió PAIRING_REQ_TOKEN")
        _state.value = ConnectionState.AwaitingPairing(
            PairingRequest(PairingRequest.Method.Pin(PIN_LENGTH), "Escribe el PIN de 4 dígitos que muestra el TV Vizio"),
        )
    }

    private suspend fun isTokenValid(token: String): Boolean = try {
        powerMode(token)
        true
    } catch (e: RemoteError.PairingRejected) {
        false
    }

    private suspend fun powerOnWithWake() {
        val token = profile.pairingToken ?: throw RemoteError.NotConnected()
        val reachable = runCatching { powerMode(token) }
            .onFailure { if (it is CancellationException || it is RemoteError.PairingRejected) throw it }
            .isSuccess
        if (!reachable) {
            val mac = profile.macAddress ?: throw fail(RemoteError.Unreachable(lan.host))
            _state.value = ConnectionState.Waking
            wakeOnLan.wake(mac, lastKnownHost = lan.host)
            var awake = false
            for (attempt in 1..WAKE_POLL_ATTEMPTS) {
                delay(WAKE_POLL_INTERVAL_MS)
                awake = runCatching { powerMode(token) }.onFailure { if (it is CancellationException) throw it }.isSuccess
                if (awake) break
            }
            if (!awake) throw fail(RemoteError.Timeout())
        }
        _state.value = ConnectionState.Connected
        val (codeset, code) = VizioKeys.codeFor(RemoteCommand.PowerOn)!!
        keypress(token, codeset, code)
    }

    private fun fail(t: Throwable): RemoteError {
        val error = RemoteError.from(t, lan.host)
        _state.value = ConnectionState.Failed(error)
        return error
    }

    private fun updateProfile(updated: DeviceProfile) {
        if (updated != profile) {
            profile = updated
            _events.tryEmit(AdapterEvent.ProfileUpdated(updated))
        }
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun powerMode(token: String): Boolean {
        val response = client.getJson("$baseUrl/state/device/power_mode", mapOf(AUTH_HEADER to token))
        checkAuth(response)
        return response.optJSONArray("ITEMS")?.optJSONObject(0)?.optInt("VALUE") == 1
    }

    private suspend fun keypress(token: String, codeset: Int, code: Int) {
        val key = JSONObject().put("CODESET", codeset).put("CODE", code).put("ACTION", "KEYPRESS")
        val response = client.putJson(
            "$baseUrl/key_command/",
            JSONObject().put("KEYLIST", JSONArray().put(key)),
            mapOf(AUTH_HEADER to token),
        )
        checkAuth(response)
    }

    /** Vizio responde HTTP 200 incluso con token inválido; el fallo va en `STATUS.RESULT`. */
    private fun checkAuth(response: JSONObject) {
        val result = result(response)
        if (result.contains("AUTH", ignoreCase = true)) throw RemoteError.PairingRejected()
    }

    private fun result(response: JSONObject): String =
        response.optJSONObject("STATUS")?.optString("RESULT").orEmpty().uppercase()

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): VizioAdapter
    }

    private companion object {
        const val AUTH_HEADER = "AUTH"
        const val SUCCESS = "SUCCESS"
        const val DEVICE_NAME = "UniversalRemote"
        const val CHALLENGE_TYPE = 1
        const val PIN_LENGTH = 4
        const val WAKE_POLL_ATTEMPTS = 20
        const val WAKE_POLL_INTERVAL_MS = 1_000L
    }
}
