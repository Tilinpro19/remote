package com.universalremote.domain.macro

import com.universalremote.domain.model.Macro
import com.universalremote.domain.model.MacroStep
import com.universalremote.domain.model.RemoteCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MacroRunnerTest {

    private val runner = MacroRunner()

    private fun macro(vararg steps: MacroStep) = Macro("m", "Cine", "dev", steps.toList())

    @Test
    fun `ejecuta los pasos en orden respetando los retardos`() = runTest {
        val sent = mutableListOf<Pair<RemoteCommand, Long>>()
        val result = runner.run(
            macro(MacroStep(RemoteCommand.PowerOn, 1_000), MacroStep(RemoteCommand.Input, 500), MacroStep(RemoteCommand.Home)),
        ) { sent += it to currentTime }

        assertSame(MacroResult.Success, result)
        assertEquals(
            listOf(RemoteCommand.PowerOn to 0L, RemoteCommand.Input to 1_000L, RemoteCommand.Home to 1_500L),
            sent,
        )
    }

    @Test
    fun `si un paso falla devuelve su indice y no ejecuta los siguientes`() = runTest {
        val sent = mutableListOf<RemoteCommand>()
        val boom = IllegalStateException("sin conexión")
        val result = runner.run(
            macro(MacroStep(RemoteCommand.PowerOn), MacroStep(RemoteCommand.Input), MacroStep(RemoteCommand.Home)),
        ) {
            if (it == RemoteCommand.Input) throw boom
            sent += it
        }

        assertEquals(MacroResult.Failed(1, boom), result)
        assertEquals(listOf<RemoteCommand>(RemoteCommand.PowerOn), sent)
    }

    @Test
    fun `cancelar la corrutina detiene la macro`() = runTest {
        val sent = mutableListOf<RemoteCommand>()
        val job = launch {
            runner.run(macro(MacroStep(RemoteCommand.PowerOn, 1_000), MacroStep(RemoteCommand.Home))) { sent += it }
        }
        runCurrent()
        advanceTimeBy(500)
        job.cancel()
        advanceTimeBy(5_000)

        assertEquals(listOf<RemoteCommand>(RemoteCommand.PowerOn), sent)
    }
}
