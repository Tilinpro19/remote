package com.universalremote

import com.universalremote.adapters.common.PointerAccumulator
import com.universalremote.adapters.roku.RokuKey
import com.universalremote.data.irdb.IrdbCatalogImporter
import com.universalremote.data.irdb.IrdbFunctionMapper
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.IrProtocol
import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.MacAddress
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp
import com.universalremote.domain.model.DiscoveredDevice
import com.universalremote.network.discovery.Ssdp
import com.universalremote.network.discovery.mergeWith
import com.universalremote.presentation.PairingHint
import com.universalremote.presentation.manualAddKinds
import com.universalremote.presentation.pairingHint
import com.universalremote.network.wol.WakeOnLanSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandTest {
    @Test
    fun `wire names round-trip`() {
        listOf(
            RemoteCommand.Power, RemoteCommand.VolumeUp, RemoteCommand.DpadCenter, RemoteCommand.Digit(7),
            RemoteCommand.Raw("InputHDMI1"), RemoteCommand.ColorKey(KeyColor.BLUE),
            RemoteCommand.LaunchApp(StreamingApp.DISNEY_PLUS),
        ).forEach { assertEquals(it, RemoteCommand.fromWireName(it.wireName)) }
        assertEquals(RemoteCommand.TouchDelta(3f, -2f), RemoteCommand.fromWireName("CMD_TOUCH_DELTA", 3f, -2f))
        assertNull(RemoteCommand.fromWireName("CMD_DIGIT_12"))
        assertNull(RemoteCommand.fromWireName("CMD_TEXT", text = ""))
        assertNull(RemoteCommand.fromWireName("CMD_APP_NOPE"))
    }

    @Test
    fun `color keys and apps are not sent as raw Roku keypresses`() {
        assertNull(RokuKey.of(RemoteCommand.ColorKey(KeyColor.RED)))
        assertNull(RokuKey.of(RemoteCommand.LaunchApp(StreamingApp.NETFLIX)))
    }
}

