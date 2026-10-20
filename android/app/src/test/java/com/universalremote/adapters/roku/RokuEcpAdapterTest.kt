package com.universalremote.adapters.roku

import androidx.test.core.app.ApplicationProvider
import com.universalremote.adapters.TestAdapters
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.network.wol.WakeOnLanSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric sólo para el parser XML de `device-info`; el resto es HTTP contra MockWebServer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RokuEcpAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter() = RokuEcpAdapter(
        DeviceProfile(name = "Roku", kind = DeviceKind.ROKU, transport = DeviceTransport.Lan(server.hostName, server.port)),
        TestAdapters.client,
        WakeOnLanSender(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined),
    )

    @Test
    fun `subir volumen hace POST a keypress VolumeUp`() = runTest {
        server.enqueue(MockResponse())

        adapter().send(RemoteCommand.VolumeUp)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/keypress/VolumeUp", request.path)
    }

    @Test
    fun `un video compartido abre el canal de YouTube con su contentId`() = runTest {
        server.enqueue(MockResponse())

        adapter().send(RemoteCommand.PlayYouTube("abc123"))

        assertEquals("/launch/837?contentId=abc123", server.takeRequest().path)
    }

    @Test
    fun `isPoweredOn lee power-mode de device-info`() = runTest {
        server.enqueue(deviceInfo("PowerOn"))
        server.enqueue(deviceInfo("DisplayOff"))
        val roku = adapter()

        assertTrue(roku.isPoweredOn()!!)
        assertFalse(roku.isPoweredOn()!!)
    }

    @Test
    fun `isPoweredOn es desconocido si el equipo no responde bien`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull(adapter().isPoweredOn())
    }

    private fun deviceInfo(powerMode: String) = MockResponse().setBody(
        "<device-info><is-tv>true</is-tv><power-mode>$powerMode</power-mode></device-info>",
    )
}
