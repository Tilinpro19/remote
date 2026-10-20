package com.universalremote.adapters.philips

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
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PhilipsJointSpaceAdapterTest {

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

    private fun adapter(token: String?) = PhilipsJointSpaceAdapter(
        DeviceProfile(
            id = "dev-1",
            name = "Philips",
            kind = DeviceKind.PHILIPS_JOINTSPACE,
            transport = DeviceTransport.Lan(server.hostName, server.port),
            pairingToken = token,
        ),
        TestAdapters.client,
        WakeOnLanSender(ContextWrapper(null), Dispatchers.Unconfined),
    )

    private val challenge = MockResponse()
        .setResponseCode(401)
        .addHeader("WWW-Authenticate", """Digest realm="XTV", nonce="abc123", qop="auth"""")

    @Test
    fun `sin credenciales pide el PIN tras pair request`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error_id":"SUCCESS","auth_key":"secreto","timestamp":4242,"timeout":60}"""))
        val tv = adapter(token = null)

        tv.connect()

        assertEquals(PairingRequest.Method.Pin(4), (tv.state.value as ConnectionState.AwaitingPairing).request.method)
        val request = server.takeRequest()
        assertEquals("/6/pair/request", request.path)
        assertEquals("dev-1", JSONObject(request.body.readUtf8()).getJSONObject("device").getString("id"))
    }

    @Test
    fun `grant firma timestamp mas PIN con HMAC-SHA1 y usa digest`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error_id":"SUCCESS","auth_key":"secreto","timestamp":4242,"timeout":60}"""))
        server.enqueue(challenge)
        server.enqueue(MockResponse().setBody("""{"error_id":"SUCCESS"}"""))
        val tv = adapter(token = null)
        tv.connect()
        server.takeRequest()

        tv.submitPairingCode("1234")

        server.takeRequest() // primer intento sin credenciales (401)
        val grant = server.takeRequest()
        assertEquals("/6/pair/grant", grant.path)
        val auth = grant.getHeader("Authorization")!!
        assertTrue(auth.startsWith("Digest "))
        assertTrue(auth.contains("""username="dev-1""""))
        assertTrue(auth.contains("""nonce="abc123""""))
        val body = JSONObject(grant.body.readUtf8()).getJSONObject("auth")
        assertEquals("1234", body.getString("pin"))
        assertEquals(4242, body.getInt("auth_timestamp"))
        assertEquals(expectedSignature("4242", "1234"), body.getString("auth_signature"))
        assertEquals(ConnectionState.Connected, tv.state.value)
        assertEquals("secreto", tv.profile.pairingToken)
    }

    @Test
    fun `PIN rechazado permite reintentar`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error_id":"SUCCESS","auth_key":"secreto","timestamp":1,"timeout":60}"""))
        server.enqueue(MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", """Digest realm="XTV", nonce="n", qop="auth""""))
        server.enqueue(MockResponse().setBody("""{"error_id":"INVALID_PIN"}"""))
        val tv = adapter(token = null)
        tv.connect()

        try {
            tv.submitPairingCode("0000")
            fail("debía lanzar")
        } catch (e: RemoteError.InvalidCode) {
            // esperado
        }
        assertTrue(tv.state.value is ConnectionState.AwaitingPairing)
        assertNull(tv.profile.pairingToken)
    }

    @Test
    fun `DpadUp envia CursorUp`() = runTest {
        server.enqueue(MockResponse().setBody("""{"powerstate":"On"}"""))
        server.enqueue(MockResponse().setBody(""))
        val tv = adapter(token = "secreto")
        tv.connect()
        assertEquals("/6/powerstate", server.takeRequest().path)

        tv.send(RemoteCommand.DpadUp)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/6/input/key", request.path)
        assertEquals("CursorUp", JSONObject(request.body.readUtf8()).getString("key"))
    }

    @Test
    fun `claves y soporte`() {
        assertEquals("VolumeUp", PhilipsKeys.nameFor(RemoteCommand.VolumeUp))
        assertEquals("Digit7", PhilipsKeys.nameFor(RemoteCommand.Digit(7)))
        assertEquals("Confirm", PhilipsKeys.nameFor(RemoteCommand.DpadCenter))
        assertNull(PhilipsKeys.nameFor(RemoteCommand.TouchTap))
        assertFalse(adapter("t").supports(RemoteCommand.TouchDelta(1f, 1f)))
    }

    private fun expectedSignature(timestamp: String, pin: String): String {
        val key = Base64.getDecoder().decode(PhilipsJointSpaceAdapter.SHARED_KEY_B64)
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }
        return Base64.getEncoder().encodeToString(mac.doFinal((timestamp + pin).toByteArray()))
    }
}
