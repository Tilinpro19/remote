package com.universalremote.widget

import com.universalremote.domain.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickActionTest {

    @Test
    fun `cada accion rapida se traduce a su comando`() {
        assertEquals(RemoteCommand.Power, QuickAction.POWER.toCommand())
        assertEquals(RemoteCommand.VolumeUp, QuickAction.VOLUME_UP.toCommand())
        assertEquals(RemoteCommand.VolumeDown, QuickAction.VOLUME_DOWN.toCommand())
        assertEquals(RemoteCommand.Mute, QuickAction.MUTE.toCommand())
    }
}
