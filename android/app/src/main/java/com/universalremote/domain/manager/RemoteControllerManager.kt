package com.universalremote.domain.manager

import com.universalremote.core.di.ApplicationScope
import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.adapter.RemoteAdapterFactory
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.domain.repository.DeviceRepository
import com.universalremote.domain.repository.SessionPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface ControllerEvent {
    data class CommandFailed(val command: RemoteCommand, val error: RemoteError) : ControllerEvent
    data class ConnectionFailed(val profile: DeviceProfile, val error: RemoteError) : ControllerEvent
    data class KeyboardRequested(val initialText: String) : ControllerEvent
    data object KeyboardDismissed : ControllerEvent
}

/**
 * Punto único de entrada de comandos. Mantiene el adaptador del dispositivo activo, serializa los
 * comandos en una cola FIFO (fusionando ráfagas de touchpad) y sincroniza la sesión con el ciclo
 * de vida del proceso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RemoteControllerManager @Inject constructor(
    private val adapterFactory: RemoteAdapterFactory,
    private val deviceRepository: DeviceRepository,
    private val preferences: SessionPreferences,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val sessionMutex = Mutex()
    private val activeAdapter = MutableStateFlow<RemoteAdapter?>(null)
    private val _activeDevice = MutableStateFlow<DeviceProfile?>(null)
    private val _events = MutableSharedFlow<ControllerEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Cola acotada: si el equipo deja de responder, los comandos viejos se descartan en lugar de
     * dispararse en ráfaga cuando vuelva (20 "subir volumen" tardíos son peores que perderlos).
     */
    private val commandQueue = Channel<RemoteCommand>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var adapterJobs: Job? = null

    @Volatile
    private var isForeground = false

    val activeDevice: StateFlow<DeviceProfile?> = _activeDevice.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = activeAdapter
        .flatMapLatest { it?.state ?: flowOf(ConnectionState.Idle) }
        .stateIn(scope, SharingStarted.Eagerly, ConnectionState.Idle)

    val events = _events.asSharedFlow()

    init {
        scope.launch { processQueue() }
        scope.launch { restoreLastDevice() }
    }

    /** No suspende ni bloquea: apto para llamarse directamente desde un click handler. */
    fun dispatch(command: RemoteCommand) {
        commandQueue.trySend(command)
    }

    fun supports(command: RemoteCommand): Boolean = activeAdapter.value?.supports(command) ?: false

    /** Capacidades del adaptador activo; algunas se conocen sólo tras conectar (p. ej. Roku TV vs. stick). */
    fun activeCapabilities(): Set<RemoteCapability> = activeAdapter.value?.capabilities.orEmpty()

    suspend fun selectDevice(profileId: String) {
        val profile = deviceRepository.get(profileId)
            ?: throw IllegalArgumentException("Perfil inexistente: $profileId")
        sessionMutex.withLock { switchTo(profile) }
    }

    /** Persiste un perfil nuevo (emparejamiento/descubrimiento) y lo activa. */
    suspend fun addAndSelect(profile: DeviceProfile) {
        deviceRepository.upsert(profile)
        sessionMutex.withLock { switchTo(profile) }
    }

    suspend fun removeDevice(profileId: String) {
        sessionMutex.withLock {
            if (_activeDevice.value?.id == profileId) {
                releaseCurrent()
                preferences.setActiveDeviceId(null)
            }
            deviceRepository.delete(profileId)
        }
    }

    suspend fun submitPairingCode(code: String) {
        val adapter = activeAdapter.value ?: throw RemoteError.NoActiveDevice()
        adapter.submitPairingCode(code)
    }

    /** Reintento manual desde la UI tras un fallo. */
    fun reconnect() {
        activeAdapter.value?.let(::connectInBackground)
    }

    fun onForeground() {
        isForeground = true
        activeAdapter.value?.let(::connectInBackground)
    }

    fun onBackground() {
        isForeground = false
        scope.launch {
            sessionMutex.withLock {
                activeAdapter.value?.let { adapter ->
                    runCatching { adapter.disconnect() }
                }
            }
        }
    }

    private suspend fun restoreLastDevice() {
        val lastId = preferences.activeDeviceId.first() ?: return
        val profile = deviceRepository.get(lastId) ?: return
        sessionMutex.withLock {
            if (activeAdapter.value == null) switchTo(profile)
        }
    }

    private suspend fun switchTo(profile: DeviceProfile) {
        releaseCurrent()
        val adapter = adapterFactory.create(profile)
        activeAdapter.value = adapter
        _activeDevice.value = profile
        preferences.setActiveDeviceId(profile.id)
        adapterJobs = observe(adapter)
        if (isForeground) connectInBackground(adapter)
    }

    private suspend fun releaseCurrent() {
        adapterJobs?.cancel()
        adapterJobs = null
        val previous = activeAdapter.value ?: return
        activeAdapter.value = null
        _activeDevice.value = null
        withContext(NonCancellable) { runCatching { previous.close() } }
    }

    private fun observe(adapter: RemoteAdapter): Job = scope.launch {
        launch {
            adapter.events.collect { event ->
                when (event) {
                    is AdapterEvent.ProfileUpdated -> persistProfile(adapter, event.profile)
                    is AdapterEvent.KeyboardRequested -> _events.emit(ControllerEvent.KeyboardRequested(event.initialText))
                    AdapterEvent.KeyboardDismissed -> _events.emit(ControllerEvent.KeyboardDismissed)
                }
            }
        }
        launch {
            adapter.state.collect { state ->
                if (state == ConnectionState.Connected) {
                    val current = _activeDevice.value ?: return@collect
                    persistProfile(adapter, current.copy(lastConnectedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    private suspend fun persistProfile(adapter: RemoteAdapter, profile: DeviceProfile) {
        if (activeAdapter.value !== adapter) return
        deviceRepository.upsert(profile)
        _activeDevice.value = profile
    }

    private fun connectInBackground(adapter: RemoteAdapter) {
        scope.launch {
            try {
                withTimeout(CONNECT_TIMEOUT_MS) { adapter.connect() }
            } catch (e: CancellationException) {
                // Un timeout propio es fallo de conexión; una cancelación externa se propaga.
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                _events.emit(ControllerEvent.ConnectionFailed(adapter.profile, RemoteError.Timeout(e)))
            } catch (t: Throwable) {
                _events.emit(ControllerEvent.ConnectionFailed(adapter.profile, RemoteError.from(t, adapter.profile.lanHost)))
            }
        }
    }

    private suspend fun processQueue() {
        for (first in commandQueue) {
            var command = first
            var deferred: RemoteCommand? = null
            if (command.isContinuous) {
                while (true) {
                    val next = commandQueue.tryReceive().getOrNull() ?: break
                    val merged = coalesce(command, next)
                    if (merged == null) {
                        deferred = next
                        break
                    }
                    command = merged
                }
            }
            execute(command)
            deferred?.let { execute(it) }
        }
    }

    private suspend fun execute(command: RemoteCommand) {
        val adapter = activeAdapter.value
        if (adapter == null) {
            _events.emit(ControllerEvent.CommandFailed(command, RemoteError.NoActiveDevice()))
            return
        }
        if (!adapter.supports(command)) {
            _events.emit(ControllerEvent.CommandFailed(command, RemoteError.UnsupportedCommand(command.wireName)))
            return
        }
        try {
            val state = adapter.state.value
            if (!state.isConnected && !state.isBusy && !command.canWakeDevice) {
                withTimeout(CONNECT_TIMEOUT_MS) { adapter.connect() }
            }
            adapter.send(command)
        } catch (e: CancellationException) {
            if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            _events.emit(ControllerEvent.CommandFailed(command, RemoteError.Timeout(e)))
        } catch (t: Throwable) {
            _events.emit(ControllerEvent.CommandFailed(command, RemoteError.from(t, adapter.profile.lanHost)))
        }
    }

    private fun coalesce(a: RemoteCommand, b: RemoteCommand): RemoteCommand? = when {
        a is RemoteCommand.TouchDelta && b is RemoteCommand.TouchDelta -> RemoteCommand.TouchDelta(a.dx + b.dx, a.dy + b.dy)
        a is RemoteCommand.Scroll && b is RemoteCommand.Scroll -> RemoteCommand.Scroll(a.dx + b.dx, a.dy + b.dy)
        else -> null
    }

    private companion object {
        const val QUEUE_CAPACITY = 32
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
