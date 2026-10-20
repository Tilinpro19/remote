package com.universalremote.adapters.tizen

import com.universalremote.adapters.common.SuspendWebSocket
import com.universalremote.adapters.common.TofuTrustManager
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
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
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
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Samsung Tizen (2016+): WebSocket `samsung.remote.control` en wss://host:8002.
 *
 * La primera conexión muestra "¿Permitir?" en el TV; al aceptarla llega `ms.channel.connect` con un
 * token que se guarda en [DeviceProfile.pairingToken]. Los modelos 2016 sin TLS usan ws://host:8001.
 */
class TizenAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val lanClient: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("TizenAdapter requiere DeviceTransport.Lan")

    @Volatile
    override var profile: DeviceProfile = initialProfile
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    override val capabilities: Set<RemoteCapability> = RemoteCapability.entries.toSet() - RemoteCapability.DEEP_LINK

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var trust = TofuTrustManager(initialProfile.pinnedCertificateSha256)
    private var socket: SuspendWebSocket? = null
    private var readerJob: Job? = null
    private var authorized = CompletableDeferred<Unit>()

    override fun supports(command: RemoteCommand): Boolean = when (command) {
        is RemoteCommand.Text, is RemoteCommand.TouchDelta, RemoteCommand.TouchTap -> true
        RemoteCommand.PowerOn -> true
        is RemoteCommand.LaunchApp -> TizenApps.id(command.app) != null
        else -> TizenKeys.of(command) != null
    }

    override suspend fun connect() = mutex.withLock { connectLocked() }

    override suspend fun disconnect() = mutex.withLock { teardown(ConnectionState.Idle) }

    override suspend fun close() {
        disconnect()
        scope.cancel()
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            wake()
            return@withLock
        }
        if (_state.value != ConnectionState.Connected) throw RemoteError.NotConnected()
        when (command) {
            RemoteCommand.PowerOn -> Unit
            is RemoteCommand.Text -> {
                control(
                    JSONObject()
                        .put("Cmd", Base64.getEncoder().encodeToString(command.text.toByteArray()))
                        .put("DataOfCmd", "base64")
                        .put("TypeOfRemote", "SendInputString"),
                )
                control(JSONObject().put("TypeOfRemote", "SendInputEnd"))
            }
            is RemoteCommand.TouchDelta -> control(
                JSONObject()
                    .put("Cmd", "Move")
                    .put(
                        "Position",
                        JSONObject().put("x", command.dx.roundToInt()).put("y", command.dy.roundToInt())
                            .put("Time", System.currentTimeMillis().toString()),
                    )
                    .put("TypeOfRemote", "ProcessMouseDevice"),
            )
            RemoteCommand.TouchTap -> control(JSONObject().put("Cmd", "LeftClick").put("TypeOfRemote", "ProcessMouseDevice"))
            is RemoteCommand.LaunchApp -> {
                val appId = TizenApps.id(command.app) ?: throw RemoteError.UnsupportedCommand(command.wireName)
                // Las apps nativas (navegador) usan NATIVE_LAUNCH; las de la tienda, DEEP_LINK.
                val actionType = if ('.' in appId) "NATIVE_LAUNCH" else "DEEP_LINK"
                emit(
                    JSONObject().put("event", "ed.apps.launch").put("to", "host")
                        .put("data", JSONObject().put("appId", appId).put("action_type", actionType).put("metaTag", "")),
                )
            }
            else -> {
                val key = TizenKeys.of(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)
                control(
                    JSONObject().put("Cmd", "Click").put("DataOfCmd", key).put("Option", "false")
                        .put("TypeOfRemote", "SendRemoteKey"),
                )
            }
        }
    }

    // ------------------------------------------------------------------ sesión

    private suspend fun connectLocked() {
        val current = _state.value
        if (current == ConnectionState.Connected || current is ConnectionState.AwaitingPairing) return
        _state.value = ConnectionState.Connecting
        try {
            trust = TofuTrustManager(profile.pinnedCertificateSha256)
            val client = lanClient.newBuilder()
                .sslSocketFactory(trust.sslContext().socketFactory, trust)
                .hostnameVerifier { _, _ -> true }
                .pingInterval(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            val ws = SuspendWebSocket.open(client, channelUrl())
            socket = ws
            authorized = CompletableDeferred()
            readerJob = scope.launch { readLoop(ws) }

            val alreadyPaired = runCatching { withTimeout(AUTH_FAST_PATH_MS) { authorized.await() } }.isSuccess
            if (!alreadyPaired && _state.value == ConnectionState.Connecting) {
                _state.value = ConnectionState.AwaitingPairing(
                    PairingRequest(PairingRequest.Method.ConfirmOnScreen, "Pulsa «Permitir» en el TV Samsung"),
                )
            }
        } catch (e: CancellationException) {
            teardown(ConnectionState.Idle)
            throw e
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            teardown(ConnectionState.Failed(error))
            throw error
        }
    }

    private fun channelUrl(): String {
        val secure = lan.port != LEGACY_PORT
        // OkHttp sólo construye URLs http(s); el WebSocket acepta ambas y las traduce a ws(s).
        val url = HttpUrl.Builder()
            .scheme(if (secure) "https" else "http")
            .host(lan.host)
            .port(lan.port)
            .addPathSegments("api/v2/channels/samsung.remote.control")
            .addQueryParameter("name", Base64.getEncoder().encodeToString(CLIENT_NAME.toByteArray()))
        profile.pairingToken?.let { url.addQueryParameter("token", it) }
        return url.build().toString()
    }

    private suspend fun readLoop(ws: SuspendWebSocket) {
        try {
            for (text in ws.incoming) handleMessage(JSONObject(text))
            onSocketLost(RemoteError.Unreachable(lan.host))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onSocketLost(RemoteError.from(t, lan.host))
        }
    }

    private fun handleMessage(message: JSONObject) {
        when (message.optString("event")) {
            "ms.channel.connect" -> {
                val token = message.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() }
                val updated = profile.copy(
                    pairingToken = token ?: profile.pairingToken,
                    pinnedCertificateSha256 = trust.seenFingerprint ?: profile.pinnedCertificateSha256,
                )
                if (updated != profile) {
                    profile = updated
                    _events.tryEmit(AdapterEvent.ProfileUpdated(updated))
                }
                _state.value = ConnectionState.Connected
                authorized.complete(Unit)
            }
            "ms.channel.unauthorized" -> {
                val error = RemoteError.PairingRejected()
                _state.value = ConnectionState.Failed(error)
                authorized.completeExceptionally(error)
            }
            "ms.channel.timeOut" -> {
                val error = RemoteError.Timeout()
                _state.value = ConnectionState.Failed(error)
                authorized.completeExceptionally(error)
            }
            "ms.remote.imeStart" -> _events.tryEmit(AdapterEvent.KeyboardRequested(""))
            "ms.remote.imeEnd" -> _events.tryEmit(AdapterEvent.KeyboardDismissed)
        }
    }

    private fun onSocketLost(error: RemoteError) {
        socket = null
        if (_state.value != ConnectionState.Idle) _state.value = ConnectionState.Failed(error)
    }

    private fun teardown(newState: ConnectionState) {
        readerJob?.cancel()
        readerJob = null
        socket?.close()
        socket = null
        _state.value = newState
    }

    private suspend fun wake() {
        val mac = profile.macAddress
        if (mac == null) {
            connectLocked()
            return
        }
        _state.value = ConnectionState.Waking
        wakeOnLan.wake(mac, lastKnownHost = lan.host)
        repeat(WAKE_POLL_ATTEMPTS) {
            delay(WAKE_POLL_INTERVAL_MS)
            _state.value = ConnectionState.Idle
            if (runCatching { connectLocked() }.isSuccess) return
        }
        throw RemoteError.Timeout()
    }

    private fun control(params: JSONObject) = sendMethod("ms.remote.control", params)

    private fun emit(params: JSONObject) = sendMethod("ms.channel.emit", params)

    private fun sendMethod(method: String, params: JSONObject) {
        val ws = socket ?: throw RemoteError.NotConnected()
        ws.send(JSONObject().put("method", method).put("params", params).toString())
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): TizenAdapter
    }

    private companion object {
        const val LEGACY_PORT = 8001
        const val CLIENT_NAME = "UniversalRemote"
        const val AUTH_FAST_PATH_MS = 3_000L
        const val WAKE_POLL_ATTEMPTS = 20
        const val WAKE_POLL_INTERVAL_MS = 1_000L
    }
}
