package com.universalremote.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    /** `LAN`, `IR` o `BT`. */
    @ColumnInfo(name = "transport_type") val transportType: String,
    val host: String?,
    val port: Int?,
    @ColumnInfo(name = "ir_codeset_id") val irCodesetId: Long?,
    @ColumnInfo(name = "ir_bridge_url") val irBridgeUrl: String?,
    @ColumnInfo(name = "bt_address") val bluetoothAddress: String?,
    @ColumnInfo(name = "mac_address") val macAddress: String?,
    val model: String?,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    @ColumnInfo(name = "pairing_token") val pairingToken: String?,
    @ColumnInfo(name = "cert_sha256") val pinnedCertificateSha256: String?,
    @ColumnInfo(name = "last_connected_at") val lastConnectedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "ir_codesets",
    indices = [
        Index(value = ["category", "brand", "popularity"]),
        Index(value = ["source_key"], unique = true),
    ],
)
data class IrCodesetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val category: String,
    val label: String,
    val popularity: Int,
    /** Ruta de origen IRDB/LIRC (`Samsung/TV/7,7.csv`): evita duplicados en reimportaciones. */
    @ColumnInfo(name = "source_key") val sourceKey: String,
)

@Entity(
    tableName = "ir_codes",
    foreignKeys = [
        ForeignKey(
            entity = IrCodesetEntity::class,
            parentColumns = ["id"],
            childColumns = ["codeset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["codeset_id", "function_name"], unique = true)],
)
data class IrCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "codeset_id") val codesetId: Long,
    /** `wireName` de [com.universalremote.domain.model.RemoteCommand]. */
    @ColumnInfo(name = "function_name") val functionName: String,
    val protocol: String,
    val device: Int,
    val subdevice: Int,
    val function: Int,
    val pronto: String?,
)

data class BrandPopularityRow(
    val brand: String,
    val popularity: Int,
)

@Entity(
    tableName = "macros",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["device_id"])],
)
data class MacroEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val name: String,
    /** Pasos en JSON; ver [MacroStepsCodec]. */
    @ColumnInfo(name = "steps_json") val stepsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
