package com.universalremote.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.universalremote.domain.repository.SessionPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreSessionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionPreferences {

    override val activeDeviceId: Flow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[ACTIVE_DEVICE_ID] }
        .distinctUntilChanged()

    override suspend fun setActiveDeviceId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(ACTIVE_DEVICE_ID) else prefs[ACTIVE_DEVICE_ID] = id
        }
    }

    private companion object {
        val ACTIVE_DEVICE_ID = stringPreferencesKey("active_device_id")
    }
}
