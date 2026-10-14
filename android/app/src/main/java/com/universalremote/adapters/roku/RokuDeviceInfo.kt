package com.universalremote.adapters.roku

import com.universalremote.domain.model.MacAddress
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Subconjunto relevante de `GET /query/device-info`. */
data class RokuDeviceInfo(
    val name: String?,
    val model: String?,
    val serialNumber: String?,
    val macAddress: MacAddress?,
    val powerMode: String?,
    val isTv: Boolean,
    val supportsWakeOnWlan: Boolean,
) {
    val isDisplayOn: Boolean get() = powerMode.equals("PowerOn", ignoreCase = true)

    companion object {
        fun parse(xml: InputStream): RokuDeviceInfo {
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(xml, null)
            }
            val fields = HashMap<String, String>()
            var depth = 0
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        // Hijos directos de <device-info>: hojas con texto.
                        if (depth == 2) {
                            val tag = parser.name
                            fields[tag] = parser.nextText().trim()
                            depth--
                        }
                    }
                    XmlPullParser.END_TAG -> depth--
                }
            }
            fun field(name: String) = fields[name]?.ifEmpty { null }

            val ethernet = field("network-type").equals("ethernet", ignoreCase = true)
            val mac = (if (ethernet) field("ethernet-mac") else field("wifi-mac"))
                ?: field("wifi-mac") ?: field("ethernet-mac")

            return RokuDeviceInfo(
                name = field("user-device-name") ?: field("friendly-device-name") ?: field("default-device-name"),
                model = field("friendly-model-name") ?: field("model-name"),
                serialNumber = field("serial-number"),
                macAddress = mac?.let(MacAddress::parse),
                powerMode = field("power-mode"),
                isTv = field("is-tv").toBoolean(),
                supportsWakeOnWlan = field("supports-wake-on-wlan").toBoolean(),
            )
        }
    }
}
