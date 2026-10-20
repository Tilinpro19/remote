import Foundation
import Testing
@testable import RemoteCore

struct AndroidTVProtocolTests {
    @Test func protoRoundTripsNestedMessages() throws {
        let data = ProtoWriter {
            $0.int(1, 2)
            $0.int(2, 300)
            $0.message(10) { $0.string(1, "atvremote"); $0.string(2, "iPhone") }
        }.data
        let message = try ProtoMessage(parsing: data)
        #expect(message.int(1) == 2)
        #expect(message.int(2) == 300)
        #expect(message.message(10)?.string(1) == "atvremote")
        #expect(message.message(10)?.string(2) == "iPhone")
        #expect(!message.has(3))
    }

    @Test func pairingStatusIsReadFromOuterMessage() {
        #expect(AndroidTVProtocol.status(AndroidTVProtocol.options()) == AndroidTVProtocol.statusOK)
        #expect(AndroidTVProtocol.status(Data()) == 0)
    }

    @Test func keyInjectMatchesReferenceBytes() {
        // RemoteMessage { remote_key_inject (10) { key_code: 24, direction: SHORT (3) } }
        #expect(AndroidTVProtocol.keyInject(24) == Data([0x52, 0x04, 0x08, 0x18, 0x10, 0x03]))
    }

    @Test func framingPrefixesVarintLength() async throws {
        let payload = Data(repeating: 0xAB, count: 300)
        let framed = ProtoFraming.frame(payload)
        #expect(Array(framed.prefix(2)) == [0xAC, 0x02])

        var cursor = framed.startIndex
        let decoded = try await ProtoFraming.read(
            readByte: {
                defer { cursor += 1 }
                return framed[cursor]
            },
            readExactly: { count in
                defer { cursor += count }
                return framed[cursor..<cursor + count]
            }
        )
        #expect(decoded == payload)
    }

    @Test func parsesPairingCode() {
        #expect(AndroidTVProtocol.codeBytes(" a1b2c3 ") == [0xA1, 0xB2, 0xC3])
        #expect(AndroidTVProtocol.codeBytes("A1B2C") == nil)
        #expect(AndroidTVProtocol.codeBytes("ZZZZZZ") == nil)
    }

    @Test func mapsKeysToAndroidKeycodes() {
        #expect(AndroidTVKey.code(for: .volumeUp) == 24)
        #expect(AndroidTVKey.code(for: .digit(9)) == 16)
        #expect(AndroidTVKey.code(for: .touchTap) == nil)
    }

    @Test func extractsRSAComponentsWithoutSignPadding() throws {
        let modulus = Data([0x80] + Array(repeating: 0x11, count: 255))
        let pkcs1 = DER.sequence(DER.unsignedInteger(modulus), DER.unsignedInteger(Data([0x01, 0x00, 0x01])))
        let components = try DER.rsaPublicKeyComponents(pkcs1: pkcs1)
        #expect(components.modulus == modulus)
        #expect(components.exponent == Data([0x01, 0x00, 0x01]))
    }

    @Test func buildsWellFormedCertificate() throws {
        let pkcs1 = DER.sequence(DER.unsignedInteger(Data(repeating: 0xC3, count: 256)), DER.unsignedInteger(Data([1, 0, 1])))
        let tbs = SelfSignedCertificate.tbs(
            publicKeyPKCS1: pkcs1,
            commonName: "atvremote/UniversalRemote",
            serial: Data([0x7F, 1, 2, 3, 4, 5, 6, 7]),
            notBefore: Date(timeIntervalSince1970: 0),
            notAfter: Date(timeIntervalSince1970: 2_000_000_000)
        )
        let certificate = SelfSignedCertificate.certificate(tbs: tbs, signature: Data(repeating: 0x5A, count: 256))
        let outer = try DER.read(certificate)
        #expect(outer.tag == 0x30)
        #expect(outer.rest.isEmpty)
        let parsedTBS = try DER.read(outer.content)
        #expect(DER.tlv(parsedTBS.tag, parsedTBS.content) == tbs)
        let algorithm = try DER.read(parsedTBS.rest)
        let signature = try DER.read(algorithm.rest)
        #expect(signature.tag == 0x03)
        #expect(signature.content.count == 257)
        #expect(signature.rest.isEmpty)
    }
}

struct DiscoveryCurationTests {
    private func bonjour(_ name: String, type: String, kind: DeviceKind?) -> DiscoveredDevice {
        DiscoveredDevice(
            id: "bonjour:\(type):\(name)",
            name: name,
            kind: kind,
            source: .bonjour(name: name, type: type, domain: "local.", txt: [:])
        )
    }

    @Test func collapsesDuplicateAnnouncementsOfTheSameTV() {
        let devices = [
            bonjour("Sala", type: "_googlecast._tcp.", kind: .androidTV),
            bonjour("Sala", type: "_androidtvremote2._tcp.", kind: .androidTV),
            bonjour("LG OLED", type: "_airplay._tcp.", kind: nil),
            bonjour("lg oled", type: "_webostv._tcp.", kind: .lgWebOS),
            bonjour("Apple TV", type: "_airplay._tcp.", kind: nil),
        ]
        let curated = devices.curated()
        #expect(curated.map(\.id) == [
            "bonjour:_androidtvremote2._tcp.:Sala",
            "bonjour:_webostv._tcp.:lg oled",
            "bonjour:_airplay._tcp.:Apple TV",
        ])
    }
}
