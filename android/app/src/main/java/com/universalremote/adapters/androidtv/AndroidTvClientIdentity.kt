package com.universalremote.adapters.androidtv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.universalremote.adapters.common.TofuTrustManager
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

/**
 * Certificado de cliente con el que el móvil se identifica ante los Android TV.
 *
 * La clave RSA vive en AndroidKeyStore (no extraíble) y es común a todos los TVs: el TV recuerda el
 * certificado tras el emparejamiento. Si la app se reinstala la clave cambia y hay que re-emparejar,
 * lo que el adaptador detecta comparando [fingerprint] con el token guardado.
 */
@Singleton
class AndroidTvClientIdentity @Inject constructor() {

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    @Synchronized
    fun certificate(): X509Certificate {
        if (!keyStore.containsAlias(ALIAS)) generate()
        return keyStore.getCertificate(ALIAS) as X509Certificate
    }

    fun fingerprint(): String = TofuTrustManager.sha256Hex(certificate().encoded)

    fun keyManagers(): Array<KeyManager> {
        val cert = certificate()
        val key = keyStore.getKey(ALIAS, null) as PrivateKey
        return arrayOf(SingleKeyManager(cert, key))
    }

    private fun generate() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(2048)
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512,
            )
            // PKCS#1 para TLS 1.2 y PSS para TLS 1.3.
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            .setCertificateSubject(X500Principal("CN=atvremote/UniversalRemote"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - DAY_MS))
            .setCertificateNotAfter(Date(now + VALIDITY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
            initialize(spec)
            generateKeyPair()
        }
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
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "atv-remote-client"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val VALIDITY_MS = 20L * 365 * DAY_MS
    }
}
