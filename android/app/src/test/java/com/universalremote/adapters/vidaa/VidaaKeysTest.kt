package com.universalremote.adapters.vidaa

import com.universalremote.domain.model.RemoteCommand
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VidaaKeysTest {

    @Test
    fun `teclas basicas`() {
        assertEquals("KEY_VOLUMEUP", VidaaKeys.keyFor(RemoteCommand.VolumeUp))
        assertEquals("KEY_OK", VidaaKeys.keyFor(RemoteCommand.DpadCenter))
        assertEquals("KEY_POWER", VidaaKeys.keyFor(RemoteCommand.Power))
        assertEquals("KEY_RETURNS", VidaaKeys.keyFor(RemoteCommand.Back))
        assertNull(VidaaKeys.keyFor(RemoteCommand.TouchTap))
    }

    @Test
    fun `topicos usan el clientId con sufijo normal`() {
        val topics = VidaaTopics("abc")
        assertEquals("/remoteapp/tv/remote_service/abc\$normal/actions/sendkey", topics.sendKey)
        assertEquals("/remoteapp/tv/ui_service/abc\$normal/actions/authenticationcode", topics.authCode)
        assertEquals("/remoteapp/mobile/abc\$normal/ui_service/data/authenticationcode", topics.authResult)
    }

    @Test
    fun `payload del codigo de autenticacion`() {
        assertEquals(1234, JSONObject(VidaaTopics.authPayload("1234")).getInt("authNum"))
    }

    @Test
    fun `resultado de autenticacion`() {
        assertEquals(true, VidaaTopics.isAuthAccepted("""{"result":1,"info":""}"""))
        assertEquals(false, VidaaTopics.isAuthAccepted("""{"result":0}"""))
        assertEquals(false, VidaaTopics.isAuthAccepted("basura"))
    }
}
