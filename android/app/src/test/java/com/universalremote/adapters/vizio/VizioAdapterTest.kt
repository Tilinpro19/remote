package com.universalremote.adapters.vizio

import android.content.ContextWrapper
import com.universalremote.adapters.TestAdapters
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.PairingRequest
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.network.wol.WakeOnLanSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VizioAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server = MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(), false)
            start()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(token: String?) = VizioAdapter(
        DeviceProfile(
            id = "dev-1",
            name = "Vizio",
            kind = DeviceKind.VIZIO,
            transport = DeviceTransport.Lan(server.hostName, server.port),
            pairingToken = token,
        ),
        TestAdapters.client,
        WakeOnLanSender(ContextWrapper(null), Dispatchers.Unconfined),
    )

    private fun ok(item: String = "{}") =
        MockResponse().setBody("""{"STATUS":{"RESULT":"SUCCESS","DETAIL":"Success"},"ITEM":$item}""")

    private val powerOn = MockResponse().setBody("""{"STATUS":{"RESULT":"SUCCESS"},"ITEMS":[{"VALUE":1}]}""")

    @Test
    fun `sin token inicia el emparejamiento y espera el PIN`() = runTest {
        server.enqueue(ok("""{"PAIRING_REQ_TOKEN":777,"CHALLENGE_TYPE":1}"""))
        val vizio = adapter(token = null)

        vizio.connect()

        val state = vizio.state.value as ConnectionState.AwaitingPairing
        assertEquals(PairingRequest.Method.Pin(4), state.request.method)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/pairing/start", request.path)
        assertEquals("dev-1", JSONObject(request.body.readUtf8()).getString("DEVICE_ID"))
    }

    @Test
    fun `submitPairingCode guarda el token y fija el certificado`() = runTest {
        server.enqueue(ok("""{"PAIRING_REQ_TOKEN":777,"CHALLENGE_TYPE":1}"""))
        server.enqueue(ok("""{"AUTH_TOKEN":"Ztoken"}"""))
        val vizio = adapter(token = null)
        vizio.connect()
        server.takeRequest()

        vizio.submitPairingCode("1234")

        val pair = server.takeRequest()
        assertEquals("/pairing/pair", pair.path)
        val body = JSONObject(pair.body.readUtf8())
        assertEquals("1234", body.getString("RESPONSE_VALUE"))
        assertEquals(777, body.getInt("PAIRING_REQ_TOKEN"))
        assertEquals(ConnectionState.Connected, vizio.state.value)
        assertEquals("Ztoken", vizio.profile.pairingToken)
        assertNotNull(vizio.profile.pinnedCertificateSha256)
    }

    @Test
    fun `PIN incorrecto lanza error y deja reintentar`() = runTest {
        server.enqueue(ok("""{"PAIRING_REQ_TOKEN":777,"CHALLENGE_TYPE":1}"""))
        server.enqueue(MockResponse().setBody("""{"STATUS":{"RESULT":"INVALID_PIN"}}"""))
        val vizio = adapter(token = null)
        vizio.connect()

        try {
            vizio.submitPairingCode("0000")
            fail("debía lanzar")
        } catch (e: RemoteError.InvalidCode) {
            // esperado
        }
        assertTrue(vizio.state.value is ConnectionState.AwaitingPairing)
        assertEquals(null, vizio.profile.pairingToken)
    }

    @Test
    fun `VolumeUp envia KEYLIST con codeset 5 y code 1`() = runTest {
        server.enqueue(powerOn)
        server.enqueue(ok())
        val vizio = adapter(token = "Ztoken")
        vizio.connect()
        assertEquals("/state/device/power_mode", server.takeRequest().path)

        vizio.send(RemoteCommand.VolumeUp)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/key_command/", request.path)
        assertEquals("Ztoken", request.getHeader("AUTH"))
        val key = JSONObject(request.body.readUtf8()).getJSONArray("KEYLIST").getJSONObject(0)
        assertEquals(5, key.getInt("CODESET"))
        assertEquals(1, key.getInt("CODE"))
        assertEquals("KEYPRESS", key.getString("ACTION"))
    }

    @Test
    fun `token rechazado vuelve a pedir PIN`() = runTest {
        server.enqueue(MockResponse().setBody("""{"STATUS":{"RESULT":"INVALID_AUTH_TOKEN"}}"""))
        server.enqueue(ok("""{"PAIRING_REQ_TOKEN":5,"CHALLENGE_TYPE":1}"""))
        val vizio = adapter(token = "viejo")

        vizio.connect()

        assertTrue(vizio.state.value is ConnectionState.AwaitingPairing)
    }

    @Test
    fun `mapa de teclas`() {
        assertEquals(5 to 1, VizioKeys.codeFor(RemoteCommand.VolumeUp))
        assertEquals(3 to 2, VizioKeys.codeFor(RemoteCommand.DpadCenter))
        assertEquals(11 to 2, VizioKeys.codeFor(RemoteCommand.Power))
        assertEquals(null, VizioKeys.codeFor(RemoteCommand.TouchTap))
        assertFalse(adapter("t").supports(RemoteCommand.TouchDelta(1f, 1f)))
    }
}
