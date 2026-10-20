package com.universalremote.adapters.kodi

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.json.JSONException
import org.json.JSONObject

/**
 * Kodi: JSON-RPC 2.0 sobre HTTP (`POST /jsonrpc`, puerto 8080 por defecto).
 *
 * Requiere activar "Permitir control remoto por HTTP" en Kodi. Si el servidor web pide usuario,
 * el diálogo de vinculación los pide y [DeviceProfile.pairingToken] guarda `usuario:contraseña` (Basic auth).
 */
class KodiAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val client: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("KodiAdapter requiere DeviceTransport.Lan")

    private val url = "http://${lan.host}:${lan.port}/jsonrpc"

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.NAVIGATION, RemoteCapability.MEDIA,
    )

    override fun supports(command: RemoteCommand): Boolean =
        command.capability in capabilities && KodiKeys.callFor(command) != null

    override suspend fun connect() = mutex.withLock {
        val current = _state.value
        if (current == ConnectionState.Connected || current is ConnectionState.AwaitingPairing) return@withLock
        _state.value = ConnectionState.Connecting
        try {
            call(PING, profile.pairingToken)
            _state.value = ConnectionState.Connected
        } catch (e: RemoteError.PairingRejected) {
            // El servidor web de Kodi exige usuario y contraseña (o los guardados ya no valen).
            awaitCredentials()
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    override suspend fun submitPairingCode(code: String) = mutex.withLock {
        val credentials = code.trim()
        if (!credentials.contains(':')) throw RemoteError.InvalidCode("Escribe usuario:contraseña (p. ej. kodi:1234)")
        try {
            call(PING, credentials)
        } catch (e: RemoteError.PairingRejected) {
            awaitCredentials()
            throw e
        } catch (t: Throwable) {
            throw fail(t)
        }
        profile = profile.copy(pairingToken = credentials)
        _events.tryEmit(AdapterEvent.ProfileUpdated(profile))
        _state.value = ConnectionState.Connected
    }

    override suspend fun disconnect() = mutex.withLock {
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (_state.value != ConnectionState.Connected) {
            val mac = profile.macAddress
            if (command.canWakeDevice && mac != null) {
                wakeOnLan.wake(mac, lastKnownHost = lan.host)
                return@withLock
            }
            throw RemoteError.NotConnected()
        }
        val call = KodiKeys.callFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)
        try {
            call(call, profile.pairingToken)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            if (error !is RemoteError.Protocol) _state.value = ConnectionState.Failed(error)
            throw error
        }
        Unit
    }

    private fun awaitCredentials() {
        _state.value = ConnectionState.AwaitingPairing(
            PairingRequest(
                PairingRequest.Method.Passphrase,
                "Kodi pide usuario y contraseña (Ajustes › Servicios › Control). Escríbelos como usuario:contraseña",
            ),
        )
    }

    private fun fail(t: Throwable): RemoteError {
        val error = RemoteError.from(t, lan.host)
        _state.value = ConnectionState.Failed(error)
        return error
    }

    private suspend fun call(call: KodiKeys.Call, credentials: String?) {
        val headers = credentials?.takeIf { it.contains(':') }
            ?.let { mapOf("Authorization" to Credentials.basic(it.substringBefore(':'), it.substringAfter(':'))) }
            .orEmpty()
        val request = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", call.method)
        call.params?.let { request.put("params", it) }
        val text = client.postBody(url, request.toString(), headers, contentType = "application/json")
        val response = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw RemoteError.Protocol("Respuesta JSON-RPC inválida", e)
        }
        response.optJSONObject("error")?.let {
            throw RemoteError.Protocol("Kodi rechazó ${call.method}: ${it.optString("message")}")
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): KodiAdapter
    }

    private companion object {
        val PING = KodiKeys.Call("JSONRPC.Ping")
    }
}
