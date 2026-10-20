package com.universalremote.domain.macro

import com.universalremote.domain.model.Macro
import com.universalremote.domain.model.RemoteCommand
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

sealed interface MacroResult {
    data object Success : MacroResult
    data class Failed(val stepIndex: Int, val error: Throwable) : MacroResult
}

/**
 * Ejecuta los pasos de una macro en orden. Si un paso falla se aborta y se informa su índice,
 * para que el usuario sepa en qué estado quedó el equipo. Cancelar la corrutina detiene la macro.
 */
class MacroRunner @Inject constructor() {

    suspend fun run(macro: Macro, send: suspend (RemoteCommand) -> Unit): MacroResult {
        macro.steps.forEachIndexed { index, step ->
            try {
                send(step.command)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                return MacroResult.Failed(index, t)
            }
            if (index < macro.steps.lastIndex) delay(step.delayAfterMs)
        }
        return MacroResult.Success
    }
}
