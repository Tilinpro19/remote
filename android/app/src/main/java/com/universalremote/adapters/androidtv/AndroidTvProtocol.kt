package com.universalremote.adapters.androidtv

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * Mensajes de Android TV Remote v2.
 * - Puerto 6467: emparejamiento "Polo" (`OuterMessage`).
 * - Puerto 6466: canal de control (`RemoteMessage`).
 */
internal object AndroidTvProtocol {

    // ------------------------------------------------------------------ Polo (emparejamiento)

    const val STATUS_OK = 200L
    const val STATUS_BAD_SECRET = 402L

    private const val PROTOCOL_VERSION = 2
    private const val ENCODING_HEXADECIMAL = 3
    private const val CODE_LENGTH = 6
    private const val ROLE_INPUT = 1

    private fun outer(block: ProtoWriter.() -> Unit): ByteArray = ProtoWriter()
        .int(1, PROTOCOL_VERSION)
        .int(2, STATUS_OK)
        .apply(block)
        .toByteArray()

    private fun ProtoWriter.hexEncoding(field: Int) = message(field) {
        int(1, ENCODING_HEXADECIMAL)
        int(2, CODE_LENGTH)
    }

    fun pairingRequest(clientName: String): ByteArray = outer {
        message(10) {
            string(1, "atvremote")
            string(2, clientName)
        }
    }

    fun options(): ByteArray = outer {
        message(20) {
            hexEncoding(1)
            int(3, ROLE_INPUT)
        }
    }

    fun configuration(): ByteArray = outer {
        message(30) {
            hexEncoding(1)
            int(2, ROLE_INPUT)
        }
    }

    fun secret(secret: ByteArray): ByteArray = outer { message(40) { bytes(1, secret) } }

    /** Estado de un `OuterMessage` recibido (200 = OK). */
    fun status(message: ByteArray): Long = ProtoMessage.parse(message).int(2) ?: 0L

    /**
     * Secreto = SHA-256(módulo y exponente del cliente, módulo y exponente del servidor, bytes 2..3
     * del código). El primer byte del código es una suma de control: debe coincidir con hash[0].
     *
     * @return el secreto, o `null` si el código no corresponde a este emparejamiento.
     */
    fun computeSecret(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
        val normalized = code.trim().uppercase()
        if (normalized.length != CODE_LENGTH || normalized.any { it !in "0123456789ABCDEF" }) return null
        val codeBytes = normalized.chunked(2).map { it.toInt(16).toByte() }
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(client.modulus.unsignedBytes())
            update(client.publicExponent.unsignedBytes())
            update(server.modulus.unsignedBytes())
            update(server.publicExponent.unsignedBytes())
            update(byteArrayOf(codeBytes[1], codeBytes[2]))
        }.digest()
        return digest.takeIf { it[0] == codeBytes[0] }
    }

    private fun BigInteger.unsignedBytes(): ByteArray {
        val raw = toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }

    // ------------------------------------------------------------------ RemoteMessage (control)

    const val FIELD_CONFIGURE = 1
    const val FIELD_SET_ACTIVE = 2
    const val FIELD_PING_REQUEST = 8
    const val FIELD_IME_SHOW = 22
    const val FIELD_START = 40
    private const val FIELD_APP_LINK_LAUNCH = 90

    private const val FEATURES = 622
    private const val DIRECTION_SHORT = 3

    fun configure(model: String, vendor: String, packageName: String, appVersion: String): ByteArray = ProtoWriter()
        .message(FIELD_CONFIGURE) {
            int(1, FEATURES)
            message(2) {
                string(1, model)
                string(2, vendor)
                int(3, 1)
                string(4, "1")
                string(5, packageName)
                string(6, appVersion)
            }
        }.toByteArray()

    fun setActive(): ByteArray = ProtoWriter().message(FIELD_SET_ACTIVE) { int(1, FEATURES) }.toByteArray()

    fun pingResponse(value: Long): ByteArray = ProtoWriter().message(9) { int(1, value) }.toByteArray()

    fun appLinkLaunch(link: String): ByteArray = ProtoWriter().message(FIELD_APP_LINK_LAUNCH) { string(1, link) }.toByteArray()

    fun keyInject(keyCode: Int): ByteArray = ProtoWriter().message(10) {
        int(1, keyCode)
        int(2, DIRECTION_SHORT)
    }.toByteArray()
}
