package com.universalremote.hardware.ir

/**
 * Señal lista para `ConsumerIrManager.transmit`.
 *
 * @param frequency portadora en Hz.
 * @param pattern duraciones en µs alternando marca/espacio, empezando por marca.
 */
class IrSignal(val frequency: Int, val pattern: IntArray) {
    init {
        require(frequency in MIN_FREQUENCY..MAX_FREQUENCY) { "Portadora fuera de rango: $frequency Hz" }
        require(pattern.isNotEmpty()) { "Patrón vacío" }
        require(pattern.all { it > 0 }) { "El patrón contiene duraciones no positivas" }
    }

    val totalDurationMicros: Long get() = pattern.fold(0L) { acc, d -> acc + d }

    override fun equals(other: Any?): Boolean =
        other is IrSignal && other.frequency == frequency && other.pattern.contentEquals(pattern)

    override fun hashCode(): Int = 31 * frequency + pattern.contentHashCode()

    override fun toString(): String = "IrSignal(frequency=$frequency, pattern=${pattern.size} pulses, ${totalDurationMicros}µs)"

    companion object {
        const val MIN_FREQUENCY = 30_000
        const val MAX_FREQUENCY = 60_000
    }
}

/**
 * Construye patrones marca/espacio fusionando niveles consecutivos iguales, necesario en
 * codificaciones Manchester (RC5/RC6) donde dos medios bits contiguos comparten nivel.
 */
internal class PulseBuilder {
    private val durations = ArrayList<Int>(128)
    private var lastIsMark = false

    fun mark(micros: Int) = add(true, micros)
    fun space(micros: Int) = add(false, micros)

    private fun add(isMark: Boolean, micros: Int) {
        if (micros <= 0) return
        if (durations.isEmpty()) {
            // El patrón de ConsumerIrManager siempre empieza en marca; un espacio inicial es silencio.
            if (!isMark) return
            durations += micros
        } else if (isMark == lastIsMark) {
            durations[durations.lastIndex] += micros
        } else {
            durations += micros
        }
        lastIsMark = isMark
    }

    /** Rellena con espacio hasta que la trama dure [frameMicros] (periodo de repetición del protocolo). */
    fun padTo(frameMicros: Int, frameStartIndex: Int) {
        val elapsed = durations.subList(frameStartIndex, durations.size).sum()
        space((frameMicros - elapsed).coerceAtLeast(MIN_GAP))
    }

    val size: Int get() = durations.size

    fun build(): IntArray = durations.toIntArray()

    private companion object {
        const val MIN_GAP = 10_000
    }
}
