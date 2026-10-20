package com.universalremote.data.local

import com.universalremote.domain.model.MacroStep
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroStepsCodecTest {

    @Test
    fun `los pasos sobreviven a la ida y vuelta por JSON`() {
        val steps = listOf(
            MacroStep(RemoteCommand.PowerOn, 3_000),
            MacroStep(RemoteCommand.LaunchApp(StreamingApp.entries.first())),
            MacroStep(RemoteCommand.Text("hola mundo"), 0),
            MacroStep(RemoteCommand.Digit(7)),
        )

        assertEquals(steps, MacroStepsCodec.decode(MacroStepsCodec.encode(steps)))
    }

    @Test
    fun `un paso desconocido se descarta sin perder el resto`() {
        val json = """[{"cmd":"CMD_FUTURO"},{"cmd":"CMD_HOME","delay":100}]"""

        assertEquals(listOf(MacroStep(RemoteCommand.Home, 100)), MacroStepsCodec.decode(json))
    }

    @Test
    fun `JSON corrupto devuelve lista vacia`() {
        assertEquals(emptyList<MacroStep>(), MacroStepsCodec.decode("no es json"))
    }
}
