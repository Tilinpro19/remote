package com.universalremote.adapters.denon

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
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
 * Receptores Denon/Marantz por el protocolo de texto en TCP 23: cada orden es ASCII terminada en CR
 * (`MVUP\r`). El receptor sólo admite una conexión a la vez, por eso la sesión mantiene el socket abierto.
 * Encender en standby exige "Network Control: Always On" en el receptor.
 */
class DenonAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("DenonAdapter requiere DeviceTransport.Lan")

    override val profile: DeviceProfile = initialProfile

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>()
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private var socket: Socket? = null

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.NAVIGATION, RemoteCapability.MEDIA,
    )

    override fun supports(command: RemoteCommand): Boolean =
        command in TOGGLES || (command.capability in capabilities && DenonKeys.commandFor(command) != null)

    override suspend fun connect() = mutex.withLock {
        if (_state.value == ConnectionState.Connected) return@withLock
        _state.value = ConnectionState.Connecting
        try {
            socket = withContext(Dispatchers.IO) {
                Socket().apply {
                    soTimeout = READ_TIMEOUT_MS
                    connect(InetSocketAddress(lan.host, lan.port), CONNECT_TIMEOUT_MS)
                }
            }
            _state.value = ConnectionState.Connected
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        closeSocket()
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        val s = socket?.takeIf { _state.value == ConnectionState.Connected }
        if (s == null) {
            val mac = profile.macAddress
            if (command.canWakeDevice && mac != null) {
                wakeOnLan.wake(mac, lastKnownHost = lan.host)
                return@withLock
            }
            throw RemoteError.NotConnected()
        }
        val line = when (command) {
            // El protocolo no tiene "toggle": se consulta el estado y se envía el contrario.
            RemoteCommand.Power -> if (query(s, "PW") == "PWON") "PWSTANDBY" else "PWON"
            RemoteCommand.Mute -> if (query(s, "MU") == "MUON") "MUOFF" else "MUON"
            else -> DenonKeys.commandFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)
        }
        try {
            write(s, line)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw fail(t)
        }
        Unit
    }

    /** Envía `<prefix>?` y devuelve la primera línea de respuesta con ese prefijo. */
    private suspend fun query(s: Socket, prefix: String): String = try {
        withContext(Dispatchers.IO) {
            val input = s.getInputStream()
            // Descarta los avisos de estado que el receptor emite por su cuenta.
            input.skip(input.available().toLong())
            write(s, "$prefix?")
            var reply: String
            do {
                reply = buildString {
                    while (true) {
                        val c = input.read()
                        if (c == -1) throw RemoteError.Network(java.io.EOFException("Socket cerrado"))
                        if (c == '\r'.code) break
                        append(c.toChar())
                    }
                }
            } while (!reply.startsWith(prefix))
            reply
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        throw fail(t)
    }

    private suspend fun write(s: Socket, line: String) = withContext(Dispatchers.IO) {
        s.getOutputStream().apply {
            write("$line\r".toByteArray(Charsets.US_ASCII))
            flush()
        }
    }

    private fun fail(t: Throwable): RemoteError {
        val error = RemoteError.from(t, lan.host)
        closeSocket()
        _state.value = ConnectionState.Failed(error)
        return error
    }

    private fun closeSocket() {
        socket?.let { runCatching { it.close() } }
        socket = null
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): DenonAdapter
    }

    private companion object {
        val TOGGLES = setOf(RemoteCommand.Power, RemoteCommand.Mute)
        const val CONNECT_TIMEOUT_MS = 2_000
        const val READ_TIMEOUT_MS = 2_000
    }
}
