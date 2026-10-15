package com.universalremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalremote.core.di.ApplicationScope
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.adapter.RemoteAdapterFactory
import com.universalremote.domain.manager.ControllerEvent
import com.universalremote.domain.manager.RemoteControllerManager
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.DiscoveredDevice
import com.universalremote.domain.model.IrCodeset
import com.universalremote.domain.model.IrDeviceCategory
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.domain.repository.DeviceRepository
import com.universalremote.domain.repository.IrCodeRepository
import com.universalremote.hardware.haptics.HapticFeedbackManager
import com.universalremote.hardware.ir.IrHardwareManager
import com.universalremote.network.discovery.LanDiscoveryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val manager: RemoteControllerManager,
    private val deviceRepository: DeviceRepository,
    private val irCodes: IrCodeRepository,
    private val discovery: LanDiscoveryManager,
    private val adapterFactory: RemoteAdapterFactory,
    private val haptics: HapticFeedbackManager,
    irHardware: IrHardwareManager,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val discoveryState = MutableStateFlow(DiscoveryUiState())
    private val irSetupState = MutableStateFlow<IrSetupState>(IrSetupState.Idle)
    private val hasIrEmitter = irHardware.hasIrEmitter()

    private val _events = MutableSharedFlow<RemoteUiEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<RemoteUiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<RemoteUiState> = combine(
        deviceRepository.observeDevices(),
        manager.activeDevice,
        manager.connectionState,
        discoveryState,
        irSetupState,
    ) { devices, active, connection, discovered, irSetup ->
        RemoteUiState(
            devices = devices,
            activeDevice = active,
            connection = connection,
            capabilities = manager.activeCapabilities(),
            discovery = discovered,
            irSetup = irSetup,
            hasIrEmitter = hasIrEmitter,
        )
    }
        .catch { emitError(RemoteError.from(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemoteUiState(hasIrEmitter = hasIrEmitter))

    private var scanJob: Job? = null

    /** Adaptador temporal del flujo de prueba IR; se recrea por candidato. */
    private val trialMutex = Mutex()
    private var trialAdapter: RemoteAdapter? = null

    init {
        viewModelScope.launch {
            manager.events.collect { event ->
                when (event) {
                    is ControllerEvent.CommandFailed -> {
                        haptics.performError()
                        _events.emit(RemoteUiEvent.ShowError(event.error, event.command))
                    }
                    is ControllerEvent.ConnectionFailed -> _events.emit(RemoteUiEvent.ShowError(event.error))
                    is ControllerEvent.KeyboardRequested -> _events.emit(RemoteUiEvent.OpenKeyboard(event.initialText))
                    ControllerEvent.KeyboardDismissed -> _events.emit(RemoteUiEvent.CloseKeyboard)
                }
            }
        }
    }

    // ------------------------------------------------------------------ comandos

    /** Único punto de entrada de los botones de la UI. No bloquea: háptica inmediata y encolado. */
    fun onCommandTriggered(command: RemoteCommand) {
        haptics.perform(command.hapticWeight)
        manager.dispatch(command)
    }

    /** Variante para UIs que emiten identificadores (`CMD_POWER`, `CMD_TOUCH_DELTA`…). */
    fun onCommandTriggered(wireName: String, dx: Float = 0f, dy: Float = 0f, text: String? = null) {
        val command = RemoteCommand.fromWireName(wireName, dx, dy, text)
        if (command == null) {
            emitError(RemoteError.UnsupportedCommand(wireName))
            return
        }
        onCommandTriggered(command)
    }

    fun isSupported(command: RemoteCommand): Boolean = manager.supports(command)

    // ------------------------------------------------------------------ dispositivos

    fun selectDevice(profileId: String) = launchCatching { manager.selectDevice(profileId) }

    fun removeDevice(profileId: String) = launchCatching { manager.removeDevice(profileId) }

    fun reconnect() = manager.reconnect()

    fun submitPairingCode(code: String) = launchCatching { manager.submitPairingCode(code.trim()) }

    // ------------------------------------------------------------------ descubrimiento LAN

    fun startDiscovery() {
        scanJob?.cancel()
        discoveryState.value = DiscoveryUiState(isScanning = true)
        scanJob = viewModelScope.launch {
            discovery.scan()
                .catch { emitError(RemoteError.from(it)) }
                .onCompletion { discoveryState.update { it.copy(isScanning = false) } }
                .collect { found -> discoveryState.update { it.copy(devices = found) } }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
    }

    /** Persiste un equipo descubierto (o reutiliza el perfil existente del mismo host) y lo activa. */
    fun addDiscoveredDevice(device: DiscoveredDevice) = launchCatching {
        val kind = device.kind ?: throw RemoteError.UnsupportedDevice("desconocido (${device.host})")
        val port = device.port ?: kind.defaultControlPort ?: throw RemoteError.UnsupportedDevice(kind.name)
        val existing = deviceRepository.observeDevices().first()
            .firstOrNull { it.kind == kind && it.lanHost == device.host }
        if (existing != null) {
            manager.selectDevice(existing.id)
            return@launchCatching
        }
        val profile = DeviceProfile(
            name = device.name,
            kind = kind,
            transport = DeviceTransport.Lan(device.host, port),
            model = device.model,
        )
        manager.addAndSelect(profile)
        stopDiscovery()
        _events.emit(RemoteUiEvent.DeviceAdded(profile))
    }

    /** Alta manual por IP cuando el descubrimiento no encuentra el TV (redes con multicast filtrado). */
    fun addManualDevice(host: String, kind: DeviceKind) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) {
            emitError(RemoteError.Protocol("Escribe la IP del TV (Ajustes › Red en el propio TV)"))
            return
        }
        addDiscoveredDevice(DiscoveredDevice(host = trimmed, name = "${kind.displayName()} ($trimmed)", kind = kind, port = null))
    }

    // ------------------------------------------------------------------ IR: prueba por descarte

    fun loadIrBrands(category: IrDeviceCategory) = launchCatching {
        irSetupState.value = IrSetupState.Loading
        irSetupState.value = IrSetupState.BrandSelection(category, irCodes.brands(category))
    }

    /** @param bridgeUrl hub IR externo cuando el teléfono no tiene emisor. */
    fun startIrSetup(brand: String, category: IrDeviceCategory, bridgeUrl: String? = null) = launchCatching {
        if (bridgeUrl == null && !hasIrEmitter) {
            throw RemoteError.HardwareUnavailable("Sin emisor IR: indica la URL de un hub IR en la red local")
        }
        irSetupState.value = IrSetupState.Loading
        val candidates = irCodes.codesets(brand, category)
        candidatesCache = candidates
        irSetupState.value = candidates.firstOrNull()
            ?.let { IrSetupState.Testing(brand, category, it, 0, candidates.size, bridgeUrl) }
            ?: IrSetupState.Exhausted(brand, category)
    }

    /** Envía [command] con el candidato actual para que el usuario observe si el equipo reacciona. */
    fun testIrCandidate(command: RemoteCommand = RemoteCommand.Power) = launchCatching {
        val testing = irSetupState.value as? IrSetupState.Testing ?: return@launchCatching
        haptics.perform(command.hapticWeight)
        irSetupState.value = testing.copy(isSending = true)
        try {
            trialMutex.withLock {
                val adapter = trialAdapter?.takeIf { it.profile.transport == trialTransport(testing) }
                    ?: replaceTrialAdapter(testing)
                adapter.send(command)
            }
        } finally {
            irSetupState.update { if (it is IrSetupState.Testing) it.copy(isSending = false) else it }
        }
    }

    /** Respuesta del usuario al test: confirma el codeset o avanza al siguiente candidato. */
    fun onIrCandidateResult(worked: Boolean) = launchCatching {
        val testing = irSetupState.value as? IrSetupState.Testing ?: return@launchCatching
        if (worked) {
            val profile = DeviceProfile(
                name = "${testing.brand} ${testing.category.displayName()}",
                kind = DeviceKind.INFRARED,
                transport = trialTransport(testing),
            )
            closeTrialAdapter()
            manager.addAndSelect(profile)
            irSetupState.value = IrSetupState.Idle
            _events.emit(RemoteUiEvent.DeviceAdded(profile))
            return@launchCatching
        }
        val nextIndex = testing.index + 1
        val next = candidatesCache.getOrNull(nextIndex)
        closeTrialAdapter()
        irSetupState.value = if (next != null) {
            testing.copy(candidate = next, index = nextIndex, isSending = false)
        } else {
            IrSetupState.Exhausted(testing.brand, testing.category)
        }
    }

    fun cancelIrSetup() = launchCatching {
        closeTrialAdapter()
        candidatesCache = emptyList()
        irSetupState.value = IrSetupState.Idle
    }

    private var candidatesCache: List<IrCodeset> = emptyList()

    private fun trialTransport(testing: IrSetupState.Testing) =
        DeviceTransport.Infrared(testing.candidate.id, testing.bridgeUrl)

    private suspend fun replaceTrialAdapter(testing: IrSetupState.Testing): RemoteAdapter {
        trialAdapter?.close()
        val profile = DeviceProfile(
            id = TRIAL_PROFILE_ID,
            name = testing.candidate.label,
            kind = DeviceKind.INFRARED,
            transport = trialTransport(testing),
        )
        return adapterFactory.create(profile).also {
            it.connect()
            trialAdapter = it
        }
    }

    private suspend fun closeTrialAdapter() = trialMutex.withLock {
        trialAdapter?.close()
        trialAdapter = null
    }

    // ------------------------------------------------------------------ utilidades

    private fun launchCatching(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            emitError(RemoteError.from(t))
        }
    }

    private fun emitError(error: RemoteError) {
        _events.tryEmit(RemoteUiEvent.ShowError(error))
    }

    override fun onCleared() {
        scanJob?.cancel()
        val adapter = trialAdapter
        trialAdapter = null
        if (adapter != null) {
            appScope.launch { withContext(NonCancellable) { runCatching { adapter.close() } } }
        }
    }

    private fun IrDeviceCategory.displayName(): String = when (this) {
        IrDeviceCategory.TV -> "TV"
        IrDeviceCategory.AIR_CONDITIONER -> "Aire acondicionado"
        IrDeviceCategory.SET_TOP_BOX -> "Decodificador"
        IrDeviceCategory.PROJECTOR -> "Proyector"
        IrDeviceCategory.AUDIO -> "Audio"
        IrDeviceCategory.OTHER -> "IR"
    }

    private fun DeviceKind.displayName(): String = when (this) {
        DeviceKind.ROKU -> "Roku"
        DeviceKind.ANDROID_TV -> "Android TV"
        DeviceKind.LG_WEBOS -> "LG webOS"
        DeviceKind.SAMSUNG_TIZEN -> "Samsung"
        DeviceKind.INFRARED -> "IR"
        DeviceKind.BLUETOOTH_HID -> "Bluetooth"
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TRIAL_PROFILE_ID = "ir-trial"
    }
}
