package com.universalremote.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `migracion 1 a 2 conserva los dispositivos y crea la tabla de macros`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO devices (id, name, kind, transport_type, created_at) " +
                    "VALUES ('tv1', 'Salón', 'ROKU', 'LAN', 0)",
            )
        }

        // Valida el resultado contra schemas/…/2.json.
        helper.runMigrationsAndValidate(DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT name FROM devices WHERE id = 'tv1'").use { c ->
                c.moveToFirst()
                assertEquals("Salón", c.getString(0))
            }
            db.execSQL("INSERT INTO macros VALUES ('m1', 'tv1', 'Cine', '[]', 0)")
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
