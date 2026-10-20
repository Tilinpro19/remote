package com.universalremote.adapters.firetv

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
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

/**
 * Amazon Fire TV por ADB en red (5555): cada tecla es `shell:input keyevent N`.
 *
 * Requiere activar Ajustes › Mi Fire TV › Opciones para desarrolladores › Depuración ADB. La
 * primera vez el Fire TV pregunta "¿Permitir depuración?"; al aceptar recuerda la clave de [AdbKeyStore].
 */
class FireTvAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    private val keys: AdbKeyStore,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("FireTvAdapter requiere DeviceTransport.Lan")

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private var socket: Socket? = null
    private var adb: AdbProtocol? = null

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.DIGITS,
        RemoteCapability.NAVIGATION, RemoteCapability.MEDIA, RemoteCapability.KEYBOARD,
    )

    override fun supports(command: RemoteCommand): Boolean = when (command) {
        is RemoteCommand.Text -> true
        is RemoteCommand.LaunchApp -> FireTvKeys.packageFor(command.app) != null
        else -> command.capability in capabilities && FireTvKeys.keyEventFor(command) != null
    }

    override suspend fun connect() = mutex.withLock {
        val current = _state.value
        if (current == ConnectionState.Connected || current is ConnectionState.AwaitingPairing) return@withLock
        try {
            connectLocked()
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        closeSocket()
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            wakeAndConnect()
        }
        val session = adb?.takeIf { _state.value == ConnectionState.Connected } ?: throw RemoteError.NotConnected()
        val shell = when (command) {
            is RemoteCommand.Text -> FireTvKeys.inputTextCommand(command.text)
            is RemoteCommand.LaunchApp -> {
                val pkg = FireTvKeys.packageFor(command.app) ?: throw RemoteError.UnsupportedCommand(command.wireName)
                "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
            }
            else -> "input keyevent ${FireTvKeys.keyEventFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)}"
        }
        try {
            session.shell(shell)
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            closeSocket()
            _state.value = ConnectionState.Failed(error)
            throw error
        }
        Unit
    }

    // ------------------------------------------------------------------ sesión

    private suspend fun connectLocked() {
        closeSocket()
        _state.value = ConnectionState.Connecting
        val keyPair = withContext(Dispatchers.IO) { keys.keyPair() }
        val s = withContext(Dispatchers.IO) {
            Socket().apply {
                // El usuario puede tardar en aceptar el diálogo de depuración en el TV.
                soTimeout = HANDSHAKE_TIMEOUT_MS
                connect(InetSocketAddress(lan.host, lan.port), CONNECT_TIMEOUT_MS)
            }
        }
        socket = s
        val protocol = AdbProtocol(s, keyPair)
        protocol.connect(onAuthPrompt = {
            _state.value = ConnectionState.AwaitingPairing(
                PairingRequest(PairingRequest.Method.ConfirmOnScreen, "Acepta «¿Permitir depuración USB?» en el Fire TV y marca «Permitir siempre»"),
            )
        })
        s.soTimeout = COMMAND_TIMEOUT_MS
        adb = protocol
        _state.value = ConnectionState.Connected
    }

    private suspend fun wakeAndConnect() {
        val direct = runCatching { connectLocked() }.onFailure { if (it is CancellationException) throw it }
        if (direct.isSuccess) return
        val mac = profile.macAddress ?: throw fail(direct.exceptionOrNull()!!)
        _state.value = ConnectionState.Waking
        wakeOnLan.wake(mac, lastKnownHost = lan.host)
        for (attempt in 1..WAKE_POLL_ATTEMPTS) {
            delay(WAKE_POLL_INTERVAL_MS)
            val ok = runCatching { connectLocked() }.onFailure { if (it is CancellationException) throw it }.isSuccess
            if (ok) return
        }
        throw fail(RemoteError.Timeout())
    }

    private fun fail(t: Throwable): RemoteError {
        val error = RemoteError.from(t, lan.host)
        closeSocket()
        _state.value = ConnectionState.Failed(error)
        return error
    }

    private fun closeSocket() {
        adb = null
        socket?.let { runCatching { it.close() } }
        socket = null
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): FireTvAdapter
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 2_000
        const val HANDSHAKE_TIMEOUT_MS = 60_000
        const val COMMAND_TIMEOUT_MS = 5_000
        const val WAKE_POLL_ATTEMPTS = 20
        const val WAKE_POLL_INTERVAL_MS = 1_000L
    }
}
