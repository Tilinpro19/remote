import Foundation
import Testing
@testable import RemoteCore

struct RemoteCommandTests {
    @Test func parameterlessWireNamesRoundTrip() {
        for command in RemoteCommand.parameterless {
            #expect(RemoteCommand(wireName: command.wireName) == command)
        }
    }

    @Test func parameterizedWireNames() {
        #expect(RemoteCommand(wireName: "CMD_DIGIT_7") == .digit(7))
        #expect(RemoteCommand(wireName: "CMD_DIGIT_12") == nil)
        #expect(RemoteCommand(wireName: "CMD_TOUCH_MOVE", dx: 4, dy: -2) == .touchMove(dx: 4, dy: -2))
        #expect(RemoteCommand(wireName: "CMD_TEXT", text: "hola") == .text("hola"))
        #expect(RemoteCommand(wireName: "CMD_TEXT") == nil)
        #expect(RemoteCommand(wireName: "CMD_RAW_InputHDMI1") == .raw("InputHDMI1"))
        #expect(RemoteCommand(wireName: "CMD_UNKNOWN") == nil)
    }

    @Test func hapticWeightFollowsCriticality() {
        #expect(RemoteCommand.power.hapticWeight == .heavy)
        #expect(RemoteCommand.dpadCenter.hapticWeight == .medium)
        #expect(RemoteCommand.volumeUp.hapticWeight == .light)
        #expect(RemoteCommand.touchMove(dx: 1, dy: 1).hapticWeight == .none)
    }
}

struct MACAddressTests {
    @Test(arguments: ["AA:BB:CC:00:11:22", "aa-bb-cc-00-11-22", "aabb.cc00.1122", "AABBCC001122"])
    func parsesCommonNotations(text: String) throws {
        let mac = try MACAddress(text)
        #expect(mac.bytes == [0xAA, 0xBB, 0xCC, 0x00, 0x11, 0x22])
        #expect(mac.description == "AA:BB:CC:00:11:22")
    }

    @Test(arguments: ["AA:BB:CC:00:11", "GG:BB:CC:00:11:22", "AA/BB/CC/00/11/22", ""])
    func rejectsInvalid(text: String) {
        #expect(throws: RemoteError.self) { try MACAddress(text) }
    }

    @Test func codableAsString() throws {
        let mac = try MACAddress("01:02:03:04:05:06")
        let data = try JSONEncoder().encode([mac])
        #expect(String(decoding: data, as: UTF8.self) == "[\"01:02:03:04:05:06\"]")
        #expect(try JSONDecoder().decode([MACAddress].self, from: data) == [mac])
    }
}

struct WakeOnLANPacketTests {
    @Test func magicPacketLayout() throws {
        let mac = try MACAddress("01:23:45:67:89:AB")
        let packet = [UInt8](WakeOnLANPacket.make(for: mac))
        #expect(packet.count == 102)
        #expect(packet.prefix(6).allSatisfy { $0 == 0xFF })
        for repetition in 0..<16 {
            let start = 6 + repetition * 6
            #expect(Array(packet[start..<start + 6]) == mac.bytes)
        }
    }
}

struct DeviceProfileTests {
    @Test func codableRoundTrip() throws {
        let mac = try MACAddress("01:02:03:04:05:06")
        let profile = DeviceProfile(
            name: "Sala",
            kind: .roku,
            transport: .lan(host: "192.168.1.20", port: 8060),
            macAddress: mac,
            lastConnectedAt: Date(timeIntervalSince1970: 1_700_000_000)
        )
        let data = try JSONEncoder().encode(profile)
        #expect(try JSONDecoder().decode(DeviceProfile.self, from: data) == profile)
    }

    @Test func reconnectBackoffIsBounded() {
        let policy = ReconnectPolicy(maxAttempts: 10, baseDelay: .milliseconds(500), maxDelay: .seconds(4))
        #expect(policy.delay(forAttempt: 1) == .milliseconds(500))
        #expect(policy.delay(forAttempt: 2) == .seconds(1))
        #expect(policy.delay(forAttempt: 4) == .seconds(4))
        #expect(policy.delay(forAttempt: 9) == .seconds(4))
    }
}
