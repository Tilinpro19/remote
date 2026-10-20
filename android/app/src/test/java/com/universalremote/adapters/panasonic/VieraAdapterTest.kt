package com.universalremote.adapters.panasonic

import android.content.ContextWrapper
import com.universalremote.adapters.TestAdapters
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.network.wol.WakeOnLanSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VieraAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter() = VieraAdapter(
        DeviceProfile(name = "Viera", kind = DeviceKind.PANASONIC_VIERA, transport = DeviceTransport.Lan(server.hostName, server.port)),
        TestAdapters.client,
        WakeOnLanSender(ContextWrapper(null), Dispatchers.Unconfined),
    )

    private val plainSdd = MockResponse().setBody("<scpd><actionList><action><name>X_SendKey</name></action></actionList></scpd>")

    @Test
    fun `Mute envia X_SendKey con NRC_MUTE-ONOFF`() = runTest {
        server.enqueue(plainSdd)
        server.enqueue(MockResponse().setBody("<ok/>"))
        val tv = adapter()
        tv.connect()
        assertEquals("/nrc/sdd_0.xml", server.takeRequest().path)

        tv.send(RemoteCommand.Mute)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/nrc/control_0", request.path)
        assertTrue(request.getHeader("SOAPACTION")!!.contains("X_SendKey"))
        assertTrue(request.body.readUtf8().contains("<X_KeyEvent>NRC_MUTE-ONOFF</X_KeyEvent>"))
    }

    @Test
    fun `modelo cifrado se rechaza con UnsupportedDevice`() = runTest {
        server.enqueue(MockResponse().setBody("<scpd><action><name>X_GetEncryptSessionId</name></action></scpd>"))
        val tv = adapter()
        try {
            tv.connect()
            fail("debía lanzar")
        } catch (e: RemoteError.UnsupportedDevice) {
            assertEquals("PANASONIC_ENCRYPTED", e.kind)
        }
        assertTrue(tv.state.value is ConnectionState.Failed)
    }

    @Test
    fun `un 4xx al enviar se trata como modelo cifrado`() = runTest {
        server.enqueue(plainSdd)
        server.enqueue(MockResponse().setResponseCode(401))
        val tv = adapter()
        tv.connect()
        try {
            tv.send(RemoteCommand.VolumeUp)
            fail("debía lanzar")
        } catch (e: RemoteError.UnsupportedDevice) {
            assertEquals("PANASONIC_ENCRYPTED", e.kind)
        }
    }

    @Test
    fun `claves`() {
        assertEquals("NRC_VOLUP-ONOFF", VieraKeys.keyFor(RemoteCommand.VolumeUp))
        assertEquals("NRC_D5-ONOFF", VieraKeys.keyFor(RemoteCommand.Digit(5)))
        assertEquals("NRC_ENTER-ONOFF", VieraKeys.keyFor(RemoteCommand.DpadCenter))
        assertEquals(null, VieraKeys.keyFor(RemoteCommand.TouchTap))
        assertFalse(adapter().supports(RemoteCommand.TouchDelta(1f, 1f)))
    }
}
