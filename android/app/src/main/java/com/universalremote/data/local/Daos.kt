package com.universalremote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY last_connected_at DESC, created_at DESC")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun get(id: String): DeviceEntity?

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface IrCodeDao {
    @Query("SELECT COUNT(*) FROM ir_codesets")
    suspend fun codesetCount(): Int

    @Query(
        """
        SELECT brand, SUM(popularity) AS popularity FROM ir_codesets
        WHERE category = :category
        GROUP BY brand ORDER BY popularity DESC, brand ASC
        """,
    )
    suspend fun brands(category: String): List<BrandPopularityRow>

    @Query(
        """
        SELECT * FROM ir_codesets
        WHERE brand = :brand COLLATE NOCASE AND category = :category
        ORDER BY popularity DESC, id ASC
        """,
    )
    suspend fun codesets(brand: String, category: String): List<IrCodesetEntity>

    @Query("SELECT * FROM ir_codesets WHERE id = :id")
    suspend fun codeset(id: Long): IrCodesetEntity?

    @Query("SELECT * FROM ir_codes WHERE codeset_id = :codesetId AND function_name = :function LIMIT 1")
    suspend fun code(codesetId: Long, function: String): IrCodeEntity?

    @Query("SELECT function_name FROM ir_codes WHERE codeset_id = :codesetId")
    suspend fun functions(codesetId: Long): List<String>

    @Query("SELECT id FROM ir_codesets WHERE source_key = :sourceKey")
    suspend fun codesetIdBySource(sourceKey: String): Long?

    @Insert
    suspend fun insertCodeset(codeset: IrCodesetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCodes(codes: List<IrCodeEntity>)

    /** Inserta un codeset con sus códigos de forma atómica; no hace nada si ya existe. */
    @Transaction
    suspend fun importCodeset(codeset: IrCodesetEntity, codes: (Long) -> List<IrCodeEntity>) {
        if (codesetIdBySource(codeset.sourceKey) != null) return
        val id = insertCodeset(codeset)
        insertCodes(codes(id))
    }
}

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros WHERE device_id = :deviceId ORDER BY created_at ASC")
    fun observeForDevice(deviceId: String): Flow<List<MacroEntity>>

    @Upsert
    suspend fun upsert(macro: MacroEntity)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun delete(id: String)
}
