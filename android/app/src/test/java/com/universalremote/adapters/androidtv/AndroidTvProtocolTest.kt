package com.universalremote.adapters.androidtv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidTvProtocolTest {

    @Test
    fun `protobuf round trip con mensajes anidados y varint largo`() {
        val bytes = ProtoWriter().int(1, 300).message(10) { string(1, "atvremote") }.toByteArray()
        val parsed = ProtoMessage.parse(bytes)
        assertEquals(300L, parsed.int(1))
        assertEquals("atvremote", parsed.message(10)?.string(1))
    }

    @Test
    fun `key inject codifica campo 10 con tecla y direccion SHORT`() {
        // 0x52 = campo 10 length-delimited; 0x08 0x13 = key_code 19; 0x10 0x03 = direction 3.
        assertArrayEquals(byteArrayOf(0x52, 0x04, 0x08, 0x13, 0x10, 0x03), AndroidTvProtocol.keyInject(19))
    }

    @Test
    fun `pairing request incluye version 2 y estado 200`() {
        val parsed = ProtoMessage.parse(AndroidTvProtocol.pairingRequest("test"))
        assertEquals(2L, parsed.int(1))
        assertEquals(200L, parsed.int(2))
        assertEquals("test", parsed.message(10)?.string(2))
    }

    @Test
    fun `framing lee mensajes de mas de 127 bytes`() {
        val payload = ByteArray(300) { it.toByte() }
        val out = ByteArrayOutputStream()
        ProtoFraming.write(out, payload)
        assertArrayEquals(payload, ProtoFraming.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun `el secreto valida el primer byte del codigo como checksum`() {
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }
        val client = gen.generateKeyPair().public as RSAPublicKey
        val server = gen.generateKeyPair().public as RSAPublicKey
        val tail = byteArrayOf(0x12, 0x34)
        val expected = MessageDigest.getInstance("SHA-256").apply {
            update(client.modulus.unsigned())
            update(byteArrayOf(1, 0, 1))
            update(server.modulus.unsigned())
            update(byteArrayOf(1, 0, 1))
            update(tail)
        }.digest()
        val good = "%02X1234".format(expected[0])
        assertArrayEquals(expected, AndroidTvProtocol.computeSecret(client, server, good.lowercase()))

        val bad = "%02X1234".format((expected[0] + 1).toByte())
        assertNull(AndroidTvProtocol.computeSecret(client, server, bad))
        assertNull(AndroidTvProtocol.computeSecret(client, server, "XYZ"))
        assertNotNull(AndroidTvProtocol.computeSecret(client, server, " $good "))
    }

    private fun BigInteger.unsigned(): ByteArray = toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
}
