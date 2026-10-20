package com.universalremote.adapters.webos

import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp
import org.junit.Assert.assertEquals
import org.junit.Test

class WebOsKeysTest {

    @Test
    fun `botones del socket de puntero`() {
        val cases = mapOf(
            RemoteCommand.DpadUp to "UP",
            RemoteCommand.DpadCenter to "ENTER",
            RemoteCommand.Back to "BACK",
            RemoteCommand.Home to "HOME",
            RemoteCommand.Mute to "MUTE",
            RemoteCommand.Digit(7) to "7",
            RemoteCommand.ColorKey(KeyColor.RED) to "RED",
            RemoteCommand.Raw("EXIT") to "EXIT",
            // Volumen y canales van por SSAP, no por el socket de botones.
            RemoteCommand.VolumeUp to null,
            RemoteCommand.ChannelUp to null,
        )
        cases.forEach { (command, expected) -> assertEquals(command.wireName, expected, WebOsKeys.button(command)) }
    }

    @Test
    fun `ids de apps de webOS`() {
        assertEquals("netflix", WebOsApps.id(StreamingApp.NETFLIX))
        assertEquals("youtube.leanback.v4", WebOsApps.id(StreamingApp.YOUTUBE))
        assertEquals(null, WebOsApps.id(StreamingApp.ROKU_CHANNEL))
    }
}
