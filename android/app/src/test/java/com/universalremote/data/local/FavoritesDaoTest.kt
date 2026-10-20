package com.universalremote.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoritesDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.favoriteDao()
        runTest {
            listOf("tv1", "tv2").forEach {
                db.deviceDao().upsert(
                    DeviceEntity(it, it, "ROKU", "LAN", null, null, null, null, null, null, null, null, null, null, null, 0),
                )
            }
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `lista los favoritos del dispositivo ordenados por posicion`() = runTest {
        dao.add(favorite("tv1", "NETFLIX"))
        dao.add(favorite("tv2", "YOUTUBE"))
        dao.add(favorite("tv1", "SPOTIFY"))

        assertEquals(listOf("NETFLIX", "SPOTIFY"), dao.observeForDevice("tv1").first().map { it.payload })
    }

    @Test
    fun `borrar quita solo ese favorito`() = runTest {
        dao.add(favorite("tv1", "NETFLIX"))
        dao.add(favorite("tv1", "SPOTIFY"))
        val netflix = dao.observeForDevice("tv1").first().first()

        dao.delete(netflix.id)

        assertEquals(listOf("SPOTIFY"), dao.observeForDevice("tv1").first().map { it.payload })
    }

    @Test
    fun `guardar dos veces el mismo favorito no lo duplica`() = runTest {
        dao.add(favorite("tv1", "NETFLIX"))
        dao.add(favorite("tv1", "NETFLIX"))

        assertEquals(1, dao.observeForDevice("tv1").first().size)
    }

    @Test
    fun `borrar el dispositivo borra sus favoritos`() = runTest {
        dao.add(favorite("tv1", "NETFLIX"))

        db.deviceDao().delete("tv1")

        assertEquals(emptyList<FavoriteEntity>(), dao.observeForDevice("tv1").first())
    }

    private fun favorite(deviceId: String, app: String) =
        FavoriteEntity(deviceId = deviceId, label = app, type = "APP", payload = app, position = 0)
}
