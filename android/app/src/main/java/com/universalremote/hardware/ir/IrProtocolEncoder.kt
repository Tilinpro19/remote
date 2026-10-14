package com.universalremote.hardware.ir

import com.universalremote.domain.model.IrCode
import com.universalremote.domain.model.IrProtocol
import com.universalremote.domain.model.RemoteError

/**
 * Codificadores de protocolos IR de consumo a patrones en µs.
 * Parámetros según la convención IRDB (device / subdevice / function).
 */
object IrProtocolEncoder {

    /** Convierte un código del catálogo en señal. [toggle] sólo afecta a RC5/RC6. */
    fun encode(code: IrCode, toggle: Boolean = false): IrSignal = when (code.protocol) {
        IrProtocol.PRONTO -> ProntoHex.parse(
            code.pronto ?: throw RemoteError.InvalidCode("Código PRONTO sin cadena (${code.functionName})"),
        )
        IrProtocol.NEC -> nec(code.device, code.subdevice, code.function)
        IrProtocol.NECX -> necx(code.device, code.subdevice, code.function)
        IrProtocol.SONY12 -> sony(code.function, code.device, -1, deviceBits = 5)
        IrProtocol.SONY15 -> sony(code.function, code.device, -1, deviceBits = 8)
        IrProtocol.SONY20 -> sony(code.function, code.device, code.subdevice, deviceBits = 5)
        IrProtocol.RC5 -> rc5(code.device, code.function, toggle)
        IrProtocol.RC6 -> rc6(code.device, code.function, toggle)
    }

    // ---- NEC (38 kHz, LSB first, trama 108 ms) ----

    private const val NEC_FREQ = 38_000
    private const val NEC_UNIT = 564
    private const val NEC_FRAME = 108_000

    /** NEC estándar/extendido. `subdevice < 0` = complemento del dispositivo (NEC clásico). */
    fun nec(device: Int, subdevice: Int, function: Int): IrSignal {
        checkByte("device", device)
        checkByte("function", function)
        val sub = if (subdevice < 0) device.inv() and 0xFF else subdevice.also { checkByte("subdevice", it) }
        return pulseDistance(NEC_FREQ, headerMark = 16 * NEC_UNIT, headerSpace = 8 * NEC_UNIT, bytes = intArrayOf(device, sub, function, function.inv() and 0xFF))
    }

    /** NECx / Samsung32: cabecera 8+8 unidades; sin subdispositivo se repite el dispositivo. */
    fun necx(device: Int, subdevice: Int, function: Int): IrSignal {
        checkByte("device", device)
        checkByte("function", function)
        val sub = if (subdevice < 0) device else subdevice.also { checkByte("subdevice", it) }
        return pulseDistance(NEC_FREQ, headerMark = 8 * NEC_UNIT, headerSpace = 8 * NEC_UNIT, bytes = intArrayOf(device, sub, function, function.inv() and 0xFF))
    }

    private fun pulseDistance(frequency: Int, headerMark: Int, headerSpace: Int, bytes: IntArray): IrSignal {
        val b = PulseBuilder()
        b.mark(headerMark)
        b.space(headerSpace)
        for (byte in bytes) {
            for (bit in 0 until 8) {
                b.mark(NEC_UNIT)
                b.space(if ((byte shr bit) and 1 == 1) 3 * NEC_UNIT else NEC_UNIT)
            }
        }
        b.mark(NEC_UNIT)
        b.padTo(NEC_FRAME, 0)
        return IrSignal(frequency, b.build())
    }

    // ---- Sony SIRC (40 kHz, LSB first, periodo 45 ms, mínimo 3 tramas) ----

    private const val SONY_FREQ = 40_000
    private const val SONY_UNIT = 600
    private const val SONY_FRAME = 45_000
    private const val SONY_REPEATS = 3

    /**
     * @param deviceBits 5 (Sony12/Sony20) u 8 (Sony15).
     * @param extended byte extendido de Sony20; `-1` para Sony12/15.
     */
    fun sony(function: Int, device: Int, extended: Int, deviceBits: Int): IrSignal {
        if (function !in 0..127) invalid("Sony function fuera de rango: $function")
        if (device !in 0 until (1 shl deviceBits)) invalid("Sony device fuera de rango: $device")
        if (extended > 255) invalid("Sony extended fuera de rango: $extended")

        val b = PulseBuilder()
        repeat(SONY_REPEATS) {
            val frameStart = b.size
            b.mark(4 * SONY_UNIT)
            b.space(SONY_UNIT)
            fun bits(value: Int, count: Int) {
                for (bit in 0 until count) {
                    b.mark(if ((value shr bit) and 1 == 1) 2 * SONY_UNIT else SONY_UNIT)
                    b.space(SONY_UNIT)
                }
            }
            bits(function, 7)
            bits(device, deviceBits)
            if (extended >= 0) bits(extended, 8)
            b.padTo(SONY_FRAME, frameStart)
        }
        return IrSignal(SONY_FREQ, b.build())
    }

    // ---- Philips RC5 (36 kHz, Manchester 889 µs, MSB first) ----

    private const val RC_FREQ = 36_000
    private const val RC5_HALF = 889
    private const val RC5_FRAME = 113_778

    /** RC5 / RC5x: `function` 0..127 (el bit 6 se codifica invertido en el segundo bit de inicio). */
    fun rc5(device: Int, function: Int, toggle: Boolean): IrSignal {
        if (device !in 0..31) invalid("RC5 device fuera de rango: $device")
        if (function !in 0..127) invalid("RC5 function fuera de rango: $function")

        val bits = ArrayList<Boolean>(14)
        bits += true
        bits += (function and 0x40) == 0
        bits += toggle
        for (i in 4 downTo 0) bits += (device shr i) and 1 == 1
        for (i in 5 downTo 0) bits += (function shr i) and 1 == 1

        val b = PulseBuilder()
        // RC5: "1" = espacio→marca, "0" = marca→espacio.
        for (bit in bits) {
            if (bit) { b.space(RC5_HALF); b.mark(RC5_HALF) } else { b.mark(RC5_HALF); b.space(RC5_HALF) }
        }
        b.padTo(RC5_FRAME, 0)
        return IrSignal(RC_FREQ, b.build())
    }

    // ---- Philips RC6 modo 0 (36 kHz, Manchester 444 µs, MSB first) ----

    private const val RC6_UNIT = 444
    private const val RC6_FRAME = 106_667

    fun rc6(device: Int, function: Int, toggle: Boolean): IrSignal {
        checkByte("device", device)
        checkByte("function", function)

        val b = PulseBuilder()
        b.mark(6 * RC6_UNIT)
        b.space(2 * RC6_UNIT)
        // RC6: "1" = marca→espacio, "0" = espacio→marca.
        fun bit(value: Boolean, unit: Int = RC6_UNIT) {
            if (value) { b.mark(unit); b.space(unit) } else { b.space(unit); b.mark(unit) }
        }
        bit(true) // bit de inicio
        repeat(3) { bit(false) } // modo 0
        bit(toggle, 2 * RC6_UNIT) // trailer de doble ancho
        for (i in 7 downTo 0) bit((device shr i) and 1 == 1)
        for (i in 7 downTo 0) bit((function shr i) and 1 == 1)
        b.padTo(RC6_FRAME, 0)
        return IrSignal(RC_FREQ, b.build())
    }

    private fun checkByte(name: String, value: Int) {
        if (value !in 0..255) invalid("$name fuera de rango: $value")
    }

    private fun invalid(message: String): Nothing = throw RemoteError.InvalidCode(message)
}
