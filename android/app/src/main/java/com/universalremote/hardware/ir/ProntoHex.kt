package com.universalremote.hardware.ir

import com.universalremote.domain.model.RemoteError
import kotlin.math.roundToInt

/**
 * Conversor Pronto Hex → [IrSignal].
 *
 * Formatos soportados:
 * - `0000` señal aprendida modulada (duraciones en ciclos de portadora).
 * - `5000` RC5 y `5001` RC5x, `6000` RC6 modo 0, `900A` NEC: códigos predefinidos que se
 *   re-codifican con [IrProtocolEncoder].
 * - `0100` (no modulada) se rechaza: `ConsumerIrManager` siempre modula.
 */
object ProntoHex {
    /** Unidad de reloj del Pronto original en µs. */
    private const val PRONTO_CLOCK_MICROS = 0.241246
    private val WORD = Regex("^[0-9A-Fa-f]{4}$")

    /**
     * @param repeatCount veces que se añade la secuencia de repetición tras la secuencia única.
     *   Si la secuencia única está vacía, la de repetición se emite al menos una vez.
     * @throws RemoteError.InvalidCode si la cadena no es Pronto válido.
     */
    fun parse(pronto: String, repeatCount: Int = 0): IrSignal {
        require(repeatCount >= 0) { "repeatCount negativo" }
        val words = tokenize(pronto)
        if (words.size < 4) invalid("Pronto demasiado corto (${words.size} palabras)")

        return when (val type = words[0]) {
            0x0000 -> parseLearned(words, repeatCount)
            0x0100 -> invalid("Pronto 0100 (no modulado) no es transmisible por ConsumerIrManager")
            0x5000, 0x5001 -> parseRc5(words, extended = type == 0x5001)
            0x6000 -> parseRc6(words)
            0x900A -> parseNec(words)
            else -> invalid("Tipo Pronto no soportado: %04X".format(type))
        }
    }

    /** Frecuencia de portadora codificada en la palabra 1 de un Pronto. */
    fun frequencyFromWord(word: Int): Int {
        if (word <= 0) invalid("Divisor de frecuencia Pronto nulo")
        return (1_000_000.0 / (word * PRONTO_CLOCK_MICROS)).roundToInt()
    }

    private fun tokenize(pronto: String): IntArray {
        val tokens = pronto.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return IntArray(tokens.size) { i ->
            val token = tokens[i]
            if (!WORD.matches(token)) invalid("Palabra Pronto inválida en posición $i: '$token'")
            token.toInt(16)
        }
    }

    private fun parseLearned(words: IntArray, repeatCount: Int): IrSignal {
        val frequencyWord = words[1]
        val onceBursts = words[2]
        val repeatBursts = words[3]
        val expected = 4 + 2 * (onceBursts + repeatBursts)
        if (words.size != expected) {
            invalid("Longitud Pronto inconsistente: cabecera declara $expected palabras, hay ${words.size}")
        }
        if (onceBursts + repeatBursts == 0) invalid("Pronto sin pares de ráfagas")

        val frequency = frequencyFromWord(frequencyWord)
        val cycleMicros = frequencyWord * PRONTO_CLOCK_MICROS
        val toMicros = { cycles: Int -> (cycles * cycleMicros).roundToInt().coerceAtLeast(1) }

        val onceStart = 4
        val repeatStart = onceStart + 2 * onceBursts
        val repeats = if (onceBursts == 0) maxOf(1, repeatCount) else repeatCount

        val pattern = ArrayList<Int>(2 * (onceBursts + repeatBursts * repeats))
        for (i in onceStart until repeatStart) pattern += toMicros(words[i])
        repeat(if (repeatBursts > 0) repeats else 0) {
            for (i in repeatStart until repeatStart + 2 * repeatBursts) pattern += toMicros(words[i])
        }
        return signal(frequency, pattern.toIntArray())
    }

    /** `5000 FFFF 0000 0001 SSSS CCCC`: sistema y comando en las dos últimas palabras. */
    private fun parseRc5(words: IntArray, extended: Boolean): IrSignal {
        if (words.size < 6) invalid("Pronto RC5 incompleto")
        val system = words[4]
        val command = words[5]
        val maxCommand = if (extended) 127 else 63
        if (system !in 0..31 || command !in 0..maxCommand) invalid("Pronto RC5 fuera de rango: sistema=$system comando=$command")
        val encoded = IrProtocolEncoder.rc5(system, command, toggle = false)
        return withProntoFrequency(encoded, words[1])
    }

    /** `6000 FFFF 0000 0001 AAAA CCCC`: RC6 modo 0. */
    private fun parseRc6(words: IntArray): IrSignal {
        if (words.size < 6) invalid("Pronto RC6 incompleto")
        val address = words[4]
        val command = words[5]
        if (address !in 0..255 || command !in 0..255) invalid("Pronto RC6 fuera de rango")
        val encoded = IrProtocolEncoder.rc6(address, command, toggle = false)
        return withProntoFrequency(encoded, words[1])
    }

    /** `900A FFFF 0000 0001 DDSS CCcc`: dispositivo/subdispositivo y comando + complemento. */
    private fun parseNec(words: IntArray): IrSignal {
        if (words.size < 6) invalid("Pronto NEC incompleto")
        val device = words[4] ushr 8
        val subdevice = words[4] and 0xFF
        val command = words[5] ushr 8
        val check = words[5] and 0xFF
        if (check != command.inv() and 0xFF) invalid("Pronto NEC con complemento de comando inválido")
        val encoded = IrProtocolEncoder.nec(device, subdevice, command)
        return withProntoFrequency(encoded, words[1])
    }

    private fun withProntoFrequency(signal: IrSignal, frequencyWord: Int): IrSignal {
        val frequency = if (frequencyWord == 0) signal.frequency else frequencyFromWord(frequencyWord)
        return signal(frequency, signal.pattern)
    }

    private fun signal(frequency: Int, pattern: IntArray): IrSignal {
        if (frequency !in IrSignal.MIN_FREQUENCY..IrSignal.MAX_FREQUENCY) {
            invalid("Portadora Pronto fuera del rango soportado: $frequency Hz")
        }
        return IrSignal(frequency, pattern)
    }

    private fun invalid(message: String): Nothing = throw RemoteError.InvalidCode(message)
}
