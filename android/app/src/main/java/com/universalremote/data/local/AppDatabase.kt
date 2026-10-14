package com.universalremote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DeviceEntity::class, IrCodesetEntity::class, IrCodeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun irCodeDao(): IrCodeDao

    companion object {
        const val NAME = "remote.db"
    }
}
