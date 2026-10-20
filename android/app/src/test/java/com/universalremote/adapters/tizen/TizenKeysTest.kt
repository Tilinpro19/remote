package com.universalremote.adapters.tizen

import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp
import org.junit.Assert.assertEquals
import org.junit.Test

class TizenKeysTest {

    @Test
    fun `teclas de Samsung Tizen`() {
        val cases = mapOf(
            RemoteCommand.Power to "KEY_POWER",
            RemoteCommand.VolumeUp to "KEY_VOLUP",
            RemoteCommand.VolumeDown to "KEY_VOLDOWN",
            RemoteCommand.ChannelUp to "KEY_CHUP",
            RemoteCommand.Digit(3) to "KEY_3",
            RemoteCommand.DpadCenter to "KEY_ENTER",
            RemoteCommand.Enter to "KEY_ENTER",
            RemoteCommand.Back to "KEY_RETURN",
            RemoteCommand.Input to "KEY_SOURCE",
            RemoteCommand.FastForward to "KEY_FF",
            // Samsung llama CYAN a la tecla azul.
            RemoteCommand.ColorKey(KeyColor.BLUE) to "KEY_CYAN",
            RemoteCommand.Raw("KEY_GUIDE") to "KEY_GUIDE",
            RemoteCommand.TouchTap to null,
        )
        cases.forEach { (command, expected) -> assertEquals(command.wireName, expected, TizenKeys.of(command)) }
    }

    @Test
    fun `ids de apps de Smart Hub`() {
        assertEquals("3201907018807", TizenApps.id(StreamingApp.NETFLIX))
        assertEquals(null, TizenApps.id(StreamingApp.LG_CHANNELS))
    }
}
