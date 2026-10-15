package com.universalremote.adapters.common

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * TLS "trust on first use" para TVs con certificados autofirmados (webOS, Tizen, Android TV).
 *
 * Sin huella fijada se acepta cualquier certificado y se recuerda en [seenFingerprint] para que el
 * adaptador la persista; con huella fijada sólo se acepta ese mismo certificado hoja.
 */
class TofuTrustManager(private val pinnedSha256: String?) : X509TrustManager {

    @Volatile
    var seenFingerprint: String? = null
        private set

    @Volatile
    var seenCertificate: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("El TV no presentó certificado")
        val fingerprint = sha256Hex(leaf.encoded)
        if (pinnedSha256 != null && !pinnedSha256.equals(fingerprint, ignoreCase = true)) {
            throw CertificateException("El certificado del TV cambió (posible suplantación); vuelve a añadir el equipo")
        }
        seenFingerprint = fingerprint
        seenCertificate = leaf
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Sólo cliente")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    fun sslContext(keyManagers: Array<KeyManager>? = null): SSLContext =
        SSLContext.getInstance("TLS").apply { init(keyManagers, arrayOf(this@TofuTrustManager), null) }

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
