package com.universalremote.adapters.yamaha

import com.universalremote.adapters.common.getJson
import com.universalremote.core.di.LanHttpClient
import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.network.wol.WakeOnLanSender
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Yamaha MusicCast (Yamaha Extended Control): `GET /YamahaExtendedControl/v1/...` en HTTP 80,
 * sin emparejamiento. Cada respuesta trae `response_code`; cualquier valor distinto de 0 es un error.
 */
class YamahaAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val client: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("YamahaAdapter requiere DeviceTransport.Lan")

    private val baseUrl = "http://${lan.host}:${lan.port}/YamahaExtendedControl/v1"

    override val profile: DeviceProfile = initialProfile

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>()
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.NAVIGATION, RemoteCapability.MEDIA,
    )

    override fun supports(command: RemoteCommand): Boolean =
        command == RemoteCommand.Mute || (command.capability in capabilities && YamahaKeys.pathFor(command) != null)

    override suspend fun connect() = mutex.withLock {
        if (_state.value == ConnectionState.Connected) return@withLock
        _state.value = ConnectionState.Connecting
        try {
            get("system/getDeviceInfo")
            _state.value = ConnectionState.Connected
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            _state.value = ConnectionState.Failed(error)
            throw error
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (_state.value != ConnectionState.Connected) {
            // En standby con "Network Standby" activo el receptor sigue respondiendo; sin él sólo queda WoL.
            val mac = profile.macAddress
            if (command.canWakeDevice && mac != null) {
                wakeOnLan.wake(mac, lastKnownHost = lan.host)
                return@withLock
            }
            throw RemoteError.NotConnected()
        }
        try {
            if (command == RemoteCommand.Mute) {
                // La API no tiene "toggle" para el silencio: se lee el estado y se invierte.
                val muted = get("main/getStatus").optBoolean("mute", false)
                get("main/setMute?enable=${!muted}")
            } else {
                get(YamahaKeys.pathFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            if (error !is RemoteError.Protocol && error !is RemoteError.UnsupportedCommand) {
                _state.value = ConnectionState.Failed(error)
            }
            throw error
        }
        Unit
    }

    private suspend fun get(path: String): JSONObject {
        val json = client.getJson("$baseUrl/$path")
        val code = json.optInt("response_code", -1)
        if (code != 0) throw RemoteError.Protocol("MusicCast respondió response_code=$code a $path")
        return json
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): YamahaAdapter
    }
}
