import Foundation

/// Subconjunto mínimo de protobuf (varint + length-delimited) suficiente para los mensajes de
/// Android TV Remote v2; evita arrastrar SwiftProtobuf y su generador de código.
struct ProtoWriter {
    static let wireVarint = 0
    static let wireFixed64 = 1
    static let wireLength = 2
    static let wireFixed32 = 5

    private(set) var data = Data()

    init() {}

    init(_ build: (inout ProtoWriter) -> Void) {
        build(&self)
    }

    mutating func int(_ field: Int, _ value: Int64) {
        tag(field, Self.wireVarint)
        Self.appendVarint(UInt64(bitPattern: value), to: &data)
    }

    mutating func int(_ field: Int, _ value: Int) {
        int(field, Int64(value))
    }

    mutating func bytes(_ field: Int, _ value: Data) {
        tag(field, Self.wireLength)
        Self.appendVarint(UInt64(value.count), to: &data)
        data.append(value)
    }

    mutating func string(_ field: Int, _ value: String) {
        bytes(field, Data(value.utf8))
    }

    mutating func message(_ field: Int, _ build: (inout ProtoWriter) -> Void) {
        bytes(field, ProtoWriter(build).data)
    }

    private mutating func tag(_ field: Int, _ wire: Int) {
        Self.appendVarint(UInt64((field << 3) | wire), to: &data)
    }

    static func appendVarint(_ value: UInt64, to data: inout Data) {
        var remaining = value
        while remaining & ~0x7F != 0 {
            data.append(UInt8(truncatingIfNeeded: (remaining & 0x7F) | 0x80))
            remaining >>= 7
        }
        data.append(UInt8(truncatingIfNeeded: remaining))
    }
}

/// Mensaje decodificado: número de campo → valores (`Int64` para varint, `Data` para length-delimited).
struct ProtoMessage {
    private enum Value {
        case varint(Int64)
        case bytes(Data)
    }

    private let fields: [Int: [Value]]

    func has(_ field: Int) -> Bool { fields[field] != nil }

    func int(_ field: Int) -> Int64? {
        if case .varint(let value)? = fields[field]?.first { value } else { nil }
    }

    func bytes(_ field: Int) -> Data? {
        if case .bytes(let value)? = fields[field]?.first { value } else { nil }
    }

    func string(_ field: Int) -> String? {
        bytes(field).map { String(decoding: $0, as: UTF8.self) }
    }

    func message(_ field: Int) -> ProtoMessage? {
        bytes(field).flatMap { try? ProtoMessage(parsing: $0) }
    }

    init(parsing input: Data) throws {
        let data = [UInt8](input)
        var fields: [Int: [Value]] = [:]
        var position = 0

        func readVarint() throws -> UInt64 {
            var shift: UInt64 = 0
            var result: UInt64 = 0
            while true {
                guard position < data.count else { throw RemoteError.invalidResponse("varint truncado") }
                let byte = data[position]
                position += 1
                result |= UInt64(byte & 0x7F) << shift
                if byte & 0x80 == 0 { return result }
                shift += 7
                guard shift <= 63 else { throw RemoteError.invalidResponse("varint demasiado largo") }
            }
        }

        while position < data.count {
            let key = try readVarint()
            let field = Int(key >> 3)
            switch Int(key & 0x7) {
            case ProtoWriter.wireVarint:
                fields[field, default: []].append(.varint(Int64(bitPattern: try readVarint())))
            case ProtoWriter.wireLength:
                let length = Int(try readVarint())
                guard length >= 0, position + length <= data.count else {
                    throw RemoteError.invalidResponse("campo protobuf truncado")
                }
                fields[field, default: []].append(.bytes(Data(data[position..<position + length])))
                position += length
            case ProtoWriter.wireFixed64:
                position += 8
            case ProtoWriter.wireFixed32:
                position += 4
            default:
                throw RemoteError.invalidResponse("tipo de campo protobuf no soportado")
            }
        }
        self.fields = fields
    }
}

/// Ambos canales (6466/6467) enmarcan cada mensaje con su longitud en varint.
enum ProtoFraming {
    static let maxMessageBytes = 64 * 1024

    static func frame(_ message: Data) -> Data {
        var out = Data()
        out.reserveCapacity(message.count + 5)
        ProtoWriter.appendVarint(UInt64(message.count), to: &out)
        out.append(message)
        return out
    }

    /// Lee una trama completa usando `readByte`/`readExactly` del transporte.
    static func read(
        readByte: () async throws -> UInt8,
        readExactly: (Int) async throws -> Data
    ) async throws -> Data {
        var shift = 0
        var length = 0
        while true {
            let byte = try await readByte()
            length |= Int(byte & 0x7F) << shift
            if byte & 0x80 == 0 { break }
            shift += 7
            guard shift <= 28 else { throw RemoteError.invalidResponse("longitud de mensaje inválida") }
        }
        guard length <= maxMessageBytes else {
            throw RemoteError.invalidResponse("mensaje demasiado grande: \(length)")
        }
        return length == 0 ? Data() : try await readExactly(length)
    }
}
