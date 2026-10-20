package com.universalremote.domain.macro

import com.universalremote.domain.model.MacroStep
import com.universalremote.domain.model.RemoteCommand
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Graba los comandos que el usuario pulsa en cualquier pestaña. El retardo de cada paso es el
 * tiempo real hasta la pulsación siguiente: si el TV tardó en encender, la macro espera lo mismo.
 */
@Singleton
class MacroRecorder(private val clock: () -> Long) {

    @Inject constructor() : this(System::currentTimeMillis)

    private val _steps = MutableStateFlow<List<MacroStep>?>(null)

    /** Pasos grabados; `null` si no se está grabando. */
    val steps: StateFlow<List<MacroStep>?> = _steps.asStateFlow()

    private var lastAt = 0L

    fun start() {
        _steps.value = emptyList()
    }

    fun record(command: RemoteCommand) {
        val current = _steps.value ?: return
        // Un arrastre del touchpad son cientos de deltas: no tiene sentido repetirlo a ciegas.
        if (command.isContinuous) return
        val now = clock()
        val previous = current.lastOrNull()?.let { it.copy(delayAfterMs = (now - lastAt).coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)) }
        lastAt = now
        _steps.value = current.dropLast(1) + listOfNotNull(previous) + MacroStep(command)
    }

    /** Termina la grabación y devuelve los pasos. */
    fun stop(): List<MacroStep> = _steps.value.orEmpty().also { _steps.value = null }

    companion object {
        const val MIN_DELAY_MS = 300L
        const val MAX_DELAY_MS = 10_000L
    }
}
