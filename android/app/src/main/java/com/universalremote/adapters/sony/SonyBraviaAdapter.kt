package com.universalremote.adapters.sony

import com.universalremote.adapters.common.postBody
import com.universalremote.core.di.LanHttpClient
import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.MacAddress
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
import org.json.JSONException
import org.json.JSONObject

/**
 * Sony Bravia: teclas por IRCC-IP (SOAP en `/sony/IRCC`) y energía/estado por la API REST
 * (`/sony/system`). Ambas autentican con la cabecera `X-Auth-PSK`.
 *
 * El usuario fija la clave en el TV (Ajustes › Red › Control IP › Autenticación "Normal y
 * Pre-Shared Key") y la escribe en el diálogo de vinculación; se guarda en [DeviceProfile.pairingToken].
 */
class SonyBraviaAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val client: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("SonyBraviaAdapter requiere DeviceTransport.Lan")

    private val baseUrl = "http://${lan.host}:${lan.port}/sony"

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.CHANNELS,
        RemoteCapability.DIGITS, RemoteCapability.NAVIGATION, RemoteCapability.MEDIA,
    )

    override fun supports(command: RemoteCommand): Boolean =
        command.capability in capabilities &&
            (command == RemoteCommand.PowerOn || command == RemoteCommand.PowerOff || SonyKeys.codeFor(command) != null)

    override suspend fun connect() = mutex.withLock {
        val current = _state.value
        if (current == ConnectionState.Connected || current is ConnectionState.AwaitingPairing) return@withLock
        val psk = profile.pairingToken
        if (psk == null) {
            awaitPsk()
            return@withLock
        }
        _state.value = ConnectionState.Connecting
        try {
            rest("system", "getPowerStatus", psk = psk)
            _state.value = ConnectionState.Connected
            learnSystemInfo(psk)
        } catch (e: RemoteError.PairingRejected) {
            // La clave guardada ya no vale (se cambió en el TV): se pide otra.
            awaitPsk()
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    override suspend fun submitPairingCode(code: String) = mutex.withLock {
        val psk = code.trim()
        if (psk.isEmpty()) throw RemoteError.InvalidCode("La clave no puede estar vacía")
        try {
            rest("system", "getPowerStatus", psk = psk)
        } catch (e: RemoteError.PairingRejected) {
            awaitPsk()
            throw e
        } catch (t: Throwable) {
            throw fail(t)
        }
        updateProfile(profile.copy(pairingToken = psk))
        _state.value = ConnectionState.Connected
        learnSystemInfo(psk)
    }

    override suspend fun disconnect() = mutex.withLock {
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            powerOnWithWake()
            return@withLock
        }
        if (_state.value != ConnectionState.Connected) throw RemoteError.NotConnected()
        val psk = profile.pairingToken ?: throw RemoteError.NotConnected()
        try {
            when (command) {
                RemoteCommand.PowerOn -> setPower(psk, on = true)
                RemoteCommand.PowerOff -> setPower(psk, on = false)
                else -> ircc(psk, SonyKeys.codeFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName))
            }
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            // Fallo de transporte: sesión caída para que el gestor reconecte.
            if (error !is RemoteError.Protocol && error !is RemoteError.UnsupportedCommand) {
                _state.value = ConnectionState.Failed(error)
            }
            throw error
        }
    }

    /** `true` encendido, `false` en reposo; `null` si no se pudo consultar. */
    suspend fun isPoweredOn(): Boolean? {
        val psk = profile.pairingToken ?: return null
        return runCatching { powerStatus(psk) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }

    // ------------------------------------------------------------------ sesión

    private fun awaitPsk() {
        _state.value = ConnectionState.AwaitingPairing(
            PairingRequest(
                PairingRequest.Method.Passphrase,
                "Escribe la clave (Pre-Shared Key) configurada en el TV: Ajustes › Red › Control IP",
            ),
        )
    }

    private fun fail(t: Throwable): RemoteError {
        val error = RemoteError.from(t, lan.host)
        _state.value = ConnectionState.Failed(error)
        return error
    }

    /** Con "Encendido remoto" activo el TV responde en reposo; si no, Wake-on-LAN y sondeo. */
    private suspend fun powerOnWithWake() {
        val psk = profile.pairingToken ?: throw RemoteError.NotConnected()
        val reachable = runCatching { powerStatus(psk) }
            .onFailure { if (it is CancellationException || it is RemoteError.PairingRejected) throw it }
            .isSuccess
        if (!reachable) {
            val mac = profile.macAddress ?: throw fail(RemoteError.Unreachable(lan.host))
            _state.value = ConnectionState.Waking
            wakeOnLan.wake(mac, lastKnownHost = lan.host)
            waitUntilReachable(psk)
        }
        _state.value = ConnectionState.Connected
        setPower(psk, on = true)
    }

    private suspend fun waitUntilReachable(psk: String) {
        repeat(WAKE_POLL_ATTEMPTS) { attempt ->
            delay(WAKE_POLL_INTERVAL_MS)
            try {
                powerStatus(psk)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (attempt == WAKE_POLL_ATTEMPTS - 1) throw fail(RemoteError.Timeout(t))
            }
        }
    }

    private suspend fun learnSystemInfo(psk: String) {
        val info = runCatching { rest("system", "getSystemInformation", psk = psk).optJSONObject(0) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return
        val updated = profile.copy(
            macAddress = info.optString("macAddr").let(MacAddress::parse) ?: profile.macAddress,
            model = info.optString("model").ifEmpty { null } ?: profile.model,
            serialNumber = info.optString("serial").ifEmpty { null } ?: profile.serialNumber,
        )
        updateProfile(updated)
    }

    private fun updateProfile(updated: DeviceProfile) {
        if (updated != profile) {
            profile = updated
            _events.tryEmit(AdapterEvent.ProfileUpdated(updated))
        }
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun powerStatus(psk: String): Boolean =
        rest("system", "getPowerStatus", psk = psk).optJSONObject(0)?.optString("status") == "active"

    private suspend fun setPower(psk: String, on: Boolean) {
        rest("system", "setPowerStatus", JSONArray().put(JSONObject().put("status", on)), psk)
    }

    /** Llamada JSON-RPC de la API REST. Devuelve `result`; los errores 401/403 del cuerpo son de clave. */
    private suspend fun rest(service: String, method: String, params: JSONArray = JSONArray(), psk: String): JSONArray {
        val request = JSONObject()
            .put("method", method)
            .put("id", REQUEST_ID)
            .put("params", params)
            .put("version", "1.0")
        val text = client.postBody("$baseUrl/$service", request.toString(), mapOf(PSK_HEADER to psk), JSON_TYPE)
        val response = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw RemoteError.Protocol("Respuesta inválida del TV Sony", e)
        }
        response.optJSONArray("error")?.let { error ->
            val code = error.optInt(0)
            if (code == 401 || code == 403) throw RemoteError.PairingRejected()
            throw RemoteError.Protocol("El TV Sony respondió error $code en $method")
        }
        return response.optJSONArray("result") ?: JSONArray()
    }

    private suspend fun ircc(psk: String, code: String) {
        val envelope = """<?xml version="1.0"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$code</IRCCCode></u:X_SendIRCC></s:Body>""" +
            """</s:Envelope>"""
        client.postBody(
            "$baseUrl/IRCC",
            envelope,
            mapOf(PSK_HEADER to psk, "SOAPACTION" to "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\""),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): SonyBraviaAdapter
    }

    private companion object {
        const val PSK_HEADER = "X-Auth-PSK"
        const val JSON_TYPE = "application/json; charset=utf-8"
        const val REQUEST_ID = 50
        const val WAKE_POLL_ATTEMPTS = 20
        const val WAKE_POLL_INTERVAL_MS = 1_000L
    }
}
