package com.universalremote.data.repository

import com.universalremote.data.local.DeviceDao
import com.universalremote.data.local.DeviceEntity
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.MacAddress
import com.universalremote.domain.repository.DeviceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val dao: DeviceDao,
) : DeviceRepository {

    override fun observeDevices(): Flow<List<DeviceProfile>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun get(id: String): DeviceProfile? = dao.get(id)?.toDomainOrNull()

    override suspend fun upsert(profile: DeviceProfile) {
        val createdAt = dao.get(profile.id)?.createdAt ?: System.currentTimeMillis()
        dao.upsert(profile.toEntity(createdAt))
    }

    override suspend fun delete(id: String) = dao.delete(id)
}

private const val TRANSPORT_LAN = "LAN"
private const val TRANSPORT_IR = "IR"
private const val TRANSPORT_BT = "BT"

/** Filas corruptas o de versiones futuras se ignoran en lugar de tumbar la lista completa. */
private fun DeviceEntity.toDomainOrNull(): DeviceProfile? {
    val kind = runCatching { DeviceKind.valueOf(kind) }.getOrNull() ?: return null
    val transport = when (transportType) {
        TRANSPORT_LAN -> DeviceTransport.Lan(host ?: return null, port ?: kind.defaultControlPort ?: return null)
        TRANSPORT_IR -> DeviceTransport.Infrared(irCodesetId ?: return null, irBridgeUrl)
        TRANSPORT_BT -> DeviceTransport.Bluetooth(bluetoothAddress ?: return null)
        else -> return null
    }
    return DeviceProfile(
        id = id,
        name = name,
        kind = kind,
        transport = transport,
        macAddress = macAddress?.let(MacAddress::parse),
        model = model,
        serialNumber = serialNumber,
        pairingToken = pairingToken,
        pinnedCertificateSha256 = pinnedCertificateSha256,
        lastConnectedAt = lastConnectedAt,
    )
}

private fun DeviceProfile.toEntity(createdAt: Long): DeviceEntity {
    val lan = transport as? DeviceTransport.Lan
    val ir = transport as? DeviceTransport.Infrared
    val bt = transport as? DeviceTransport.Bluetooth
    return DeviceEntity(
        id = id,
        name = name,
        kind = kind.name,
        transportType = when (transport) {
            is DeviceTransport.Lan -> TRANSPORT_LAN
            is DeviceTransport.Infrared -> TRANSPORT_IR
            is DeviceTransport.Bluetooth -> TRANSPORT_BT
        },
        host = lan?.host,
        port = lan?.port,
        irCodesetId = ir?.codesetId,
        irBridgeUrl = ir?.bridgeUrl,
        bluetoothAddress = bt?.address,
        macAddress = macAddress?.value,
        model = model,
        serialNumber = serialNumber,
        pairingToken = pairingToken,
        pinnedCertificateSha256 = pinnedCertificateSha256,
        lastConnectedAt = lastConnectedAt,
        createdAt = createdAt,
    )
}
