package com.universalremote.domain.model

/** Equipo encontrado en la LAN por SSDP o mDNS, todavía no emparejado. */
data class DiscoveredDevice(
    val host: String,
    val name: String,
    val kind: DeviceKind?,
    val port: Int?,
    val model: String? = null,
    /** Identificador estable anunciado (USN SSDP o nombre de servicio mDNS). */
    val serviceIds: Set<String> = emptySet(),
    val sources: Set<Source> = emptySet(),
) {
    enum class Source { SSDP, MDNS }

    /** Identidad para deduplicar: un host físico puede anunciarse por varios canales. */
    val key: String get() = host
}
