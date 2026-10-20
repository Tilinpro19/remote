package com.universalremote.adapters.firetv

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Par RSA 2048 con el que el móvil se identifica ante `adbd` (equivalente a `~/.android/adbkey`).
 * Se genera una vez y se guarda en PKCS#8 en `filesDir`, privado de la app, igual que la identidad
 * de Android TV. Si se pierde, el Fire TV vuelve a pedir "¿Permitir depuración?".
 */
@Singleton
class AdbKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var cached: KeyPair? = null

    @Synchronized
    fun keyPair(): KeyPair {
        cached?.let { return it }
        val file = File(context.filesDir, FILE_NAME)
        val loaded = runCatching { if (file.exists()) read(file) else null }.getOrNull() ?: generate(file)
        cached = loaded
        return loaded
    }

    private fun read(file: File): KeyPair {
        val factory = KeyFactory.getInstance("RSA")
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(file.readBytes())) as RSAPrivateCrtKey
        val public = factory.generatePublic(RSAPublicKeySpec(private.modulus, private.publicExponent))
        return KeyPair(public, private)
    }

    private fun generate(file: File): KeyPair {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS, SecureRandom()) }.generateKeyPair()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeBytes(pair.private.encoded)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        return pair
    }

    private companion object {
        const val FILE_NAME = "adb_key.pk8"
        const val KEY_BITS = 2048
    }
}
