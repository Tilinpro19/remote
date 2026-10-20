package com.universalremote.adapters.androidtv

import android.os.Build
import com.universalremote.BuildConfig
import com.universalremote.adapters.common.PointerAccumulator
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
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Android TV / Google TV (protocolo "Android TV Remote v2", el mismo de la app Google TV).
 *
 * 1. Emparejamiento (puerto 6467): TLS con certificado de cliente; el TV muestra un código
 *    hexadecimal de 6 caracteres que el usuario escribe en el móvil.
 * 2. Control (puerto 6466): TLS con el mismo certificado; protobuf enmarcado por longitud. El TV
 *    envía pings periódicos que hay que contestar para mantener la sesión.
 */
class AndroidTvAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    private val identity: AndroidTvClientIdentity,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("AndroidTvAdapter requiere DeviceTransport.Lan")

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    /** Sin puntero ni texto libre: el protocolo sólo inyecta teclas (Borrar/Intro sí funcionan). */
    override val capabilities: Set<RemoteCapability> = RemoteCapability.entries.toSet() - RemoteCapability.POINTER

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val writeLock = Any()

    private var socket: SSLSocket? = null
    private var readerJob: Job? = null
    private var pairing: PairingSession? = null

    @Volatile
    private var isOn = true

    private class PairingSession(val socket: SSLSocket, val trust: TofuTrustManager)

    /** Sin puntero nativo en el protocolo: el touchpad se traduce a pasos de D-pad. */
    private val pointer = PointerAccumulator()

    override fun supports(command: RemoteCommand): Boolean = when (command) {
        is RemoteCommand.LaunchApp -> AndroidTvApps.link(command.app) != null
        is RemoteCommand.PlayYouTube -> true
        is RemoteCommand.Text -> AndroidTvKeys.text(command.text).isNotEmpty()
        is RemoteCommand.TouchDelta, is RemoteCommand.Scroll -> true
        else -> AndroidTvKeys.of(command) != null
    }

    override suspend fun connect() = mutex.withLock { connectLocked() }

    override suspend fun disconnect() = mutex.withLock {
        closePairing()
        closeRemote()
        _state.value = ConnectionState.Idle
    }

    override suspend fun close() {
        disconnect()
        scope.cancel()
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            wake()
            if (_state.value != ConnectionState.Connected) return@withLock
        }
        // Reconexión transparente si la sesión persistente se cayó (TV en reposo, Wi-Fi que parpadea…).
        if (_state.value != ConnectionState.Connected && _state.value !is ConnectionState.AwaitingPairing) {
            _state.value = ConnectionState.Idle
            connectLocked()
        }
        if (_state.value != ConnectionState.Connected) throw RemoteError.NotConnected()
        // KEYCODE_POWER alterna: se omite si el TV ya está en el estado pedido.
        if (command == RemoteCommand.PowerOn && isOn) return@withLock
        if (command == RemoteCommand.PowerOff && !isOn) return@withLock
        val frames: List<ByteArray> = when (command) {
            is RemoteCommand.LaunchApp ->
                listOf(AndroidTvProtocol.appLinkLaunch(AndroidTvApps.link(command.app) ?: throw RemoteError.UnsupportedCommand(command.wireName)))
            // El TV resuelve el enlace a la app de YouTube instalada.
            is RemoteCommand.PlayYouTube ->
                listOf(AndroidTvProtocol.appLinkLaunch("https://www.youtube.com/watch?v=${command.videoId}"))
            is RemoteCommand.Text ->
                AndroidTvKeys.text(command.text).map(AndroidTvProtocol::keyInject)
            is RemoteCommand.TouchDelta ->
                pointer.accumulate(command.dx, command.dy).map { AndroidTvProtocol.keyInject(AndroidTvKeys.of(it)) }
            is RemoteCommand.Scroll ->
                pointer.accumulate(0f, command.dy).map { AndroidTvProtocol.keyInject(AndroidTvKeys.of(it)) }
            else -> listOf(AndroidTvProtocol.keyInject(AndroidTvKeys.of(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)))
        }
        if (frames.isEmpty()) return@withLock
        try {
            for (frame in frames) write(frame)
        } catch (e: RemoteError) {
            // Socket medio cerrado: un único reintento con sesión nueva antes de rendirse.
            closeRemote()
            _state.value = ConnectionState.Idle
            connectLocked()
            if (_state.value != ConnectionState.Connected) throw e
            for (frame in frames) write(frame)
        }
    }

    override suspend fun submitPairingCode(code: String) = mutex.withLock {
        val session = pairing ?: throw RemoteError.NotConnected()
        val server = session.trust.seenCertificate?.publicKey as? RSAPublicKey
            ?: throw RemoteError.Protocol("El TV no presentó una clave RSA")
        val client = identity.certificate().publicKey as RSAPublicKey
        val secret = AndroidTvProtocol.computeSecret(client, server, code)
            ?: throw RemoteError.InvalidCode("Código incorrecto: revisa los 6 caracteres que muestra el TV")

        val status = try {
            withContext(Dispatchers.IO) {
                ProtoFraming.write(session.socket.outputStream, AndroidTvProtocol.secret(secret))
                AndroidTvProtocol.status(ProtoFraming.read(session.socket.inputStream))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            closePairing()
            val error = RemoteError.from(t, lan.host)
            _state.value = ConnectionState.Failed(error)
            throw error
        }
        closePairing()
        when (status) {
            AndroidTvProtocol.STATUS_OK -> {
                // Tras re-emparejar (p. ej. TV restablecido) el certificado del TV puede ser otro:
                // se vuelve a aprender en la próxima conexión de control.
                updateProfile(profile.copy(pairingToken = identity.fingerprint(), pinnedCertificateSha256 = null))
                _state.value = ConnectionState.Idle
                connectLocked()
            }
            AndroidTvProtocol.STATUS_BAD_SECRET -> {
                // El TV invalida el código: se pide uno nuevo.
                _state.value = ConnectionState.Idle
                startPairing()
                throw RemoteError.InvalidCode("El TV rechazó el código; introduce el nuevo que aparece en pantalla")
            }
            else -> {
                val error = RemoteError.PairingRejected()
                _state.value = ConnectionState.Failed(error)
                throw error
            }
        }
    }

    // ------------------------------------------------------------------ sesión

    private suspend fun connectLocked() {
        val current = _state.value
        if (current == ConnectionState.Connected || current is ConnectionState.AwaitingPairing) return
        _state.value = ConnectionState.Connecting
        try {
            if (profile.pairingToken != identity.fingerprint()) {
                startPairing()
                return
            }
            probe(lan.port)
            try {
                openRemote()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // El TCP respondió (sonda previa): un rechazo TLS o un cierre inmediato significa que
                // el TV ya no reconoce el certificado → se pide el PIN en vez de mostrar el error.
                if (t is SSLException || t is EOFException || t is SessionRejected) {
                    closeRemote()
                    startPairing()
                    return
                }
                throw t
            }
        } catch (e: CancellationException) {
            closeRemote()
            _state.value = ConnectionState.Idle
            throw e
        } catch (t: Throwable) {
            closeRemote()
            closePairing()
            val error = RemoteError.from(t, lan.host)
            _state.value = ConnectionState.Failed(error)
            throw error
        }
    }

    private suspend fun openRemote() {
        val trust = TofuTrustManager(profile.pinnedCertificateSha256)
        val s = openTls(lan.port, trust, identity.keyManagers())
        socket = s
        val ready = CompletableDeferred<Unit>()
        readerJob = scope.launch { readLoop(s, ready) }
        try {
            withTimeout(READY_TIMEOUT_MS) { ready.await() }
        } catch (e: IOException) {
            // Handshake completo pero el TV corta la sesión antes de configurarla: certificado no autorizado.
            throw SessionRejected(e)
        }
        if (profile.pinnedCertificateSha256 == null) {
            trust.seenFingerprint?.let { updateProfile(profile.copy(pinnedCertificateSha256 = it)) }
        }
        _state.value = ConnectionState.Connected
    }

    private suspend fun startPairing() {
        closePairing()
        val trust = TofuTrustManager(null)
        val s = openTls(lan.port + 1, trust, identity.keyManagers())
        pairing = PairingSession(s, trust)
        withContext(Dispatchers.IO) {
            for (message in listOf(
                AndroidTvProtocol.pairingRequest("UniversalRemote (${Build.MODEL})"),
                AndroidTvProtocol.options(),
                AndroidTvProtocol.configuration(),
            )) {
                ProtoFraming.write(s.outputStream, message)
                val status = AndroidTvProtocol.status(ProtoFraming.read(s.inputStream))
                if (status != AndroidTvProtocol.STATUS_OK) throw RemoteError.PairingRejected()
            }
        }
        _state.value = ConnectionState.AwaitingPairing(
            PairingRequest(PairingRequest.Method.Pin(6), "Escribe el código de 6 caracteres que muestra el TV"),
        )
    }

    private suspend fun openTls(port: Int, trust: TofuTrustManager, keyManagers: Array<KeyManager>): SSLSocket =
        withContext(Dispatchers.IO) {
            val s = trust.sslContext(keyManagers).socketFactory.createSocket() as SSLSocket
            try {
                // TLS 1.3 obliga a firmar con RSA-PSS, que varios TVs (y algunos Conscrypt) no
                // negocian bien: se fija TLS 1.2, el que usa la app oficial.
                s.enabledProtocols = arrayOf(TLS_PROTOCOL)
                s.connect(InetSocketAddress(lan.host, port), CONNECT_TIMEOUT_MS)
                s.soTimeout = READ_TIMEOUT_MS
                s.startHandshake()
                s
            } catch (t: Throwable) {
                runCatching { s.close() }
                throw t
            }
        }

    /** Prueba TCP real (no ICMP): distingue "TV apagada/IP cambiada" de un fallo TLS posterior. */
    private suspend fun probe(port: Int) = withContext(Dispatchers.IO) {
        Socket().use { it.connect(InetSocketAddress(lan.host, port), CONNECT_TIMEOUT_MS) }
    }

    private class SessionRejected(cause: Throwable) : IOException(cause)

    private fun readLoop(s: SSLSocket, ready: CompletableDeferred<Unit>) {
        try {
            while (true) {
                val message = ProtoMessage.parse(ProtoFraming.read(s.inputStream))
                when {
                    message.has(AndroidTvProtocol.FIELD_CONFIGURE) -> writeBlocking(
                        AndroidTvProtocol.configure(
                            model = Build.MODEL,
                            vendor = Build.MANUFACTURER,
                            packageName = BuildConfig.APPLICATION_ID,
                            appVersion = BuildConfig.VERSION_NAME,
                        ),
                    )
                    message.has(AndroidTvProtocol.FIELD_SET_ACTIVE) -> {
                        writeBlocking(AndroidTvProtocol.setActive())
                        ready.complete(Unit)
                    }
                    message.has(AndroidTvProtocol.FIELD_PING_REQUEST) ->
                        writeBlocking(AndroidTvProtocol.pingResponse(message.message(AndroidTvProtocol.FIELD_PING_REQUEST)?.int(1) ?: 0L))
                    message.has(AndroidTvProtocol.FIELD_START) -> {
                        isOn = message.message(AndroidTvProtocol.FIELD_START)?.int(1) == 1L
                        ready.complete(Unit)
                    }
                    message.has(AndroidTvProtocol.FIELD_IME_SHOW) -> _events.tryEmit(AdapterEvent.KeyboardRequested(""))
                }
            }
        } catch (t: Throwable) {
            if (!ready.isCompleted) {
                ready.completeExceptionally(t)
            } else if (socket === s && _state.value == ConnectionState.Connected) {
                _state.value = ConnectionState.Failed(RemoteError.from(t, lan.host))
            }
            runCatching { s.close() }
        }
    }

    private suspend fun write(bytes: ByteArray) {
        val s = socket ?: throw RemoteError.NotConnected()
        try {
            withContext(Dispatchers.IO) { synchronized(writeLock) { ProtoFraming.write(s.outputStream, bytes) } }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            _state.value = ConnectionState.Failed(error)
            throw error
        }
    }

    /** Variante bloqueante para el hilo lector (ya está en Dispatchers.IO). */
    private fun writeBlocking(bytes: ByteArray) {
        val s = socket ?: return
        synchronized(writeLock) { ProtoFraming.write(s.outputStream, bytes) }
    }

    private suspend fun wake() {
        try {
            connectLocked()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val mac = profile.macAddress ?: throw t
            _state.value = ConnectionState.Waking
            wakeOnLan.wake(mac, lastKnownHost = lan.host)
        }
        repeat(WAKE_POLL_ATTEMPTS) {
            delay(WAKE_POLL_INTERVAL_MS)
            _state.value = ConnectionState.Idle
            if (runCatching { connectLocked() }.isSuccess) return
        }
        throw RemoteError.Timeout()
    }

    private fun closeRemote() {
        readerJob?.cancel()
        readerJob = null
        socket?.let { runCatching { it.close() } }
        socket = null
    }

    private fun closePairing() {
        pairing?.socket?.let { runCatching { it.close() } }
        pairing = null
    }

    private fun updateProfile(updated: DeviceProfile) {
        if (updated != profile) {
            profile = updated
            _events.tryEmit(AdapterEvent.ProfileUpdated(updated))
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): AndroidTvAdapter
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 2_500
        const val TLS_PROTOCOL = "TLSv1.2"
        /** El TV envía un ping cada ~5 s; sin tráfico en 20 s la sesión se da por caída. */
        const val READ_TIMEOUT_MS = 20_000
        const val READY_TIMEOUT_MS = 5_000L
        const val WAKE_POLL_ATTEMPTS = 20
        const val WAKE_POLL_INTERVAL_MS = 1_000L
    }
}
