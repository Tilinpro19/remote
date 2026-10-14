package com.universalremote.domain.model

enum class IrDeviceCategory { TV, AIR_CONDITIONER, SET_TOP_BOX, PROJECTOR, AUDIO, OTHER }

/** Familias de codificación soportadas. Los nombres coinciden con la columna `protocol` de IRDB. */
enum class IrProtocol(vararg val aliases: String) {
    PRONTO("PRONTO", "RAW"),
    NEC("NEC", "NEC1", "NEC2"),

    /** NECx / Samsung32: cabecera 4,5 ms + 4,5 ms y dirección repetida. */
    NECX("NECX", "NECX1", "NECX2", "SAMSUNG", "SAMSUNG32"),
    SONY12("SONY12"),
    SONY15("SONY15"),
    SONY20("SONY20"),
    RC5("RC5"),
    RC6("RC6", "RC6-0-16");

    companion object {
        private val byAlias = entries.flatMap { p -> p.aliases.map { it to p } }.toMap()

        fun fromIrdbName(name: String): IrProtocol? = byAlias[name.trim().uppercase()]
    }
}

data class IrCodeset(
    val id: Long,
    val brand: String,
    val category: IrDeviceCategory,
    val label: String,
    val popularity: Int,
)

/**
 * Código IR de una función. Si [protocol] es [IrProtocol.PRONTO], [pronto] contiene la cadena;
 * en otro caso [device]/[subdevice]/[function] siguen la convención IRDB (`subdevice = -1` = sin subdispositivo).
 */
data class IrCode(
    val codesetId: Long,
    val functionName: String,
    val protocol: IrProtocol,
    val device: Int = -1,
    val subdevice: Int = -1,
    val function: Int = -1,
    val pronto: String? = null,
)
