package com.universalremote.domain.repository

import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.IrCode
import com.universalremote.domain.model.IrCodeset
import com.universalremote.domain.model.IrDeviceCategory
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun observeDevices(): Flow<List<DeviceProfile>>
    suspend fun get(id: String): DeviceProfile?
    suspend fun upsert(profile: DeviceProfile)
    suspend fun delete(id: String)
}

/** Preferencias de sesión (DataStore). */
interface SessionPreferences {
    val activeDeviceId: Flow<String?>
    suspend fun setActiveDeviceId(id: String?)
    val hapticsEnabled: Flow<Boolean>
    suspend fun setHapticsEnabled(enabled: Boolean)
}

data class BrandPopularity(val brand: String, val popularity: Int)

interface IrCodeRepository {
    /** Marcas con códigos para la categoría, ordenadas por popularidad agregada. */
    suspend fun brands(category: IrDeviceCategory): List<BrandPopularity>

    /** Candidatos del flujo de prueba por descarte, del más al menos popular. */
    suspend fun codesets(brand: String, category: IrDeviceCategory): List<IrCodeset>

    suspend fun codeset(id: Long): IrCodeset?

    /** @param function `wireName` del comando (`CMD_POWER`, `CMD_RAW_SLEEP`…). */
    suspend fun code(codesetId: Long, function: String): IrCode?

    suspend fun functions(codesetId: Long): Set<String>
}
