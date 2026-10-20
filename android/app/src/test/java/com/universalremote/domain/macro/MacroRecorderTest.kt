package com.universalremote.domain.macro

import com.universalremote.domain.model.MacroStep
import com.universalremote.domain.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacroRecorderTest {

    private var now = 0L
    private val recorder = MacroRecorder { now }

    @Test
    fun `sin grabar no guarda nada`() {
        recorder.record(RemoteCommand.Home)

        assertNull(recorder.steps.value)
    }

    @Test
    fun `el retardo de cada paso es el tiempo real hasta el siguiente, acotado`() {
        recorder.start()
        recorder.record(RemoteCommand.PowerOn)
        now = 4_000
        recorder.record(RemoteCommand.Input)
        now = 4_050
        recorder.record(RemoteCommand.DpadDown)
        now = 60_000
        recorder.record(RemoteCommand.DpadCenter)

        assertEquals(
            listOf(
                MacroStep(RemoteCommand.PowerOn, 4_000),
                MacroStep(RemoteCommand.Input, MacroRecorder.MIN_DELAY_MS),
                MacroStep(RemoteCommand.DpadDown, MacroRecorder.MAX_DELAY_MS),
                MacroStep(RemoteCommand.DpadCenter),
            ),
            recorder.stop(),
        )
        assertNull(recorder.steps.value)
    }

    @Test
    fun `los gestos continuos del touchpad no se graban`() {
        recorder.start()
        recorder.record(RemoteCommand.TouchDelta(3f, 4f))

        assertEquals(emptyList<MacroStep>(), recorder.stop())
    }
}
