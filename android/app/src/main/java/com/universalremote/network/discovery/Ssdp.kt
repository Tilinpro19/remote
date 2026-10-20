package com.universalremote.network.discovery

import com.universalremote.domain.model.DeviceKind
import java.net.URI

/** Respuesta SSDP (`HTTP/1.1 200 OK`) o anuncio `NOTIFY` parseado. */
data class SsdpResponse(
    val sourceHost: String,
    val location: String?,
    val searchTarget: String?,
    val usn: String?,
    val server: String?,
) {
    /** Host anunciado en LOCATION; si falta, el origen del datagrama. */
    val host: String
        get() = location?.let { runCatching { URI(it).host }.getOrNull() } ?: sourceHost

    val locationPort: Int?
        get() = location?.let { runCatching { URI(it).port }.getOrNull() }?.takeIf { it > 0 }

    fun classify(): DeviceKind? {
        val st = searchTarget.orEmpty().lowercase()
        val srv = server.orEmpty().lowercase()
        val id = usn.orEmpty().lowercase()
        return when {
            st == "roku:ecp" || id.contains("roku:ecp") || srv.contains("roku") -> DeviceKind.ROKU
            st.contains("lge-com") || srv.contains("webos") -> DeviceKind.LG_WEBOS
            srv.contains("vizio") || id.contains("vizio") -> DeviceKind.VIZIO
            st.contains("sony-com") || srv.contains("bravia") -> DeviceKind.SONY_BRAVIA
            st.contains("samsung.com") || srv.contains("samsung") || srv.contains("tizen") -> DeviceKind.SAMSUNG_TIZEN
            else -> null
        }
    }
}

object Ssdp {
    const val MULTICAST_ADDRESS = "239.255.255.250"
    const val PORT = 1900

    /** Targets específicos: responden menos equipos irrelevantes que `ssdp:all`. */
    val SEARCH_TARGETS = listOf(
        "roku:ecp",
        "urn:lge-com:service:webos-second-screen:1",
        "urn:samsung.com:device:RemoteControlReceiver:1",
        "urn:schemas-sony-com:service:ScalarWebAPI:1",
        "urn:dial-multiscreen-org:service:dial:1",
    )

    fun searchRequest(searchTarget: String, mxSeconds: Int = 2): ByteArray = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $MULTICAST_ADDRESS:$PORT\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: $mxSeconds\r\n")
        append("ST: $searchTarget\r\n")
        append("USER-AGENT: Android/UPnP/1.1 UniversalRemote/1.0\r\n")
        append("\r\n")
    }.toByteArray(Charsets.US_ASCII)

    /** Devuelve `null` si el datagrama no es una respuesta ni un NOTIFY `ssdp:alive`. */
    fun parse(datagram: String, sourceHost: String): SsdpResponse? {
        val lines = datagram.split("\r\n", "\n")
        val status = lines.firstOrNull()?.trim()?.uppercase() ?: return null
        val isResponse = status.startsWith("HTTP/1.1 200") || status.startsWith("HTTP/1.0 200")
        val isNotify = status.startsWith("NOTIFY")
        if (!isResponse && !isNotify) return null

        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            headers[line.substring(0, idx).trim().uppercase()] = line.substring(idx + 1).trim()
        }
        if (isNotify && headers["NTS"]?.equals("ssdp:alive", ignoreCase = true) != true) return null

        return SsdpResponse(
            sourceHost = sourceHost,
            location = headers["LOCATION"],
            searchTarget = headers["ST"] ?: headers["NT"],
            usn = headers["USN"],
            server = headers["SERVER"],
        )
    }
}
