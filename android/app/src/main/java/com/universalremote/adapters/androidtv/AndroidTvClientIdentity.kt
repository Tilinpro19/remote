package com.universalremote.adapters.androidtv

import android.content.Context
import com.universalremote.adapters.common.TofuTrustManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Certificado de cliente con el que el móvil se identifica ante los Android TV.
 *
 * La clave RSA 2048 se genera por software y se guarda en un PKCS12 en `filesDir` (privado de la
 * app). No se usa AndroidKeyStore: en muchos equipos su RSA no firma bien el CertificateVerify de
 * TLS y Conscrypt aborta con `04000044 RSA routines:OPENSSL_internal:internal error`.
 *
 * Es común a todos los TVs: el TV recuerda el certificado tras el emparejamiento. Si cambia (p. ej.
 * reinstalación) hay que re-emparejar, lo que el adaptador detecta comparando [fingerprint] con el
 * token guardado.
 */
@Singleton
class AndroidTvClientIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var cached: Pair<X509Certificate, PrivateKey>? = null

    fun certificate(): X509Certificate = load().first

    fun fingerprint(): String = TofuTrustManager.sha256Hex(certificate().encoded)

    fun keyManagers(): Array<KeyManager> {
        val (cert, key) = load()
        return arrayOf(SingleKeyManager(cert, key))
    }

    @Synchronized
    private fun load(): Pair<X509Certificate, PrivateKey> {
        cached?.let { return it }
        val file = File(context.filesDir, FILE_NAME)
        val loaded = runCatching { if (file.exists()) read(file) else null }.getOrNull() ?: generate(file)
        cached = loaded
        return loaded
    }

    private fun read(file: File): Pair<X509Certificate, PrivateKey>? {
        val store = KeyStore.getInstance(STORE_TYPE).apply { file.inputStream().use { load(it, PASSWORD) } }
        val cert = store.getCertificate(ALIAS) as? X509Certificate ?: return null
        val key = store.getKey(ALIAS, PASSWORD) as? PrivateKey ?: return null
        return cert to key
    }

    private fun generate(file: File): Pair<X509Certificate, PrivateKey> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS, SecureRandom()) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val cert = SelfSignedCertificate.create(keyPair, COMMON_NAME, Date(now - DAY_MS), Date(now + VALIDITY_MS))
        val store = KeyStore.getInstance(STORE_TYPE).apply {
            load(null, null)
            setKeyEntry(ALIAS, keyPair.private, PASSWORD, arrayOf(cert))
        }
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.outputStream().use { store.store(it, PASSWORD) }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        return cert to keyPair.private
    }

    private class SingleKeyManager(private val cert: X509Certificate, private val key: PrivateKey) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(cert)
        override fun getPrivateKey(alias: String?): PrivateKey = key
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    }

    private companion object {
        const val FILE_NAME = "atv_client_identity.p12"
        const val STORE_TYPE = "PKCS12"
        const val ALIAS = "atv-remote-client"
        const val COMMON_NAME = "atvremote/UniversalRemote"
        const val KEY_BITS = 2048
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val VALIDITY_MS = 20L * 365 * DAY_MS

        /** El fichero ya es privado de la app; la contraseña sólo satisface el formato PKCS12. */
        val PASSWORD = "universalremote".toCharArray()
    }
}

/**
 * Certificado X.509 v3 autofirmado (SHA256withRSA) codificado a mano en DER: Android no expone un
 * generador de certificados público y así se evita depender de BouncyCastle.
 */
internal object SelfSignedCertificate {

    fun create(keyPair: KeyPair, commonName: String, notBefore: Date, notAfter: Date): X509Certificate {
        val algorithm = seq(SHA256_WITH_RSA, byteArrayOf(0x05, 0x00))
        val name = seq(set(seq(OID_COMMON_NAME, tlv(0x0C, commonName.toByteArray(Charsets.UTF_8)))))
        val serial = BigInteger(63, SecureRandom()).add(BigInteger.ONE)
        // basicConstraints (cA = false) para que sea un certificado de entidad final v3 válido.
        val extensions = tlv(0xA3, seq(seq(OID_BASIC_CONSTRAINTS, tlv(0x04, seq()))))
        val tbs = seq(
            tlv(0xA0, tlv(0x02, byteArrayOf(2))),
            tlv(0x02, serial.toByteArray()),
            algorithm,
            name,
            seq(utcTime(notBefore), utcTime(notAfter)),
            name,
            keyPair.public.encoded,
            extensions,
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }
        val der = seq(tbs, algorithm, tlv(0x03, byteArrayOf(0) + signature))
        return CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
    }

    /** 1.2.840.113549.1.1.11 */
    private val SHA256_WITH_RSA = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)

    /** 2.5.4.3 */
    private val OID_COMMON_NAME = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)

    /** 2.5.29.19 */
    private val OID_BASIC_CONSTRAINTS = byteArrayOf(0x06, 0x03, 0x55, 0x1D, 0x13)

    private fun utcTime(date: Date): ByteArray {
        val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return tlv(0x17, format.format(date).toByteArray(Charsets.US_ASCII))
    }

    private fun seq(vararg parts: ByteArray) = tlv(0x30, concat(parts))

    private fun set(vararg parts: ByteArray) = tlv(0x31, concat(parts))

    private fun concat(parts: Array<out ByteArray>): ByteArray =
        ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val length = content.size
        if (length < 0x80) {
            out.write(length)
        } else {
            val bytes = BigInteger.valueOf(length.toLong()).toByteArray().dropWhile { it == 0.toByte() }
            out.write(0x80 or bytes.size)
            bytes.forEach { out.write(it.toInt()) }
        }
        out.write(content)
        return out.toByteArray()
    }
}
