package com.universalremote.domain.model

/** Dirección MAC normalizada a `AA:BB:CC:DD:EE:FF`. */
@JvmInline
value class MacAddress private constructor(val value: String) {

    fun toBytes(): ByteArray = ByteArray(OCTETS) { i ->
        value.substring(i * 3, i * 3 + 2).toInt(16).toByte()
    }

    override fun toString(): String = value

    companion object {
        private const val OCTETS = 6
        private val SEPARATORS = Regex("[:\\-.\\s]")
        private val HEX12 = Regex("^[0-9A-F]{12}$")

        /** Acepta `aa:bb:cc:dd:ee:ff`, `AA-BB-…`, `aabb.ccdd.eeff` y `aabbccddeeff`. */
        fun parse(raw: String): MacAddress? {
            val hex = raw.trim().uppercase().replace(SEPARATORS, "")
            if (!HEX12.matches(hex)) return null
            if (hex == "000000000000" || hex == "FFFFFFFFFFFF") return null
            return MacAddress(hex.chunked(2).joinToString(":"))
        }
    }
}
