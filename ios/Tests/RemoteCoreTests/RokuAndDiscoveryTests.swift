import Foundation
import Testing
@testable import RemoteCore

struct RokuKeyTests {
    @Test func mapsCoreCommands() {
        #expect(RokuKey(command: .dpadCenter)?.path == "Select")
        #expect(RokuKey(command: .touchTap)?.path == "Select")
        #expect(RokuKey(command: .volumeUp)?.path == "VolumeUp")
        #expect(RokuKey(command: .mute)?.path == "VolumeMute")
        #expect(RokuKey(command: .playPause)?.path == "Play")
        #expect(RokuKey(command: .digit(4))?.path == "Lit_4")
        #expect(RokuKey(command: .input) == nil)
        #expect(RokuKey(command: .raw("InputHDMI2"))?.path == "InputHDMI2")
        #expect(RokuKey(command: .raw("../launch")) == nil)
    }

    @Test func literalPercentEncodesUTF8() {
        #expect(RokuKey.literal("a").path == "Lit_a")
        #expect(RokuKey.literal(" ").path == "Lit_%20")
        #expect(RokuKey.literal("ñ").path == "Lit_%C3%B1")
        #expect(RokuKey.literal("&").path == "Lit_%26")
    }
}

struct RokuDeviceInfoTests {
    let xml = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <device-info>
        <udn>29380001-0000-1000-8000-d83134aabbcc</udn>
        <serial-number>X01900ABCDEF</serial-number>
        <model-name>Roku Ultra</model-name>
        <user-device-name>Roku Sala</user-device-name>
        <wifi-mac>d8:31:34:aa:bb:cc</wifi-mac>
        <ethernet-mac>d8:31:34:aa:bb:cd</ethernet-mac>
        <network-type>ethernet</network-type>
        <is-tv>false</is-tv>
        <power-mode>PowerOn</power-mode>
        <supports-wake-on-wlan>false</supports-wake-on-wlan>
    </device-info>
    """

    @Test func parsesFields() throws {
        let info = try RokuDeviceInfo(xml: Data(xml.utf8))
        #expect(info.serialNumber == "X01900ABCDEF")
        #expect(info.modelName == "Roku Ultra")
        #expect(info.friendlyName == "Roku Sala")
        #expect(info.isTV == false)
        #expect(info.powerMode == "PowerOn")
        let ethernetMAC = try MACAddress("D8:31:34:AA:BB:CD")
        #expect(info.macAddress == ethernetMAC)
    }

    @Test func rejectsForeignXML() {
        #expect(throws: RemoteError.self) {
            try RokuDeviceInfo(xml: Data("<root><a>1</a></root>".utf8))
        }
    }
}

struct SSDPTests {
    @Test func buildsSearchRequest() {
        let text = String(decoding: SSDPMessage.searchRequest(target: "roku:ecp", mx: 2), as: UTF8.self)
        #expect(text.hasPrefix("M-SEARCH * HTTP/1.1\r\n"))
        #expect(text.contains("ST: roku:ecp\r\n"))
        #expect(text.contains("MAN: \"ssdp:discover\"\r\n"))
        #expect(text.hasSuffix("\r\n\r\n"))
    }

    @Test func parsesRokuResponse() throws {
        let raw = "HTTP/1.1 200 OK\r\nCache-Control: max-age=3600\r\nST: roku:ecp\r\n"
            + "Location: http://192.168.1.134:8060/\r\nUSN: uuid:roku:ecp:X01900ABCDEF\r\n"
            + "Server: Roku/12.5.0 UPnP/1.0 Roku/12.5.0\r\n\r\n"
        let response = try #require(SSDPMessage.parse(Data(raw.utf8)))
        #expect(response.location.absoluteString == "http://192.168.1.134:8060/")
        #expect(response.searchTarget == "roku:ecp")
        #expect(response.usn == "uuid:roku:ecp:X01900ABCDEF")
        #expect(response.server?.hasPrefix("Roku/") == true)
    }

    @Test func ignoresByebyeAndGarbage() {
        let byebye = "NOTIFY * HTTP/1.1\r\nNT: roku:ecp\r\nNTS: ssdp:byebye\r\nUSN: uuid:x\r\nLOCATION: http://1.2.3.4/\r\n\r\n"
        #expect(SSDPMessage.parse(Data(byebye.utf8)) == nil)
        #expect(SSDPMessage.parse(Data("hello".utf8)) == nil)
    }
}

struct PointerTests {
    @Test func accumulatesOnDominantAxis() {
        var pointer = PointerAccumulator(threshold: 36)
        #expect(pointer.consume(dx: 20, dy: 4).isEmpty)
        #expect(pointer.consume(dx: 60, dy: 3) == [.right, .right])
        #expect(pointer.consume(dx: 0, dy: -40) == [.up])
    }

    @Test func capsBurstSize() {
        var pointer = PointerAccumulator(threshold: 10, maxStepsPerUpdate: 3)
        #expect(pointer.consume(dx: -500, dy: 0) == [.left, .left, .left])
        #expect(pointer.consume(dx: 0, dy: 0).isEmpty)
    }

    @Test func touchpadDetectsTapAndMoves() {
        let start = ContinuousClock.now
        var tracker = TouchpadTracker(sensitivity: 2)
        tracker.began(at: .init(x: 100, y: 100), time: start)
        #expect(tracker.ended(at: .init(x: 103, y: 101), time: start + .milliseconds(120)) == [.tap])

        tracker.began(at: .init(x: 0, y: 0), time: start)
        #expect(tracker.moved(to: .init(x: 30, y: 10)) == .move(dx: 60, dy: 20))
        #expect(tracker.ended(at: .init(x: 30, y: 10), time: start + .seconds(1)).isEmpty)
    }
}
