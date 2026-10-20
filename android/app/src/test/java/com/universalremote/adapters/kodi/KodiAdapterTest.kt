package com.universalremote.adapters.kodi

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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class KodiAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(token: String? = null) = KodiAdapter(
        DeviceProfile(
            name = "Kodi", kind = DeviceKind.KODI,
            transport = DeviceTransport.Lan(server.hostName, server.port), pairingToken = token,
        ),
        TestAdapters.client,
        WakeOnLanSender(ContextWrapper(null), Dispatchers.Unconfined),
    )

    private fun ok() = MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":"OK"}""")

    @Test
    fun `VolumeUp envia Application_SetVolume increment`() = runTest {
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":1,"result":"pong"}"""))
        server.enqueue(ok())
        val kodi = adapter(token = "kodi:secreto")
        kodi.connect()
        val ping = server.takeRequest()
        assertEquals("JSONRPC.Ping", JSONObject(ping.body.readUtf8()).getString("method"))
        assertEquals(okhttp3.Credentials.basic("kodi", "secreto"), ping.getHeader("Authorization"))

        kodi.send(RemoteCommand.VolumeUp)

        val request = server.takeRequest()
        assertEquals("/jsonrpc", request.path)
        val json = JSONObject(request.body.readUtf8())
        assertEquals("Application.SetVolume", json.getString("method"))
        assertEquals("increment", json.getJSONObject("params").getString("volume"))
    }

    @Test
    fun `DpadUp envia Input_Up`() = runTest {
        server.enqueue(MockResponse().setBody("""{"result":"pong"}"""))
        server.enqueue(ok())
        val kodi = adapter()
        kodi.connect()
        server.takeRequest()
        kodi.send(RemoteCommand.DpadUp)
        assertEquals("Input.Up", JSONObject(server.takeRequest().body.readUtf8()).getString("method"))
    }

    @Test
    fun `un error JSON-RPC se convierte en RemoteError_Protocol`() = runTest {
        server.enqueue(MockResponse().setBody("""{"result":"pong"}"""))
        server.enqueue(MockResponse().setBody("""{"error":{"code":-32601,"message":"Method not found."}}"""))
        val kodi = adapter()
        kodi.connect()
        try {
            kodi.send(RemoteCommand.Home)
            fail("debía lanzar")
        } catch (e: RemoteError.Protocol) {
            // esperado
        }
    }

    @Test
    fun `un 401 pide credenciales y submitPairingCode las guarda`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"result":"pong"}"""))
        val kodi = adapter()
        kodi.connect()
        assertTrue(kodi.state.value is ConnectionState.AwaitingPairing)

        kodi.submitPairingCode("kodi:1234")

        assertEquals(ConnectionState.Connected, kodi.state.value)
        assertEquals("kodi:1234", kodi.profile.pairingToken)
        server.takeRequest()
        assertEquals(okhttp3.Credentials.basic("kodi", "1234"), server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `sin canales ni touchpad`() {
        assertFalse(adapter().supports(RemoteCommand.ChannelUp))
        assertFalse(adapter().supports(RemoteCommand.TouchDelta(1f, 1f)))
    }
}
