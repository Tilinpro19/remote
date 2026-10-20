package com.universalremote.adapters.sony

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SonyBraviaAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(psk: String?) = SonyBraviaAdapter(
        DeviceProfile(
            name = "Bravia",
            kind = DeviceKind.SONY_BRAVIA,
            transport = DeviceTransport.Lan(server.hostName, server.port),
            pairingToken = psk,
        ),
        TestAdapters.client,
        WakeOnLanSender(ContextWrapper(null), Dispatchers.Unconfined),
    )

    private fun powerStatus(status: String = "active") =
        MockResponse().setBody("""{"result":[{"status":"$status"}],"id":50}""")

    private val sysInfo =
        MockResponse().setBody("""{"result":[{"model":"KD-55X80J","serial":"123","macAddr":"aa:bb:cc:dd:ee:ff"}],"id":51}""")

    @Test
    fun `sin PSK queda esperando la clave`() = runTest {
        val sony = adapter(psk = null)

        sony.connect()

        val state = sony.state.value as ConnectionState.AwaitingPairing
        assertTrue(state.request.method is PairingRequest.Method.Passphrase)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `PSK correcto conecta, guarda la clave y aprende la MAC`() = runTest {
        server.enqueue(powerStatus())
        server.enqueue(sysInfo)
        val sony = adapter(psk = null)
        sony.connect()

        sony.submitPairingCode("0000")

        assertEquals(ConnectionState.Connected, sony.state.value)
        assertEquals("0000", sony.profile.pairingToken)
        assertEquals("AA:BB:CC:DD:EE:FF", sony.profile.macAddress?.value)
        val first = server.takeRequest()
        assertEquals("/sony/system", first.path)
        assertEquals("0000", first.getHeader("X-Auth-PSK"))
        assertTrue(first.body.readUtf8().contains("getPowerStatus"))
    }

    @Test
    fun `PSK incorrecto lanza PairingRejected y permite reintentar`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val sony = adapter(psk = null)
        sony.connect()

        try {
            sony.submitPairingCode("mala")
            fail("debía lanzar")
        } catch (e: RemoteError.PairingRejected) {
            // esperado
        }

        assertTrue(sony.state.value is ConnectionState.AwaitingPairing)
        assertEquals(null, sony.profile.pairingToken)
    }

    @Test
    fun `error 403 en el cuerpo JSON tambien es PairingRejected`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":[403,"Forbidden"],"id":50}"""))
        val sony = adapter(psk = null)
        sony.connect()

        try {
            sony.submitPairingCode("mala")
            fail("debía lanzar")
        } catch (e: RemoteError.PairingRejected) {
            // esperado
        }
    }

    @Test
    fun `VolumeUp envia IRCC con PSK`() = runTest {
        server.enqueue(powerStatus())
        server.enqueue(sysInfo)
        server.enqueue(MockResponse().setBody("<ok/>"))
        val sony = adapter(psk = "1234")
        sony.connect()
        server.takeRequest()
        server.takeRequest()

        sony.send(RemoteCommand.VolumeUp)

        val request = server.takeRequest()
        assertEquals("/sony/IRCC", request.path)
        assertEquals("1234", request.getHeader("X-Auth-PSK"))
        assertTrue(request.getHeader("SOAPACTION")!!.contains("X_SendIRCC"))
        assertTrue(request.body.readUtf8().contains("<IRCCCode>AAAAAQAAAAEAAAASAw==</IRCCCode>"))
    }

    @Test
    fun `PowerOff usa la API REST`() = runTest {
        server.enqueue(powerStatus())
        server.enqueue(sysInfo)
        server.enqueue(MockResponse().setBody("""{"result":[],"id":55}"""))
        val sony = adapter(psk = "1234")
        sony.connect()
        server.takeRequest()
        server.takeRequest()

        sony.send(RemoteCommand.PowerOff)

        val request = server.takeRequest()
        assertEquals("/sony/system", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("setPowerStatus"))
        assertTrue(body.contains("false"))
    }

    @Test
    fun `send sin sesion lanza NotConnected`() = runTest {
        val sony = adapter(psk = "1234")
        try {
            sony.send(RemoteCommand.VolumeUp)
            fail("debía lanzar")
        } catch (e: RemoteError.NotConnected) {
            // esperado
        }
    }

    @Test
    fun `equipo apagado falla con Unreachable`() = runTest {
        val sony = adapter(psk = "1234")
        server.shutdown()
        try {
            sony.connect()
            fail("debía lanzar")
        } catch (e: RemoteError.Unreachable) {
            // esperado
        }
        assertTrue(sony.state.value is ConnectionState.Failed)
    }

    @Test
    fun `no soporta puntero`() {
        val sony = adapter(psk = "1234")
        assertFalse(sony.supports(RemoteCommand.TouchDelta(1f, 1f)))
        assertTrue(sony.supports(RemoteCommand.VolumeUp))
        assertTrue(sony.supports(RemoteCommand.Digit(7)))
    }

    @Test
    fun `codigos IRCC conocidos`() {
        assertEquals("AAAAAQAAAAEAAAASAw==", SonyKeys.codeFor(RemoteCommand.VolumeUp))
        assertEquals("AAAAAQAAAAEAAAATAw==", SonyKeys.codeFor(RemoteCommand.VolumeDown))
        assertEquals("AAAAAQAAAAEAAAAJAw==", SonyKeys.codeFor(RemoteCommand.Digit(0)))
        assertEquals("AAAAAQAAAAEAAAAAAw==", SonyKeys.codeFor(RemoteCommand.Digit(1)))
        assertEquals("AAAAAQAAAAEAAAB0Aw==", SonyKeys.codeFor(RemoteCommand.DpadUp))
        assertEquals(null, SonyKeys.codeFor(RemoteCommand.TouchTap))
    }
}