class MacAndWolTest {
    @Test
    fun `mac parsing normalizes separators`() {
        assertEquals("AA:BB:CC:DD:EE:FF", MacAddress.parse("aa-bb-cc-dd-ee-ff")?.value)
        assertEquals("AA:BB:CC:DD:EE:FF", MacAddress.parse("aabb.ccdd.eeff")?.value)
        assertNull(MacAddress.parse("00:00:00:00:00:00"))
        assertNull(MacAddress.parse("zz:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `magic packet is 6 x FF plus 16 x MAC`() {
        val mac = MacAddress.parse("01:23:45:67:89:AB")!!
        val packet = WakeOnLanSender.magicPacket(mac)
        assertEquals(102, packet.size)
        assertTrue(packet.take(6).all { it == 0xFF.toByte() })
        for (i in 0 until 16) {
            assertEquals(mac.toBytes().toList(), packet.copyOfRange(6 + i * 6, 12 + i * 6).toList())
        }
    }

    @Test
    fun `directed broadcast from prefix`() {
        val ip = byteArrayOf(192.toByte(), 168.toByte(), 1, 37)
        assertEquals("192.168.1.255", WakeOnLanSender.broadcastFor(ip, 24).hostAddress)
        assertEquals("192.168.1.63", WakeOnLanSender.broadcastFor(ip, 26).hostAddress)
    }
}

class SsdpTest {
    @Test
    fun `roku response is classified with location host and port`() {
        val datagram = "HTTP/1.1 200 OK\r\nCache-Control: max-age=3600\r\nST: roku:ecp\r\n" +
            "Location: http://192.168.1.50:8060/\r\nUSN: uuid:roku:ecp:X00000ABC\r\n\r\n"
        val response = Ssdp.parse(datagram, "192.168.1.50")!!
        assertEquals(DeviceKind.ROKU, response.classify())
        assertEquals("192.168.1.50", response.host)
        assertEquals(8060, response.locationPort)
    }

    @Test
    fun `notify byebye is ignored`() {
        val datagram = "NOTIFY * HTTP/1.1\r\nNT: roku:ecp\r\nNTS: ssdp:byebye\r\n\r\n"
        assertNull(Ssdp.parse(datagram, "10.0.0.2"))
    }

    @Test
    fun `m-search request is well formed`() {
        val text = String(Ssdp.searchRequest("roku:ecp"), Charsets.US_ASCII)
        assertTrue(text.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(text.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(text.endsWith("\r\n\r\n"))
    }
}

class IrdbTest {
    @Test
    fun `irdb names map to wire names`() {
        assertEquals("CMD_VOL_UP", IrdbFunctionMapper.toWireName("VOLUME +"))
        assertEquals("CMD_POWER", IrdbFunctionMapper.toWireName("Power"))
        assertEquals("CMD_DIGIT_5", IrdbFunctionMapper.toWireName("5"))
        assertEquals("CMD_DPAD_UP", IrdbFunctionMapper.toWireName("KEY_UP"))
        assertEquals("CMD_RAW_PIP_SWAP", IrdbFunctionMapper.toWireName("PiP Swap"))
    }

    @Test
    fun `csv rows parse protocol and subdevice`() {
        val row = IrdbCatalogImporter.parseRow("POWER,NECx2,7,7,2")!!
        assertEquals(IrProtocol.NECX, row.protocol)
        assertEquals(7, row.subdevice)
        assertEquals(-1, IrdbCatalogImporter.parseRow("POWER,Sony12,1,-1,21")!!.subdevice)
        assertNull(IrdbCatalogImporter.parseRow("POWER,UnknownProto,1,-1,21"))
    }
}

class PointerAndRokuTest {
    @Test
    fun `pointer emits steps on dominant axis and keeps remainder`() {
        val acc = PointerAccumulator(stepThresholdPx = 50f)
        assertEquals(emptyList<PointerAccumulator.Step>(), acc.accumulate(30f, 5f))
        assertEquals(listOf(PointerAccumulator.Step.RIGHT), acc.accumulate(30f, 0f))
        assertEquals(listOf(PointerAccumulator.Step.UP, PointerAccumulator.Step.UP), acc.accumulate(0f, -100f))
    }

    @Test
    fun `roku literal keys are percent encoded`() {
        assertEquals("Lit_a", RokuKey.literal('a'.code).path)
        assertEquals("Lit_%C3%B1", RokuKey.literal('ñ'.code).path)
        assertEquals("Lit_%20", RokuKey.literal(' '.code).path)
        assertNull(RokuKey.raw("../reboot"))
        assertEquals("Lit_4", RokuKey.of(RemoteCommand.Digit(4))?.path)
    }
}

class DiscoveryClassificationTest {

    private fun ssdp(st: String, server: String, usn: String = "uuid:x") =
        Ssdp.parse("HTTP/1.1 200 OK\r\nST: $st\r\nSERVER: $server\r\nLocation: http://192.168.1.60:80/\r\nUSN: $usn\r\n\r\n", "192.168.1.60")!!

    @Test
    fun `cada marca nueva se reconoce por su respuesta SSDP`() {
        assertEquals(DeviceKind.SONY_BRAVIA, ssdp("urn:schemas-sony-com:service:ScalarWebAPI:1", "Linux/2.6 UPnP/1.0 KDL-55W800C").classify())
        assertEquals(DeviceKind.PANASONIC_VIERA, ssdp("urn:panasonic-com:device:p00RemoteController:1", "Linux/4.0 UPnP/1.0 Panasonic-UPnP-MW/1.0").classify())
        assertEquals(DeviceKind.VIZIO, ssdp("urn:dial-multiscreen-org:service:dial:1", "Linux/3.10 UPnP/1.0 Vizio/1.0").classify())
        assertEquals(DeviceKind.PHILIPS_JOINTSPACE, ssdp("urn:dial-multiscreen-org:service:dial:1", "Linux/4.9 UPnP/1.0 Philips/TPVision").classify())
        assertEquals(DeviceKind.HISENSE_VIDAA, ssdp("urn:dial-multiscreen-org:service:dial:1", "Linux UPnP/1.0 Hisense/VIDAA").classify())
        assertNull(ssdp("urn:dial-multiscreen-org:service:dial:1", "Linux UPnP/1.0 Generic/1.0").classify())
    }

    @Test
    fun `dos anuncios del mismo host se fusionan en una entrada`() {
        val ssdp = DiscoveredDevice("192.168.1.60", "192.168.1.60", DeviceKind.SONY_BRAVIA, 80, sources = setOf(DiscoveredDevice.Source.SSDP))
        val mdns = DiscoveredDevice("192.168.1.60", "Salón", DeviceKind.ANDROID_TV, 6466, sources = setOf(DiscoveredDevice.Source.MDNS))
        assertEquals(ssdp.key, mdns.key)
        val merged = ssdp.mergeWith(mdns)
        assertEquals(DeviceKind.ANDROID_TV, merged.kind)
        assertEquals(6466, merged.port)
        assertEquals("Salón", merged.name)
        assertEquals(2, merged.sources.size)
    }

    @Test
    fun `el alta manual ofrece todas las marcas de red y ninguna sin red`() {
        assertTrue(DeviceKind.KODI in manualAddKinds && DeviceKind.PANASONIC_VIERA in manualAddKinds)
        assertTrue(DeviceKind.INFRARED !in manualAddKinds && DeviceKind.BLUETOOTH_HID !in manualAddKinds)
        assertEquals(PairingHint.PASSPHRASE, DeviceKind.SONY_BRAVIA.pairingHint())
        assertEquals(PairingHint.PIN, DeviceKind.VIZIO.pairingHint())
        assertEquals(PairingHint.NONE, DeviceKind.ROKU.pairingHint())
    }
}
