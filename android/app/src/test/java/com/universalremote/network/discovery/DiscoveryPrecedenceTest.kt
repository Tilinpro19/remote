package com.universalremote.network.discovery

import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DiscoveredDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryPrecedenceTest {

    private fun device(kind: DeviceKind?) =
        DiscoveredDevice(host = "192.168.1.20", name = "192.168.1.20", kind = kind, port = kind?.defaultControlPort)

    @Test
    fun `Android TV gana a JointSpace en cualquier orden`() {
        val atv = device(DeviceKind.ANDROID_TV)
        val philips = device(DeviceKind.PHILIPS_JOINTSPACE)

        assertEquals(DeviceKind.ANDROID_TV, philips.mergeWith(atv).kind)
        assertEquals(DeviceKind.ANDROID_TV, atv.mergeWith(philips).kind)
        assertEquals(6466, philips.mergeWith(atv).port)
    }

    @Test
    fun `Android TV gana a Sony IRCC`() {
        val merged = device(DeviceKind.SONY_BRAVIA).mergeWith(device(DeviceKind.ANDROID_TV))
        assertEquals(DeviceKind.ANDROID_TV, merged.kind)
    }

    @Test
    fun `respuesta SSDP de Philips se clasifica como JointSpace`() {
        val response = SsdpResponse("10.0.0.5", "http://10.0.0.5:49153/nmrDescription.xml", "upnp:rootdevice", "uuid:1", "Linux/4.9 UPnP/1.0 Philips-TV/1.0")
        assertEquals(DeviceKind.PHILIPS_JOINTSPACE, response.classify())
    }
}
