package com.universalremote.hardware.ir

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import com.universalremote.core.di.IoDispatcher
import com.universalremote.domain.model.RemoteError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Destino físico de una señal IR: emisor del teléfono o hub externo en la LAN. */
interface IrTransmitter {
    val isAvailable: Boolean
    suspend fun transmit(signal: IrSignal)
}

/**
 * Envoltorio de [ConsumerIrManager]. `transmit` bloquea durante toda la señal, por eso se ejecuta
 * en [IoDispatcher] y serializado con un [Mutex]: dos señales solapadas corromperían ambas.
 */
@Singleton
class IrHardwareManager @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : IrTransmitter {
    private val irManager: ConsumerIrManager? = context.getSystemService(ConsumerIrManager::class.java)
    private val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)
    private val mutex = Mutex()

    /** Rangos de portadora del emisor; `null` si el HAL no los publica. */
    val carrierFrequencies: List<IntRange>? by lazy {
        runCatching { irManager?.carrierFrequencies }
            .getOrNull()
            ?.map { it.minFrequency..it.maxFrequency }
    }

    override val isAvailable: Boolean
        get() = hasIrEmitter()

    fun hasIrEmitter(): Boolean = hasFeature && runCatching { irManager?.hasIrEmitter() == true }.getOrDefault(false)

    override suspend fun transmit(signal: IrSignal) {
        val manager = irManager?.takeIf { hasIrEmitter() }
            ?: throw RemoteError.HardwareUnavailable("Este dispositivo no tiene emisor de infrarrojos")

        carrierFrequencies?.takeIf { it.isNotEmpty() }?.let { ranges ->
            if (ranges.none { signal.frequency in it }) {
                throw RemoteError.HardwareUnavailable(
                    "El emisor no soporta ${signal.frequency} Hz (rangos: ${ranges.joinToString()})",
                )
            }
        }
        if (signal.totalDurationMicros > MAX_PATTERN_MICROS) {
            throw RemoteError.InvalidCode("Señal de ${signal.totalDurationMicros} µs excede el límite de ${MAX_PATTERN_MICROS} µs")
        }

        mutex.withLock {
            withContext(io) {
                try {
                    manager.transmit(signal.frequency, signal.pattern)
                } catch (e: SecurityException) {
                    throw RemoteError.PermissionDenied(android.Manifest.permission.TRANSMIT_IR, e)
                } catch (e: IllegalArgumentException) {
                    throw RemoteError.InvalidCode("Patrón IR rechazado por el HAL: ${e.message}")
                }
            }
        }
    }

    private companion object {
        /** Varios HAL rechazan patrones de más de 2 s. */
        const val MAX_PATTERN_MICROS = 2_000_000L
    }
}
