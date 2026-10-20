package com.universalremote.data.repository

import com.universalremote.data.local.MacroDao
import com.universalremote.data.local.MacroEntity
import com.universalremote.data.local.MacroStepsCodec
import com.universalremote.domain.model.Macro
import com.universalremote.domain.repository.MacroRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MacroRepositoryImpl @Inject constructor(
    private val dao: MacroDao,
) : MacroRepository {

    override fun observeForDevice(deviceId: String): Flow<List<Macro>> =
        dao.observeForDevice(deviceId).map { rows ->
            rows.map { Macro(it.id, it.name, it.deviceId, MacroStepsCodec.decode(it.stepsJson)) }
        }

    override suspend fun save(macro: Macro) = dao.upsert(
        MacroEntity(macro.id, macro.deviceId, macro.name, MacroStepsCodec.encode(macro.steps), System.currentTimeMillis()),
    )

    override suspend fun delete(id: String) = dao.delete(id)
}
