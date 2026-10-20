package com.universalremote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DeviceEntity::class, IrCodesetEntity::class, IrCodeEntity::class, MacroEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun irCodeDao(): IrCodeDao
    abstract fun macroDao(): MacroDao

    companion object {
        const val NAME = "remote.db"

        /** v2: tabla de macros. El SQL debe coincidir con `schemas/…/2.json`. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `macros` (`id` TEXT NOT NULL, `device_id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `steps_json` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`device_id`) REFERENCES `devices`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_macros_device_id` ON `macros` (`device_id`)")
            }
        }
    }
}
