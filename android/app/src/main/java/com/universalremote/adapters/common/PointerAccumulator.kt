package com.universalremote.adapters.common

import kotlin.math.abs
import kotlin.math.sign

/**
 * Convierte deltas continuos del touchpad en pasos discretos de D-pad para protocolos sin puntero
 * (Roku ECP, IR). Bloquea el eje dominante para evitar zig-zag en gestos diagonales.
 * No es thread-safe: cada adaptador lo usa bajo su propio Mutex.
 */
class PointerAccumulator(
    private val stepThresholdPx: Float = 60f,
    private val maxStepsPerEvent: Int = 5,
) {
    enum class Step { UP, DOWN, LEFT, RIGHT }

    private var accX = 0f
    private var accY = 0f

    fun accumulate(dx: Float, dy: Float): List<Step> {
        accX += dx
        accY += dy
        val horizontal = abs(accX) >= abs(accY)
        val value = if (horizontal) accX else accY
        val count = (abs(value) / stepThresholdPx).toInt()
        if (count == 0) return emptyList()

        val step = when {
            horizontal && value > 0 -> Step.RIGHT
            horizontal -> Step.LEFT
            value > 0 -> Step.DOWN
            else -> Step.UP
        }
        // Se consume el eje dominante y se descarta el residuo del otro.
        if (horizontal) {
            accX -= value.sign * count * stepThresholdPx
            accY = 0f
        } else {
            accY -= value.sign * count * stepThresholdPx
            accX = 0f
        }
        return List(count.coerceAtMost(maxStepsPerEvent)) { step }
    }

    fun reset() {
        accX = 0f
        accY = 0f
    }
}
