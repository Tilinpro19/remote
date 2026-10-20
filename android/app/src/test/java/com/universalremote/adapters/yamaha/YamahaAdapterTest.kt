package com.universalremote.adapters.yamaha

import android.content.ContextWrapper
import com.universalremote.adapters.TestAdapters
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class YamahaAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter() = YamahaAdapter(
        DeviceProfile(name = "RX-V", kind = DeviceKind.YAMAHA_MUSICCAST, transport = DeviceTransport.Lan(server.hostName, server.port)),
        TestAdapters.client,
        WakeOnLanSender(ContextWrapper(null), Dispatchers.Unconfined),
    )

    private fun ok(extra: String = "") = MockResponse().setBody("""{"response_code":0$extra}""")

    private suspend fun connected(): YamahaAdapter {
        server.enqueue(ok())
        return adapter().also {
            it.connect()
            assertEquals("/YamahaExtendedControl/v1/system/getDeviceInfo", server.takeRequest().path)
        }
    }

    @Test
    fun `VolumeUp llama a main_setVolume`() = runTest {
        val avr = connected()
        server.enqueue(ok())
        avr.send(RemoteCommand.VolumeUp)
        assertEquals("/YamahaExtendedControl/v1/main/setVolume?volume=up", server.takeRequest().path)
    }

    @Test
    fun `Mute invierte el estado actual`() = runTest {
        val avr = connected()
        server.enqueue(ok(""","mute":false"""))
        server.enqueue(ok())
        avr.send(RemoteCommand.Mute)
        assertEquals("/YamahaExtendedControl/v1/main/getStatus", server.takeRequest().path)
        assertEquals("/YamahaExtendedControl/v1/main/setMute?enable=true", server.takeRequest().path)
    }

    @Test
    fun `response_code distinto de 0 lanza Protocol`() = runTest {
        val avr = connected()
        server.enqueue(MockResponse().setBody("""{"response_code":3}"""))
        try {
            avr.send(RemoteCommand.Play)
            fail("debía lanzar")
        } catch (e: RemoteError.Protocol) {
            // esperado
        }
    }

    @Test
    fun `sin digitos`() {
        assertFalse(adapter().supports(RemoteCommand.Digit(1)))
    }
}
