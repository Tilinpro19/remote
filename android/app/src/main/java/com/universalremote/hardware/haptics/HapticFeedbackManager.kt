package com.universalremote.hardware.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.universalremote.domain.model.HapticWeight
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Háptica nativa acoplada al envío de comandos. Llamable desde el hilo principal (no bloquea). */
@Singleton
class HapticFeedbackManager @Inject constructor(@ApplicationContext context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    @Volatile
    var enabled: Boolean = true

    fun perform(weight: HapticWeight) {
        if (!enabled || weight == HapticWeight.NONE) return
        val effect = when (weight) {
            HapticWeight.HEAVY -> predefined(VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 30, amplitude = 255)
            HapticWeight.MEDIUM -> predefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 20, amplitude = 180)
            HapticWeight.LIGHT, HapticWeight.SELECTION ->
                predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 10, amplitude = 100)
            HapticWeight.NONE -> return
        }
        vibrate(effect)
    }

    /** Patrón de error (doble pulso) cuando un comando no llega al equipo. */
    fun performError() {
        if (!enabled) return
        vibrate(predefined(VibrationEffect.EFFECT_DOUBLE_CLICK, fallbackMs = 40, amplitude = 255))
    }

    private fun predefined(effectId: Int, fallbackMs: Long, amplitude: Int): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(effectId)
        } else {
            VibrationEffect.createOneShot(fallbackMs, amplitude)
        }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
            // Sin permiso VIBRATE (p. ej. retirado por política MDM): la háptica es opcional.
            enabled = false
        }
    }
}
