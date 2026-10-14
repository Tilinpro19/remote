package com.universalremote.adapters

import com.universalremote.adapters.ir.IrRemoteAdapter
import com.universalremote.adapters.roku.RokuEcpAdapter
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.adapter.RemoteAdapterFactory
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.RemoteError
import javax.inject.Inject
import javax.inject.Singleton

/** Registro tipo de dispositivo → adaptador. Los protocolos de próximas entregas se añaden aquí. */
@Singleton
class DefaultRemoteAdapterFactory @Inject constructor(
    private val irFactory: IrRemoteAdapter.Factory,
    private val rokuFactory: RokuEcpAdapter.Factory,
) : RemoteAdapterFactory {

    override fun create(profile: DeviceProfile): RemoteAdapter = when (profile.kind) {
        DeviceKind.INFRARED -> irFactory.create(profile)
        DeviceKind.ROKU -> rokuFactory.create(profile)
        DeviceKind.ANDROID_TV,
        DeviceKind.LG_WEBOS,
        DeviceKind.SAMSUNG_TIZEN,
        DeviceKind.BLUETOOTH_HID -> throw RemoteError.UnsupportedDevice(profile.kind.name)
    }
}
