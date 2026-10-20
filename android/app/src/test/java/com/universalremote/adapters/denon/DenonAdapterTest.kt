package com.universalremote.adapters.denon

import android.content.ContextWrapper
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.network.wol.WakeOnLanSender
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class DenonAdapterTest {

    private lateinit var server: ServerSocket

    @Before
    fun setUp() {
        server = ServerSocket(0).apply { soTimeout = 2_000 }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun adapter() = DenonAdapter(
        DeviceProfile(name = "AVR", kind = DeviceKind.DENON_AVR, transport = DeviceTransport.Lan("127.0.0.1", server.localPort)),
        WakeOnLanSender(ContextWrapper(null), Dispatchers.Unconfined),
    )

    /** Lee una línea terminada en CR, como las envía el protocolo de Denon. */
    private fun Socket.readCommand(): String = buildString {
        while (true) {
            val c = getInputStream().read()
            if (c == -1 || c == '\r'.code) break
            append(c.toChar())
        }
    }

    @Test
    fun `VolumeUp envia MVUP`() = runBlocking {
        val avr = adapter()
        avr.connect()
        server.accept().use { peer ->
            avr.send(RemoteCommand.VolumeUp)
            assertEquals("MVUP", peer.readCommand())
        }
    }

    @Test
    fun `Mute consulta el estado e invierte`() = runBlocking {
        val avr = adapter()
        avr.connect()
        server.accept().use { peer ->
            peer.soTimeout = 2_000
            val responder = Thread {
                assertEquals("MU?", peer.readCommand())
                peer.getOutputStream().write("MUOFF\r".toByteArray())
            }.apply { start() }
            avr.send(RemoteCommand.Mute)
            responder.join()
            assertEquals("MUON", peer.readCommand())
        }
    }

    @Test
    fun `sin canales`() {
        assertFalse(adapter().supports(RemoteCommand.ChannelUp))
    }
}
