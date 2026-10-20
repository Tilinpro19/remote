package com.universalremote.data.repository

import com.universalremote.data.local.FavoriteDao
import com.universalremote.data.local.FavoriteEntity
import com.universalremote.domain.model.Favorite
import com.universalremote.domain.model.StreamingApp
import com.universalremote.domain.repository.FavoriteRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FavoriteRepositoryImpl @Inject constructor(
    private val dao: FavoriteDao,
) : FavoriteRepository {

    override fun observeForDevice(deviceId: String): Flow<List<Favorite>> =
        dao.observeForDevice(deviceId).map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun add(deviceId: String, label: String, target: Favorite.Target) {
        val (type, payload) = when (target) {
            is Favorite.Target.App -> TYPE_APP to target.app.name
            is Favorite.Target.Channel -> TYPE_CHANNEL to target.number
        }
        dao.add(FavoriteEntity(deviceId = deviceId, label = label, type = type, payload = payload, position = 0))
    }

    override suspend fun remove(id: Long) = dao.delete(id)
}

private const val TYPE_APP = "APP"
private const val TYPE_CHANNEL = "CHANNEL"

/** Apps que ya no existen en el enum (versión futura o eliminada) se ignoran. */
private fun FavoriteEntity.toDomainOrNull(): Favorite? {
    val target = when (type) {
        TYPE_APP -> StreamingApp.entries.firstOrNull { it.name == payload }?.let(Favorite.Target::App)
        TYPE_CHANNEL -> Favorite.Target.Channel(payload)
        else -> null
    } ?: return null
    return Favorite(id, label, target)
}
