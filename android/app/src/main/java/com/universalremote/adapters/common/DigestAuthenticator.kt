package com.universalremote.adapters.common

import java.security.MessageDigest
import java.security.SecureRandom
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * HTTP Digest (RFC 7616, MD5, `qop=auth`) para OkHttp, que sólo trae Basic.
 * Se usa con la API JointSpace de Philips. Devuelve `null` tras un segundo 401 para no entrar en bucle.
 */
class DigestAuthenticator(private val username: String, private val password: String) : Authenticator {

    private val random = SecureRandom()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") != null) return null
        val challenge = response.headers("WWW-Authenticate")
            .firstOrNull { it.startsWith("Digest", ignoreCase = true) } ?: return null
        val params = parseParams(challenge.substringAfter(' '))
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val qop = params["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }

        val request = response.request
        val uri = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("${request.method}:$uri")
        val nc = "00000001"
        val cnonce = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val digest = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")

        val header = buildString {
            append("Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", ")
            append("algorithm=MD5, response=\"$digest\"")
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            params["opaque"]?.let { append(", opaque=\"$it\"") }
        }
        return request.newBuilder().header("Authorization", header).build()
    }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val PARAM = Regex("""(\w+)=(?:"([^"]*)"|([^,\s]*))""")

        fun parseParams(text: String): Map<String, String> =
            PARAM.findAll(text).associate { m -> m.groupValues[1].lowercase() to (m.groups[2]?.value ?: m.groupValues[3]) }
    }
}
