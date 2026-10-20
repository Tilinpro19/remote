package com.universalremote.domain.model

import java.util.UUID

enum class DeviceKind(
    /** Puerto de control por defecto para equipos IP. */
    val defaultControlPort: Int?,
) {
    ROKU(8060),

    /** Android TV Remote v2: 6466 = canal de comandos, 6467 = emparejamiento. */
    ANDROID_TV(6466),

    /** SSAP sobre WSS (3001); 3000 sólo en firmwares antiguos sin TLS. */
    LG_WEBOS(3001),
    SAMSUNG_TIZEN(8002),

    /** IRCC-IP (SOAP) + API REST en HTTP 80, autenticación por Pre-Shared Key. */
    SONY_BRAVIA(80),
    INFRARED(null),
    BLUETOOTH_HID(null),
}

/** Cómo se alcanza físicamente el equipo. */
sealed interface DeviceTransport {
    data class Lan(val host: String, val port: Int) : DeviceTransport {
        init {
            require(host.isNotBlank()) { "Host vacío" }
            require(port in 1..65_535) { "Puerto inválido: $port" }
        }
    }

    /**
     * @param codesetId fila de `ir_codesets` validada en el flujo de prueba por descarte.
     * @param bridgeUrl hub IR externo (HTTP) para teléfonos sin `ConsumerIrManager`; `null` = emisor local.
     */
    data class Infrared(val codesetId: Long, val bridgeUrl: String? = null) : DeviceTransport

    data class Bluetooth(val address: String) : DeviceTransport
}

/** Perfil persistido de un equipo emparejado. */
data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: DeviceKind,
    val transport: DeviceTransport,
    /** Se aprende en el primer emparejamiento para poder enviar Wake-on-LAN. */
    val macAddress: MacAddress? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    /** Token de emparejamiento (client-key de webOS, token de Tizen, certificado de cliente ATV). */
    val pairingToken: String? = null,
    /** SHA-256 (hex) del certificado hoja aceptado en el primer emparejamiento TLS (TOFU). */
    val pinnedCertificateSha256: String? = null,
    val lastConnectedAt: Long? = null,
) {
    val lanHost: String? get() = (transport as? DeviceTransport.Lan)?.host
}
