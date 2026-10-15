package com.universalremote.adapters

import com.universalremote.adapters.androidtv.AndroidTvAdapter
import com.universalremote.adapters.ir.IrRemoteAdapter
import com.universalremote.adapters.roku.RokuEcpAdapter
import com.universalremote.adapters.tizen.TizenAdapter
import com.universalremote.adapters.webos.WebOsAdapter
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
    private val androidTvFactory: AndroidTvAdapter.Factory,
    private val webOsFactory: WebOsAdapter.Factory,
    private val tizenFactory: TizenAdapter.Factory,
) : RemoteAdapterFactory {

    override fun create(profile: DeviceProfile): RemoteAdapter = when (profile.kind) {
        DeviceKind.INFRARED -> irFactory.create(profile)
        DeviceKind.ROKU -> rokuFactory.create(profile)
        DeviceKind.ANDROID_TV -> androidTvFactory.create(profile)
        DeviceKind.LG_WEBOS -> webOsFactory.create(profile)
        DeviceKind.SAMSUNG_TIZEN -> tizenFactory.create(profile)
        DeviceKind.BLUETOOTH_HID -> throw RemoteError.UnsupportedDevice(profile.kind.name)
    }
}
