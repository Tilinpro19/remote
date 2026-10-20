package com.universalremote.adapters.firetv

import com.universalremote.domain.model.RemoteError
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mensaje ADB: cabecera de 24 bytes little-endian + payload. */
class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray) {

    fun encode(): ByteArray = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(command)
        putInt(arg0)
        putInt(arg1)
        putInt(payload.size)
        putInt(payload.sumOf { it.toInt() and 0xff })
        putInt(command xor -1)
        put(payload)
    }.array()

    companion object {
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        const val VERSION = 0x01000000
        const val MAX_PAYLOAD = 4096
        private const val HEADER_SIZE = 24
        /** Límite defensivo: un payload mayor indica un flujo corrupto. */
        private const val MAX_ACCEPTED_PAYLOAD = 1 shl 20

        /** @throws RemoteError.Protocol si la cabecera no es ADB. */
        fun decode(stream: InputStream): AdbMessage {
            val input = stream as? DataInputStream ?: DataInputStream(stream)
            val header = ByteArray(HEADER_SIZE).also(input::readFully)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = buf.getInt(0)
            val length = buf.getInt(12)
            if (buf.getInt(20) != command xor -1 || length !in 0..MAX_ACCEPTED_PAYLOAD) {
                throw RemoteError.Protocol("Respuesta ADB inválida")
            }
            val payload = ByteArray(length).also(input::readFully)
            return AdbMessage(command, buf.getInt(4), buf.getInt(8), payload)
        }
    }
}

/** Firma y formato de clave pública que espera `adbd`. */
object AdbCrypto {

    /** DigestInfo DER de SHA-1: el token de 20 bytes se firma como si fuera su hash. */
    val SHA1_DIGEST_INFO: ByteArray = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
    )

    fun signToken(key: PrivateKey, token: ByteArray): ByteArray =
        Signature.getInstance("NONEwithRSA").run {
            initSign(key)
            update(SHA1_DIGEST_INFO + token)
            sign()
        }

    /**
     * Estructura `RSAPublicKey` de libmincrypt (palabras de 32 bits little-endian):
     * `len`, `n0inv`, `n[len]`, `rr[len]`, `exponent`; en base64 seguida de ` nombre\0`.
     */
    fun androidPublicKey(key: RSAPublicKey, name: String): ByteArray {
        val words = KEY_BYTES / 4
        val n = key.modulus
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32)).toInt()
        val rr = BigInteger.ONE.shiftLeft(KEY_BYTES * 8).pow(2).mod(n)
        val struct = ByteBuffer.allocate(4 + 4 + KEY_BYTES * 2 + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(words)
            putInt(n0inv)
            put(littleEndian(n))
            put(littleEndian(rr))
            putInt(key.publicExponent.toInt())
        }.array()
        return (Base64.getEncoder().encodeToString(struct) + " $name\u0000").toByteArray(Charsets.US_ASCII)
    }

    private fun littleEndian(value: BigInteger): ByteArray {
        val be = value.toByteArray().let { if (it.size > KEY_BYTES) it.copyOfRange(it.size - KEY_BYTES, it.size) else it }
        val out = ByteArray(KEY_BYTES)
        for (i in be.indices) out[i] = be[be.size - 1 - i]
        return out
    }

    const val KEY_BYTES = 256
}

/**
 * Cliente ADB mínimo por TCP (CNXN, AUTH, OPEN, WRTE, OKAY, CLSE): sólo lo necesario para
 * `shell:` de un disparo. No es seguro para uso concurrente; el adaptador serializa con un mutex.
 */
class AdbProtocol(private val socket: Socket, private val keyPair: KeyPair, private val keyName: String = "UniversalRemote") {

    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = socket.getOutputStream()
    private var nextLocalId = 1

    /**
     * Completa el handshake. Si el equipo no conoce la clave, envía la pública y llama a
     * [onAuthPrompt]: el Fire TV muestra "¿Permitir depuración?" y el CNXN llega al aceptar.
     */
    suspend fun connect(onAuthPrompt: () -> Unit = {}) = io {
        write(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, "host::\u0000".toByteArray()))
        var signed = false
        var sentPublicKey = false
        while (true) {
            val msg = AdbMessage.decode(input)
            when (msg.command) {
                AdbMessage.CNXN -> return@io
                AdbMessage.AUTH -> {
                    if (msg.arg0 != AdbMessage.AUTH_TOKEN) throw RemoteError.Protocol("AUTH ADB inesperado")
                    when {
                        !signed -> {
                            signed = true
                            write(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_SIGNATURE, 0, AdbCrypto.signToken(keyPair.private, msg.payload)))
                        }
                        !sentPublicKey -> {
                            sentPublicKey = true
                            val pub = keyPair.public as RSAPublicKey
                            write(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_RSAPUBLICKEY, 0, AdbCrypto.androidPublicKey(pub, keyName)))
                            onAuthPrompt()
                        }
                        else -> throw RemoteError.PairingRejected()
                    }
                }
                else -> throw RemoteError.Protocol("Mensaje ADB inesperado durante el handshake")
            }
        }
    }

    /** Ejecuta `shell:<command>` y devuelve su salida completa. */
    suspend fun shell(command: String): String = io {
        val localId = nextLocalId++
        write(AdbMessage(AdbMessage.OPEN, localId, 0, "shell:$command\u0000".toByteArray()))
        val output = StringBuilder()
        var remoteId = 0
        while (true) {
            val msg = AdbMessage.decode(input)
            when (msg.command) {
                AdbMessage.OKAY -> remoteId = msg.arg0
                AdbMessage.WRTE -> {
                    remoteId = msg.arg0
                    output.append(String(msg.payload, Charsets.UTF_8))
                    write(AdbMessage(AdbMessage.OKAY, localId, remoteId, ByteArray(0)))
                }
                AdbMessage.CLSE -> {
                    write(AdbMessage(AdbMessage.CLSE, localId, msg.arg0.takeIf { it != 0 } ?: remoteId, ByteArray(0)))
                    return@io output.toString()
                }
                else -> throw RemoteError.Protocol("Mensaje ADB inesperado en shell")
            }
        }
        @Suppress("UNREACHABLE_CODE")
        output.toString()
    }

    private fun write(message: AdbMessage) {
        output.write(message.encode())
        output.flush()
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: EOFException) {
            throw RemoteError.Network(e)
        } catch (t: Throwable) {
            throw RemoteError.from(t, socket.inetAddress?.hostAddress)
        }
    }
}
