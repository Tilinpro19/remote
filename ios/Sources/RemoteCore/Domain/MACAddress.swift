import Foundation

/// Dirección MAC de 48 bits. Acepta `AA:BB:CC:DD:EE:FF`, `aa-bb-…`, `aabb.ccdd.eeff` o sin separadores.
public struct MACAddress: Hashable, Sendable, Codable, CustomStringConvertible {
    public let bytes: [UInt8]

    public init(bytes: [UInt8]) throws {
        guard bytes.count == 6 else {
            throw RemoteError.invalidMACAddress(bytes.map { String($0, radix: 16) }.joined(separator: ":"))
        }
        self.bytes = bytes
    }

    public init(_ string: String) throws {
        let trimmed = string.trimmingCharacters(in: .whitespaces)
        let hex = trimmed.filter(\.isHexDigit)
        let separators = trimmed.filter { !$0.isHexDigit }
        guard hex.count == 12, separators.allSatisfy({ ":-.".contains($0) }) else {
            throw RemoteError.invalidMACAddress(string)
        }
        var result: [UInt8] = []
        result.reserveCapacity(6)
        var index = hex.startIndex
        for _ in 0..<6 {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else {
                throw RemoteError.invalidMACAddress(string)
            }
            result.append(byte)
            index = next
        }
        self.bytes = result
    }

    public var description: String {
        bytes.map { byte in
            let digits = String(byte, radix: 16, uppercase: true)
            return digits.count == 1 ? "0" + digits : digits
        }.joined(separator: ":")
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.singleValueContainer()
        try self.init(container.decode(String.self))
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}
