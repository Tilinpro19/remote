package com.universalremote

import java.net.URI
import java.net.URISyntaxException

enum class Kind { YOUTUBE, OTHER }

/** Contenido compartido desde otra app: id del vídeo para YouTube, la URL completa en otro caso. */
data class SharedMedia(val kind: Kind, val id: String)

private val URL = Regex("""https?://\S+""")
private val VIDEO_ID = Regex("[A-Za-z0-9_-]+")

/** Extrae la primera URL del texto compartido; `null` si no hay ninguna. */
fun parseSharedUrl(text: String): SharedMedia? {
    val url = URL.find(text)?.value ?: return null
    return youTubeId(url)?.let { SharedMedia(Kind.YOUTUBE, it) } ?: SharedMedia(Kind.OTHER, url)
}

private fun youTubeId(url: String): String? {
    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        return null
    }
    val host = uri.host?.removePrefix("www.")?.removePrefix("m.") ?: return null
    val segments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
    val id = when {
        host == "youtu.be" -> segments.firstOrNull()
        host != "youtube.com" -> null
        segments.firstOrNull() == "watch" ->
            uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
        segments.firstOrNull() in setOf("shorts", "embed", "live") -> segments.getOrNull(1)
        else -> null
    }
    return id?.takeIf(VIDEO_ID::matches)
}
