package com.universalremote.data.irdb

import android.content.Context
import android.util.Log
import com.universalremote.core.di.IoDispatcher
import com.universalremote.data.local.IrCodeDao
import com.universalremote.data.local.IrCodeEntity
import com.universalremote.data.local.IrCodesetEntity
import com.universalremote.domain.model.IrDeviceCategory
import com.universalremote.domain.model.IrProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Fila parseada de un CSV IRDB (`functionname,protocol,device,subdevice,function`). */
data class IrdbRow(
    val functionName: String,
    val protocol: IrProtocol,
    val device: Int,
    val subdevice: Int,
    val function: Int,
    val pronto: String?,
)

/**
 * Importa catálogos con el formato de IRDB (github.com/probonopd/irdb) a Room.
 *
 * Estructura en assets:
 * - `irdb/index.csv`: `brand,category,file,popularity`
 * - `irdb/<Brand>/<Category>/<device>,<subdevice>.csv`: CSV IRDB. Se acepta además el protocolo
 *   `PRONTO` con la cadena Pronto Hex en la columna `function`.
 */
@Singleton
class IrdbCatalogImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: IrCodeDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun importBundledCatalog() = withContext(io) {
        val index = try {
            context.assets.open(INDEX).use { parseIndex(it) }
        } catch (e: IOException) {
            Log.w(TAG, "Catálogo IR empaquetado no encontrado ($INDEX)", e)
            return@withContext
        }
        for (entry in index) {
            try {
                context.assets.open("$ROOT/${entry.file}").use { stream ->
                    importCsv(entry.brand, entry.category, entry.label, entry.popularity, entry.file, stream)
                }
            } catch (e: IOException) {
                Log.w(TAG, "No se pudo importar ${entry.file}", e)
            }
        }
    }

    /** Importa un CSV IRDB arbitrario (asset, descarga o archivo del usuario). Idempotente por [sourceKey]. */
    suspend fun importCsv(
        brand: String,
        category: IrDeviceCategory,
        label: String,
        popularity: Int,
        sourceKey: String,
        stream: InputStream,
    ) {
        val rows = parseCsv(stream)
        if (rows.isEmpty()) return
        val codeset = IrCodesetEntity(
            brand = brand,
            category = category.name,
            label = label,
            popularity = popularity,
            sourceKey = sourceKey,
        )
        dao.importCodeset(codeset) { codesetId ->
            rows.mapNotNull { row ->
                val wireName = IrdbFunctionMapper.toWireName(row.functionName) ?: return@mapNotNull null
                IrCodeEntity(
                    codesetId = codesetId,
                    functionName = wireName,
                    protocol = row.protocol.name,
                    device = row.device,
                    subdevice = row.subdevice,
                    function = row.function,
                    pronto = row.pronto,
                )
            }.distinctBy { it.functionName } // IRDB repite funciones con alias; gana la primera.
        }
    }

    private data class IndexEntry(
        val brand: String,
        val category: IrDeviceCategory,
        val file: String,
        val popularity: Int,
    ) {
        val label: String get() = "$brand ${file.substringAfterLast('/').removeSuffix(".csv")}"
    }

    private fun parseIndex(stream: InputStream): List<IndexEntry> =
        stream.bufferedReader().readLines()
            .drop(1)
            .mapNotNull { line ->
                val cols = line.split(',').map { it.trim() }
                if (cols.size < 4) return@mapNotNull null
                // La ruta contiene una coma (`7,7.csv`): todo lo que hay entre categoría y popularidad.
                val brand = cols.first()
                val category = runCatching { IrDeviceCategory.valueOf(cols[1]) }.getOrNull() ?: return@mapNotNull null
                val popularity = cols.last().toIntOrNull() ?: return@mapNotNull null
                val file = cols.subList(2, cols.size - 1).joinToString(",")
                IndexEntry(brand, category, file, popularity)
            }

    companion object {
        private const val TAG = "IrdbImporter"
        private const val ROOT = "irdb"
        private const val INDEX = "$ROOT/index.csv"

        fun parseCsv(stream: InputStream): List<IrdbRow> =
            stream.bufferedReader().useLines { lines ->
                lines.drop(1).mapNotNull(::parseRow).toList()
            }

        internal fun parseRow(line: String): IrdbRow? {
            if (line.isBlank()) return null
            val cols = line.split(',').map { it.trim() }
            if (cols.size < 5) return null
            val protocol = IrProtocol.fromIrdbName(cols[1]) ?: return null
            if (protocol == IrProtocol.PRONTO) {
                return IrdbRow(cols[0], protocol, -1, -1, -1, cols.subList(4, cols.size).joinToString(","))
            }
            val device = cols[2].toIntOrNull() ?: return null
            val subdevice = cols[3].toIntOrNull() ?: -1
            val function = cols[4].toIntOrNull() ?: return null
            return IrdbRow(cols[0], protocol, device, subdevice, function, null)
        }
    }
}
