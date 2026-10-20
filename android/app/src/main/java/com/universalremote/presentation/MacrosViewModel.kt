package com.universalremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalremote.domain.macro.MacroRecorder
import com.universalremote.domain.macro.MacroResult
import com.universalremote.domain.macro.MacroRunner
import com.universalremote.domain.manager.RemoteControllerManager
import com.universalremote.domain.model.Macro
import com.universalremote.domain.model.MacroStep
import com.universalremote.domain.repository.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MacrosUiState(
    val macros: List<Macro> = emptyList(),
    /** Pasos grabados hasta ahora; `null` si no se está grabando. */
    val recording: List<MacroStep>? = null,
    val runningId: String? = null,
    /** Resultado de la última ejecución, para mostrarlo en la pantalla. */
    val message: String? = null,
    val hasActiveDevice: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MacrosViewModel @Inject constructor(
    private val manager: RemoteControllerManager,
    private val repository: MacroRepository,
    private val recorder: MacroRecorder,
    private val runner: MacroRunner,
) : ViewModel() {

    private val runningId = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private var runJob: Job? = null

    val uiState: StateFlow<MacrosUiState> = combine(
        manager.activeDevice.flatMapLatest { device ->
            device?.let { repository.observeForDevice(it.id) } ?: flowOf(emptyList())
        },
        manager.activeDevice,
        recorder.steps,
        runningId,
        message,
    ) { macros, device, recording, running, msg ->
        MacrosUiState(macros, recording, running, msg, hasActiveDevice = device != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MacrosUiState())

    fun startRecording() {
        message.value = null
        recorder.start()
    }

    fun cancelRecording() {
        recorder.stop()
    }

    fun saveRecording(name: String) {
        val device = manager.activeDevice.value ?: return
        val steps = recorder.stop()
        if (steps.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            repository.save(Macro(UUID.randomUUID().toString(), name.trim(), device.id, steps))
        }
    }

    fun run(macro: Macro) {
        if (runJob?.isActive == true) return
        message.value = null
        runningId.value = macro.id
        runJob = viewModelScope.launch {
            try {
                message.value = when (val result = runner.run(macro, manager::sendAndAwait)) {
                    MacroResult.Success -> "«${macro.name}» completada"
                    is MacroResult.Failed -> {
                        val step = macro.steps[result.stepIndex]
                        "«${macro.name}» se detuvo en el paso ${result.stepIndex + 1} " +
                            "(${step.command.label()}): ${result.error.message}"
                    }
                }
            } finally {
                runningId.value = null
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        message.value = "Macro detenida"
    }

    fun delete(macro: Macro) {
        viewModelScope.launch { repository.delete(macro.id) }
    }
}
