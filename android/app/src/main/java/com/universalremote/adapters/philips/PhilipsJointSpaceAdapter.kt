package com.universalremote.adapters.philips

import com.universalremote.adapters.common.DigestAuthenticator
import com.universalremote.adapters.common.TofuTrustManager
import com.universalremote.adapters.common.getJson
import com.universalremote.adapters.common.postBody
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
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
import org.json.JSONException
import org.json.JSONObject

/**
 * Philips con Saphi/Linux (JointSpace API v6): HTTPS autofirmado en 1926 con autenticación Digest.
 * Los Philips con Android TV entran por [com.universalremote.domain.model.DeviceKind.ANDROID_TV].
 *
 * Emparejamiento: `pair/request` devuelve `auth_key` y `timestamp`; el TV muestra un PIN y
 * `pair/grant` lo confirma firmado con HMAC-SHA1. Credenciales Digest: usuario = id del perfil,
 * contraseña = `auth_key` (guardada en [DeviceProfile.pairingToken]).
 */
class PhilipsJointSpaceAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val baseClient: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("PhilipsJointSpaceAdapter requiere DeviceTransport.Lan")

    private val baseUrl = "https://${lan.host}:${lan.port}/6"

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private val trust = TofuTrustManager(initialProfile.pinnedCertificateSha256)
    private val tlsClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .sslSocketFactory(trust.sslContext().socketFactory, trust)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private class PendingPairing(val authKey: String, val timestamp: Long)

    private var pending: PendingPairing? = null

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.CHANNELS,
        RemoteCapability.DIGITS, RemoteCapability.NAVIGATION, RemoteCapability.MEDIA,
    )

    override fun supports(command: RemoteCommand): Boolean =
        command.capability in capabilities &&
            (command == RemoteCommand.PowerOn || command == RemoteCommand.PowerOff || PhilipsKeys.nameFor(command) != null)

    override suspend fun connect() = mutex.withLock {
        val current = _state.value
        if (current == ConnectionState.Connected || current is ConnectionState.AwaitingPairing) return@withLock
        _state.value = ConnectionState.Connecting
        try {
            val key = profile.pairingToken
            if (key == null) {
                startPairing()
                return@withLock
            }
            try {
                powerState(key)
                _state.value = ConnectionState.Connected
            } catch (e: RemoteError.PairingRejected) {
                startPairing()
            }
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    override suspend fun submitPairingCode(code: String) = mutex.withLock {
        val session = pending ?: throw RemoteError.NotConnected()
        val pin = code.trim()
        val auth = JSONObject()
            .put("auth_AppId", "1")
            .put("pin", pin)
            .put("auth_timestamp", session.timestamp)
            .put("auth_signature", signature(session.timestamp.toString(), pin))
        val response = try {
            json(
                authed(session.authKey).postBody(
                    "$baseUrl/pair/grant",
                    JSONObject().put("auth", auth).put("device", deviceJson()).toString(),
                    contentType = JSON_TYPE,
                ),
            )
        } catch (e: RemoteError.PairingRejected) {
            throw RemoteError.InvalidCode("PIN incorrecto; revisa el código que muestra el TV")
        } catch (t: Throwable) {
            throw fail(t)
        }
        if (response.optString("error_id") != SUCCESS) {
            throw RemoteError.InvalidCode("PIN incorrecto; revisa el código que muestra el TV")
        }
        pending = null
        updateProfile(
            profile.copy(
                pairingToken = session.authKey,
                pinnedCertificateSha256 = trust.seenFingerprint ?: profile.pinnedCertificateSha256,
            ),
        )
        _state.value = ConnectionState.Connected
    }

    override suspend fun disconnect() = mutex.withLock {
        pending = null
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            powerOnWithWake()
            return@withLock
        }
        if (_state.value != ConnectionState.Connected) throw RemoteError.NotConnected()
        val key = profile.pairingToken ?: throw RemoteError.NotConnected()
        try {
            when (command) {
                RemoteCommand.PowerOn -> setPower(key, on = true)
                RemoteCommand.PowerOff -> setPower(key, on = false)
                else -> post(key, "input/key", JSONObject().put("key", PhilipsKeys.nameFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)))
            }
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            if (error !is RemoteError.Protocol && error !is RemoteError.UnsupportedCommand) {
                _state.value = ConnectionState.Failed(error)
            }
            throw error
        }
    }

    /** `true` encendido, `false` en reposo; `null` si no se pudo consultar. */
    override suspend fun isPoweredOn(): Boolean? {
        val key = profile.pairingToken ?: return null
        return runCatching { powerState(key) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }

    // ------------------------------------------------------------------ sesión

    private suspend fun startPairing() {
        val body = JSONObject()
            .put("scope", JSONArray().put("read").put("write").put("control"))
            .put("device", deviceJson())
        val response = json(tlsClient.postBody("$baseUrl/pair/request", body.toString(), contentType = JSON_TYPE))
        if (response.optString("error_id") != SUCCESS) {
            throw RemoteError.Protocol("El TV Philips rechazó la vinculación (${response.optString("error_id")})")
        }
        val authKey = response.optString("auth_key").ifEmpty { throw RemoteError.Protocol("El TV Philips no devolvió auth_key") }
        pending = PendingPairing(authKey, response.optLong("timestamp"))
        _state.value = ConnectionState.AwaitingPairing(
            PairingRequest(PairingRequest.Method.Pin(PIN_LENGTH), "Escribe el PIN de 4 dígitos que muestra el TV Philips"),
        )
    }

    private fun deviceJson() = JSONObject()
        .put("device_name", "UniversalRemote")
        .put("device_os", "Android")
        .put("app_name", "UniversalRemote")
        .put("type", "native")
        .put("app_id", "com.universalremote")
        .put("id", profile.id)

    private suspend fun powerOnWithWake() {
        val key = profile.pairingToken ?: throw RemoteError.NotConnected()
        val reachable = runCatching { powerState(key) }
            .onFailure { if (it is CancellationException || it is RemoteError.PairingRejected) throw it }
            .isSuccess
        if (!reachable) {
            val mac = profile.macAddress ?: throw fail(RemoteError.Unreachable(lan.host))
            _state.value = ConnectionState.Waking
            wakeOnLan.wake(mac, lastKnownHost = lan.host)
            var awake = false
            for (attempt in 1..WAKE_POLL_ATTEMPTS) {
                delay(WAKE_POLL_INTERVAL_MS)
                awake = runCatching { powerState(key) }.onFailure { if (it is CancellationException) throw it }.isSuccess
                if (awake) break
            }
            if (!awake) throw fail(RemoteError.Timeout())
        }
        _state.value = ConnectionState.Connected
        setPower(key, on = true)
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

    private fun authed(key: String): OkHttpClient =
        tlsClient.newBuilder().authenticator(DigestAuthenticator(profile.id, key)).build()

    private suspend fun powerState(key: String): Boolean =
        authed(key).getJson("$baseUrl/powerstate").optString("powerstate").equals("On", ignoreCase = true)

    private suspend fun setPower(key: String, on: Boolean) =
        post(key, "powerstate", JSONObject().put("powerstate", if (on) "On" else "Standby"))

    private suspend fun post(key: String, path: String, body: JSONObject) {
        authed(key).postBody("$baseUrl/$path", body.toString(), contentType = JSON_TYPE)
    }

    private fun json(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw RemoteError.Protocol("Respuesta inválida del TV Philips", e)
    }

    private fun signature(timestamp: String, pin: String): String {
        val mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(Base64.getDecoder().decode(SHARED_KEY_B64), "HmacSHA1"))
        }
        return Base64.getEncoder().encodeToString(mac.doFinal((timestamp + pin).toByteArray()))
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): PhilipsJointSpaceAdapter
    }

    companion object {
        /**
         * Secreto compartido público de JointSpace (no es un secreto del usuario): lo usan todos los
         * clientes abiertos para firmar `pair/grant`. Pendiente de verificar contra un TV real.
         */
        internal const val SHARED_KEY_B64 =
            "ZmVay1EQVFOaZhwQ4Kv81ypLAZNczV9sG4KkseXWn1NEk6cXmPKO/MCa9sryslvLCFMnNe4Z4CPXzToowvhHvA=="

        private const val SUCCESS = "SUCCESS"
        private const val JSON_TYPE = "application/json; charset=utf-8"
        private const val PIN_LENGTH = 4
        private const val WAKE_POLL_ATTEMPTS = 20
        private const val WAKE_POLL_INTERVAL_MS = 1_000L
    }
}
