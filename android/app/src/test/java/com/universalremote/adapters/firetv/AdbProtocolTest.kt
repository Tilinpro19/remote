package com.universalremote.adapters.firetv

import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import java.io.DataInputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AdbProtocolTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private var server: ServerSocket? = null

    @After
    fun tearDown() {
        server?.close()
    }

    @Test
    fun `CNXN tiene cabecera de 24 bytes y magic = cmd xor ffffffff`() {
        val payload = "host::\u0000".toByteArray()
        val packet = AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, payload).encode()

        assertEquals(24 + payload.size, packet.size)
        val header = ByteBuffer.wrap(packet, 0, 24).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x4e584e43, header.getInt(0))
        assertEquals(AdbMessage.VERSION, header.getInt(4))
        assertEquals(payload.size, header.getInt(12))
        assertEquals(payload.sumOf { it.toInt() and 0xff }, header.getInt(16))
        assertEquals(0x4e584e43 xor -1, header.getInt(20))
    }

    @Test
    fun `la firma AUTH es PKCS1 SHA1 del token`() {
        val token = ByteArray(20) { it.toByte() }

        val signature = AdbCrypto.signToken(keyPair.private, token)

        // El token se trata como un digest SHA-1 ya calculado: verificar con NONEwithRSA sobre DigestInfo.
        val verifier = Signature.getInstance("NONEwithRSA").apply { initVerify(keyPair.public) }
        verifier.update(AdbCrypto.SHA1_DIGEST_INFO + token)
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `clave publica en formato Android`() {
        val encoded = AdbCrypto.androidPublicKey(keyPair.public as RSAPublicKey, "user@phone")
        val text = String(encoded, Charsets.US_ASCII)
        assertTrue(text.endsWith(" user@phone\u0000"))

        val raw = Base64.getDecoder().decode(text.substringBefore(' '))
        assertEquals(4 + 4 + 256 + 256 + 4, raw.size)
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, buf.getInt(0))
        val n = (keyPair.public as RSAPublicKey).modulus
        val n0 = n.and(BigInteger.valueOf(0xffffffffL)).toInt()
        assertEquals(0, n0 * buf.getInt(4) + 1) // n0inv = -1/n[0] mod 2^32
        val modulusLe = raw.copyOfRange(8, 8 + 256)
        assertEquals(n, BigInteger(1, modulusLe.reversedArray()))
        assertEquals(65537, buf.getInt(8 + 256 + 256))
    }

    @Test
    fun `handshake con clave ya autorizada y shell devuelve la salida`() = runTest {
        val device = fakeDevice(acceptSignature = true)
        Socket("127.0.0.1", device).use { socket ->
            val adb = AdbProtocol(socket, keyPair)
            adb.connect()

            val out = adb.shell("input keyevent 24")

            assertEquals("ok\n", out)
        }
        assertEquals("shell:input keyevent 24", lastOpen)
    }

    @Test
    fun `clave no autorizada envia la publica y avisa`() = runTest {
        val device = fakeDevice(acceptSignature = false)
        var prompted = false
        Socket("127.0.0.1", device).use { socket ->
            AdbProtocol(socket, keyPair).connect(onAuthPrompt = { prompted = true })
        }
        assertTrue(prompted)
        assertTrue(receivedPublicKey)
    }

    @Test
    fun `respuesta corrupta lanza RemoteError Protocol`() = runTest {
        server = ServerSocket(0)
        val port = server!!.localPort
        thread {
            server!!.accept().use {
                read(DataInputStream(it.getInputStream()))
                it.getOutputStream().write(ByteArray(24) { 7 })
                it.getInputStream().read()
            }
        }
        Socket("127.0.0.1", port).use { socket ->
            try {
                AdbProtocol(socket, keyPair).connect()
                fail("debía lanzar")
            } catch (e: RemoteError.Protocol) {
                // esperado
            }
        }
    }

    @Test
    fun `input text escapa espacios y comillas`() {
        assertEquals("input text 'hola%sque%stal'", FireTvKeys.inputTextCommand("hola que tal"))
        assertEquals("input text 'it'\\''s'", FireTvKeys.inputTextCommand("it's"))
        assertEquals("input text '\$(rm)'", FireTvKeys.inputTextCommand("\$(rm)"))
    }

    @Test
    fun `teclas de Fire TV`() {
        assertEquals(24, FireTvKeys.keyEventFor(RemoteCommand.VolumeUp))
        assertEquals(23, FireTvKeys.keyEventFor(RemoteCommand.DpadCenter))
        assertEquals(3, FireTvKeys.keyEventFor(RemoteCommand.Home))
        assertEquals(null, FireTvKeys.keyEventFor(RemoteCommand.TouchTap))
    }

    // ------------------------------------------------------------------ Fire TV simulado

    @Volatile private var lastOpen: String? = null
    @Volatile private var receivedPublicKey = false

    private fun fakeDevice(acceptSignature: Boolean): Int {
        val ss = ServerSocket(0).also { server = it }
        thread {
            ss.accept().use { s ->
                val input = DataInputStream(s.getInputStream())
                val out = s.getOutputStream()
                check(read(input).command == AdbMessage.CNXN)
                out.write(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, ByteArray(20) { 9 }).encode())
                val auth = read(input)
                check(auth.command == AdbMessage.AUTH && auth.arg0 == AdbMessage.AUTH_SIGNATURE)
                if (!acceptSignature) {
                    out.write(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, ByteArray(20) { 8 }).encode())
                    val pub = read(input)
                    receivedPublicKey = pub.command == AdbMessage.AUTH && pub.arg0 == AdbMessage.AUTH_RSAPUBLICKEY
                }
                out.write(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, "device::\u0000".toByteArray()).encode())
                if (!acceptSignature) return@thread
                val open = read(input)
                check(open.command == AdbMessage.OPEN)
                lastOpen = String(open.payload).trimEnd('\u0000')
                val local = open.arg0
                out.write(AdbMessage(AdbMessage.OKAY, 77, local, ByteArray(0)).encode())
                out.write(AdbMessage(AdbMessage.WRTE, 77, local, "ok\n".toByteArray()).encode())
                check(read(input).command == AdbMessage.OKAY)
                out.write(AdbMessage(AdbMessage.CLSE, 77, local, ByteArray(0)).encode())
                runCatching { read(input) } // CLSE del cliente
            }
        }
        return ss.localPort
    }

    private fun read(input: DataInputStream): AdbMessage {
        val header = ByteArray(24).also(input::readFully)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val payload = ByteArray(buf.getInt(12)).also(input::readFully)
        return AdbMessage(buf.getInt(0), buf.getInt(4), buf.getInt(8), payload)
    }

    @Test
    fun `payload se decodifica igual que se codifica`() {
        val msg = AdbMessage(AdbMessage.WRTE, 1, 2, byteArrayOf(1, 2, 3))
        val decoded = AdbMessage.decode(msg.encode().inputStream())
        assertEquals(msg.command, decoded.command)
        assertArrayEquals(msg.payload, decoded.payload)
    }
}
