package com.universalremote.adapters.webos

import com.universalremote.adapters.common.SuspendWebSocket
import com.universalremote.adapters.common.TofuTrustManager
import com.universalremote.core.di.LanHttpClient
import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.adapter.nullOnFailure
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * LG webOS (SSAP sobre WebSocket, wss://host:3001).
 *
 * El primer registro muestra un aviso en el TV; al aceptarlo el TV devuelve un `client-key` que se
 * guarda como [DeviceProfile.pairingToken] y evita el aviso en adelante. Las teclas de navegación
 * viajan por un segundo socket ("pointer input") que se abre bajo demanda.
 */
class WebOsAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val lanClient: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("WebOsAdapter requiere DeviceTransport.Lan")

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
    private val nextId = AtomicInteger()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private var trust = TofuTrustManager(initialProfile.pinnedCertificateSha256)
    private var socket: SuspendWebSocket? = null
    private var pointer: SuspendWebSocket? = null
    private var readerJob: Job? = null
    private var registered = CompletableDeferred<Unit>()
    private var playing = false

    override fun supports(command: RemoteCommand): Boolean = when (command) {
        RemoteCommand.Search, RemoteCommand.Next, RemoteCommand.Previous -> false
        is RemoteCommand.LaunchApp -> WebOsApps.id(command.app) != null
        else -> command.capability in capabilities
    }

    override suspend fun connect() = mutex.withLock { connectLocked() }

    override suspend fun disconnect() = mutex.withLock { teardown(ConnectionState.Idle) }

    override suspend fun close() {
        disconnect()
        scope.cancel()
    }

    /** Sólo responde con la sesión abierta; "Active Standby" es reposo con la red despierta. */
    override suspend fun isPoweredOn(): Boolean? = nullOnFailure {
        when (request("ssap://com.webos.service.tvpower/power/getPowerState").optString("state")) {
            "Active", "Screen Off" -> true
            "Active Standby", "Suspend" -> false
            else -> null
        }
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (command.canWakeDevice && _state.value != ConnectionState.Connected) {
            wake()
            return@withLock
        }
        if (_state.value != ConnectionState.Connected) throw RemoteError.NotConnected()
        when (command) {
            RemoteCommand.Power, RemoteCommand.PowerOff -> request("ssap://system/turnOff")
            RemoteCommand.PowerOn -> Unit
            RemoteCommand.VolumeUp -> request("ssap://audio/volumeUp")
            RemoteCommand.VolumeDown -> request("ssap://audio/volumeDown")
            RemoteCommand.ChannelUp -> request("ssap://tv/channelUp")
            RemoteCommand.ChannelDown -> request("ssap://tv/channelDown")
            RemoteCommand.Play -> media("play")
            RemoteCommand.Pause -> media("pause")
            RemoteCommand.PlayPause -> media(if (playing) "pause" else "play")
            RemoteCommand.Stop -> media("stop")
            RemoteCommand.Rewind -> media("rewind")
            RemoteCommand.FastForward -> media("fastForward")
            RemoteCommand.Input -> launchInputPicker()
            is RemoteCommand.LaunchApp -> request(
                "ssap://system.launcher/launch",
                JSONObject().put("id", WebOsApps.id(command.app) ?: throw RemoteError.UnsupportedCommand(command.wireName)),
            )
            is RemoteCommand.Text ->
                request("ssap://com.webos.service.ime/insertText", JSONObject().put("text", command.text).put("replace", 0))
            RemoteCommand.Backspace -> request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", 1))
            RemoteCommand.Enter -> request("ssap://com.webos.service.ime/sendEnterKey")
            is RemoteCommand.TouchDelta ->
                pointer("type:move\ndx:${command.dx.roundToInt()}\ndy:${command.dy.roundToInt()}\ndown:0\n\n")
            is RemoteCommand.Scroll -> pointer("type:scroll\ndx:${command.dx.roundToInt()}\ndy:${command.dy.roundToInt()}\n\n")
            RemoteCommand.TouchTap -> pointer("type:click\n\n")
            else -> pointer("type:button\nname:${WebOsKeys.button(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)}\n\n")
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
            val scheme = if (lan.port == LEGACY_PORT) "ws" else "wss"
            val ws = SuspendWebSocket.open(client, "$scheme://${lan.host}:${lan.port}/")
            socket = ws
            registered = CompletableDeferred()
            readerJob = scope.launch { readLoop(ws, client) }
            ws.send(registerMessage().toString())

            // Con client-key válido el TV responde "registered" al instante; si no, muestra el aviso.
            val alreadyPaired = runCatching { withTimeout(REGISTER_FAST_PATH_MS) { registered.await() } }.isSuccess
            if (!alreadyPaired && _state.value == ConnectionState.Connecting) {
                _state.value = ConnectionState.AwaitingPairing(
                    PairingRequest(PairingRequest.Method.ConfirmOnScreen, "Acepta la solicitud que aparece en el TV LG"),
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

    private suspend fun readLoop(ws: SuspendWebSocket, client: OkHttpClient) {
        try {
            for (text in ws.incoming) handleMessage(JSONObject(text), client)
            onSocketLost(RemoteError.Unreachable(lan.host))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onSocketLost(RemoteError.from(t, lan.host))
        }
    }

    private fun handleMessage(message: JSONObject, client: OkHttpClient) {
        val id = message.optString("id")
        val type = message.optString("type")
        val payload = message.optJSONObject("payload") ?: JSONObject()
        when {
            id == REGISTER_ID && type == "registered" -> {
                val key = payload.optString("client-key").takeIf { it.isNotEmpty() }
                updateProfile(profile.copy(pairingToken = key ?: profile.pairingToken, pinnedCertificateSha256 = trust.seenFingerprint ?: profile.pinnedCertificateSha256))
                _state.value = ConnectionState.Connected
                registered.complete(Unit)
                subscribeKeyboard()
                pointerClient = client
                if (profile.macAddress == null) scope.launch { learnMacAddress() }
            }
            id == REGISTER_ID && type == "error" -> {
                val error = RemoteError.PairingRejected()
                _state.value = ConnectionState.Failed(error)
                registered.completeExceptionally(error)
            }
            id == KEYBOARD_ID -> {
                val widget = payload.optJSONObject("currentWidget")
                if (widget != null) {
                    _events.tryEmit(
                        if (widget.optBoolean("focus")) AdapterEvent.KeyboardRequested(widget.optString("value"))
                        else AdapterEvent.KeyboardDismissed,
                    )
                }
            }
            else -> pending.remove(id)?.complete(message)
        }
    }

    private var pointerClient: OkHttpClient? = null

    /** Lee la MAC del TV (cable preferido, si no Wi-Fi) para poder enviar Wake-on-LAN estando apagado. */
    private suspend fun learnMacAddress() {
        val info = runCatching { request("ssap://com.webos.service.connectionmanager/getinfo") }.getOrNull() ?: return
        val wired = info.optJSONObject("wiredInfo")
        val wifi = info.optJSONObject("wifiInfo")
        val wiredUp = info.optJSONObject("wired")?.optString("state") == "connected"
        val raw = listOfNotNull(wired, wifi)
            .let { if (wiredUp) it else it.reversed() }
            .firstNotNullOfOrNull { it.optString("macAddress").takeIf(String::isNotEmpty) }
            ?: return
        val mac = MacAddress.parse(raw) ?: return
        updateProfile(profile.copy(macAddress = mac))
    }

    private fun subscribeKeyboard() {
        val msg = JSONObject().put("type", "subscribe").put("id", KEYBOARD_ID)
            .put("uri", "ssap://com.webos.service.ime/registerRemoteKeyboard")
        runCatching { socket?.send(msg.toString()) }
    }

    private fun onSocketLost(error: RemoteError) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        if (_state.value != ConnectionState.Idle) _state.value = ConnectionState.Failed(error)
        socket = null
        pointer?.close()
        pointer = null
    }

    private fun teardown(newState: ConnectionState) {
        readerJob?.cancel()
        readerJob = null
        pointer?.close()
        pointer = null
        socket?.close()
        socket = null
        pending.clear()
        _state.value = newState
    }

    private suspend fun wake() {
        val mac = profile.macAddress
        if (mac == null) {
            // Sin MAC aprendida sólo se puede encender si el TV sigue en la red (modo "Quick Start").
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

    // ------------------------------------------------------------------ SSAP

    private suspend fun request(uri: String, payload: JSONObject? = null): JSONObject {
        val ws = socket ?: throw RemoteError.NotConnected()
        val id = "req_${nextId.incrementAndGet()}"
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val message = JSONObject().put("type", "request").put("id", id).put("uri", uri)
        if (payload != null) message.put("payload", payload)
        ws.send(message.toString())
        val response = try {
            withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending.remove(id)
        }
        if (response.optString("type") == "error") {
            throw RemoteError.Protocol("El TV LG rechazó $uri: ${response.optString("error")}")
        }
        return response.optJSONObject("payload") ?: JSONObject()
    }

    private suspend fun launchInputPicker() {
        val candidates = listOf(
            "com.webos.app.inputpicker",
            "com.webos.app.inputmgr",
            "com.webos.app.homeconnect",
            "com.webos.app.homedashboard",
        )
        for (appId in candidates) {
            try {
                request("ssap://system.launcher/launch", JSONObject().put("id", appId))
                return
            } catch (_: RemoteError.Protocol) {
                // TV rechazó este id (ej. 500 Application error); probar siguiente candidato
            }
        }
        try {
            request("ssap://tv/switchInput")
            return
        } catch (_: Throwable) {
            // Intentar tecla de puntero
        }
        try {
            pointer("type:button\nname:INPUT\n\n")
        } catch (_: Throwable) {
            throw RemoteError.Protocol("El modelo de TV LG no admite abrir el selector de entradas por red")
        }
    }

    private suspend fun media(action: String) {
        request("ssap://media.controls/$action")
        if (action == "play") playing = true
        if (action == "pause" || action == "stop") playing = false
    }

    private suspend fun pointer(frame: String) {
        val ws = pointer ?: openPointer().also { pointer = it }
        try {
            ws.send(frame)
        } catch (e: RemoteError.NotConnected) {
            pointer = null
            openPointer().also { pointer = it }.send(frame)
        }
    }

    private suspend fun openPointer(): SuspendWebSocket {
        val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket").optString("socketPath")
        if (path.isEmpty()) throw RemoteError.Protocol("El TV LG no ofreció socket de puntero")
        val client = pointerClient ?: throw RemoteError.NotConnected()
        return SuspendWebSocket.open(client, path)
    }

    private fun updateProfile(updated: DeviceProfile) {
        if (updated != profile) {
            profile = updated
            _events.tryEmit(AdapterEvent.ProfileUpdated(updated))
        }
    }

    private fun registerMessage(): JSONObject {
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put(
                "manifest",
                JSONObject()
                    .put("manifestVersion", 1)
                    .put("appVersion", "1.0")
                    .put("permissions", JSONArray(PERMISSIONS)),
            )
        profile.pairingToken?.let { payload.put("client-key", it) }
        return JSONObject().put("type", "register").put("id", REGISTER_ID).put("payload", payload)
    }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): WebOsAdapter
    }

    private companion object {
        const val LEGACY_PORT = 3000
        const val REGISTER_ID = "register_0"
        const val KEYBOARD_ID = "keyboard_0"
        const val REGISTER_FAST_PATH_MS = 3_000L
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val WAKE_POLL_ATTEMPTS = 20
        const val WAKE_POLL_INTERVAL_MS = 1_000L

        val PERMISSIONS = listOf(
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO", "CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_MEDIA_RECORDING",
            "CONTROL_INPUT_TEXT", "CONTROL_INPUT_TV", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER",
            "CONTROL_TV_SCREEN", "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST",
            "READ_INSTALLED_APPS", "READ_NETWORK_STATE", "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST",
            "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "READ_COUNTRY_INFO",
        )
    }
}
