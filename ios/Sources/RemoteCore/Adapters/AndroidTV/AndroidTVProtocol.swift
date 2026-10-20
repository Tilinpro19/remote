import Foundation
#if canImport(CryptoKit)
import CryptoKit
#endif

/// Mensajes de Android TV Remote v2.
/// - Puerto 6467: emparejamiento "Polo" (`OuterMessage`).
/// - Puerto 6466: canal de control (`RemoteMessage`).
enum AndroidTVProtocol {
    // MARK: Polo (emparejamiento)

    static let statusOK: Int64 = 200
    static let statusBadSecret: Int64 = 402

    private static let protocolVersion = 2
    private static let encodingHexadecimal = 3
    private static let codeLength = 6
    private static let roleInput = 1

    private static func outer(_ build: (inout ProtoWriter) -> Void) -> Data {
        ProtoWriter { writer in
            writer.int(1, protocolVersion)
            writer.int(2, statusOK)
            build(&writer)
        }.data
    }

    private static func hexEncoding(_ writer: inout ProtoWriter, field: Int) {
        writer.message(field) {
            $0.int(1, encodingHexadecimal)
            $0.int(2, codeLength)
        }
    }

    static func pairingRequest(clientName: String) -> Data {
        outer { $0.message(10) { $0.string(1, "atvremote"); $0.string(2, clientName) } }
    }

    static func options() -> Data {
        outer { $0.message(20) { hexEncoding(&$0, field: 1); $0.int(3, roleInput) } }
    }

    static func configuration() -> Data {
        outer { $0.message(30) { hexEncoding(&$0, field: 1); $0.int(2, roleInput) } }
    }

    static func secret(_ secret: Data) -> Data {
        outer { $0.message(40) { $0.bytes(1, secret) } }
    }

    /// Estado de un `OuterMessage` recibido (200 = OK).
    static func status(_ message: Data) -> Int64 {
        (try? ProtoMessage(parsing: message))?.int(2) ?? 0
    }

    /// Bytes del código de 6 caracteres hexadecimales, o `nil` si el formato no es válido.
    static func codeBytes(_ code: String) -> [UInt8]? {
        let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard normalized.count == codeLength, normalized.allSatisfy({ "0123456789ABCDEF".contains($0) }) else {
            return nil
        }
        var bytes: [UInt8] = []
        var index = normalized.startIndex
        while index < normalized.endIndex {
            let next = normalized.index(index, offsetBy: 2)
            bytes.append(UInt8(normalized[index..<next], radix: 16)!)
            index = next
        }
        return bytes
    }

    #if canImport(CryptoKit)
    /// Secreto = SHA-256(módulo y exponente del cliente, módulo y exponente del servidor, bytes 2..3
    /// del código). El primer byte del código es una suma de control: debe coincidir con hash[0].
    ///
    /// - Returns: el secreto, o `nil` si el código no corresponde a este emparejamiento.
    static func computeSecret(
        client: (modulus: Data, exponent: Data),
        server: (modulus: Data, exponent: Data),
        code: String
    ) -> Data? {
        guard let bytes = codeBytes(code) else { return nil }
        var hasher = SHA256()
        hasher.update(data: client.modulus)
        hasher.update(data: client.exponent)
        hasher.update(data: server.modulus)
        hasher.update(data: server.exponent)
        hasher.update(data: Data([bytes[1], bytes[2]]))
        let digest = Data(hasher.finalize())
        return digest.first == bytes[0] ? digest : nil
    }
    #endif

    // MARK: RemoteMessage (control)

    static let fieldConfigure = 1
    static let fieldSetActive = 2
    static let fieldPingRequest = 8
    static let fieldIMEShow = 22
    static let fieldStart = 40

    private static let features = 622
    private static let directionShort = 3

    static func configure(model: String, vendor: String, packageName: String, appVersion: String) -> Data {
        ProtoWriter {
            $0.message(fieldConfigure) {
                $0.int(1, features)
                $0.message(2) {
                    $0.string(1, model)
                    $0.string(2, vendor)
                    $0.int(3, 1)
                    $0.string(4, "1")
                    $0.string(5, packageName)
                    $0.string(6, appVersion)
                }
            }
        }.data
    }

    static func setActive() -> Data {
        ProtoWriter { $0.message(fieldSetActive) { $0.int(1, features) } }.data
    }

    static func pingResponse(_ value: Int64) -> Data {
        ProtoWriter { $0.message(9) { $0.int(1, value) } }.data
    }

    static func keyInject(_ keyCode: Int) -> Data {
        ProtoWriter {
            $0.message(10) {
                $0.int(1, keyCode)
                $0.int(2, directionShort)
            }
        }.data
    }
}

/// Android TV inyecta `KeyEvent.KEYCODE_*` estándar.
enum AndroidTVKey {
    static func code(for command: RemoteCommand) -> Int? {
        switch command {
        case .power, .powerOn, .powerOff: 26
        case .volumeUp: 24
        case .volumeDown: 25
        case .mute: 164
        case .channelUp: 166
        case .channelDown: 167
        case .digit(let value): 7 + Int(value)
        case .dpadUp: 19
        case .dpadDown: 20
        case .dpadLeft: 21
        case .dpadRight: 22
        case .dpadCenter: 23
        case .back: 4
        case .home: 3
        case .menu: 82
        case .info: 165
        case .input: 178
        case .search: 84
        case .playPause: 85
        case .play: 126
        case .pause: 127
        case .stop: 86
        case .rewind: 89
        case .fastForward: 90
        case .next: 87
        case .previous: 88
        case .backspace: 67
        case .enter: 66
        case .touchMove, .touchTap, .scroll, .text, .raw: nil
        }
    }
}
