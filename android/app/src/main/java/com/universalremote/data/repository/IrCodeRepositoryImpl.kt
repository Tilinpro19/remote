package com.universalremote.data.repository

import com.universalremote.data.irdb.IrdbCatalogImporter
import com.universalremote.data.local.IrCodeDao
import com.universalremote.data.local.IrCodeEntity
import com.universalremote.data.local.IrCodesetEntity
import com.universalremote.domain.model.IrCode
import com.universalremote.domain.model.IrCodeset
import com.universalremote.domain.model.IrDeviceCategory
import com.universalremote.domain.model.IrProtocol
import com.universalremote.domain.repository.BrandPopularity
import com.universalremote.domain.repository.IrCodeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class IrCodeRepositoryImpl @Inject constructor(
    private val dao: IrCodeDao,
    private val importer: IrdbCatalogImporter,
) : IrCodeRepository {

    private val seedMutex = Mutex()

    @Volatile
    private var seeded = false

    override suspend fun brands(category: IrDeviceCategory): List<BrandPopularity> {
        ensureSeeded()
        return dao.brands(category.name).map { BrandPopularity(it.brand, it.popularity) }
    }

    override suspend fun codesets(brand: String, category: IrDeviceCategory): List<IrCodeset> {
        ensureSeeded()
        return dao.codesets(brand, category.name).mapNotNull { it.toDomainOrNull() }
    }

    override suspend fun codeset(id: Long): IrCodeset? = dao.codeset(id)?.toDomainOrNull()

    override suspend fun code(codesetId: Long, function: String): IrCode? =
        dao.code(codesetId, function)?.toDomainOrNull()

    override suspend fun functions(codesetId: Long): Set<String> = dao.functions(codesetId).toSet()

    /** Importa el catálogo empaquetado en assets la primera vez que se consulta. */
    private suspend fun ensureSeeded() {
        if (seeded) return
        seedMutex.withLock {
            if (seeded) return
            if (dao.codesetCount() == 0) importer.importBundledCatalog()
            seeded = true
        }
    }
}

private fun IrCodesetEntity.toDomainOrNull(): IrCodeset? {
    val category = runCatching { IrDeviceCategory.valueOf(category) }.getOrNull() ?: return null
    return IrCodeset(id = id, brand = brand, category = category, label = label, popularity = popularity)
}

private fun IrCodeEntity.toDomainOrNull(): IrCode? {
    val protocol = IrProtocol.fromIrdbName(protocol) ?: return null
    return IrCode(
        codesetId = codesetId,
        functionName = functionName,
        protocol = protocol,
        device = device,
        subdevice = subdevice,
        function = function,
        pronto = pronto,
    )
}
