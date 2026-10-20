import Foundation

/// Codificación DER mínima para construir el certificado de cliente de Android TV y leer claves RSA.
enum DER {
    static func tlv(_ tag: UInt8, _ content: Data) -> Data {
        var out = Data([tag])
        let length = content.count
        if length < 0x80 {
            out.append(UInt8(length))
        } else {
            var bytes: [UInt8] = []
            var remaining = length
            while remaining > 0 {
                bytes.insert(UInt8(remaining & 0xFF), at: 0)
                remaining >>= 8
            }
            out.append(0x80 | UInt8(bytes.count))
            out.append(contentsOf: bytes)
        }
        out.append(content)
        return out
    }

    static func sequence(_ parts: Data...) -> Data { tlv(0x30, parts.reduce(Data(), +)) }

    static func set(_ parts: Data...) -> Data { tlv(0x31, parts.reduce(Data(), +)) }

    /// INTEGER positivo a partir de bytes sin signo (antepone 0x00 si el bit alto está activo).
    static func unsignedInteger(_ bytes: Data) -> Data {
        var content = Data(bytes.drop { $0 == 0 })
        if content.isEmpty { content = Data([0]) }
        if content[content.startIndex] & 0x80 != 0 { content.insert(0, at: 0) }
        return tlv(0x02, content)
    }

    static func utcTime(_ date: Date) -> Data {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyMMddHHmmss'Z'"
        return tlv(0x17, Data(formatter.string(from: date).utf8))
    }

    /// Lee un elemento TLV y devuelve (tag, contenido, resto).
    static func read(_ data: Data) throws -> (tag: UInt8, content: Data, rest: Data) {
        let bytes = [UInt8](data)
        guard bytes.count >= 2 else { throw RemoteError.invalidResponse("DER truncado") }
        var position = 1
        var length = Int(bytes[position])
        position += 1
        if length & 0x80 != 0 {
            let count = length & 0x7F
            guard count > 0, count <= 4, position + count <= bytes.count else {
                throw RemoteError.invalidResponse("longitud DER inválida")
            }
            length = bytes[position..<position + count].reduce(0) { ($0 << 8) | Int($1) }
            position += count
        }
        guard position + length <= bytes.count else { throw RemoteError.invalidResponse("DER truncado") }
        return (bytes[0], Data(bytes[position..<position + length]), Data(bytes[(position + length)...]))
    }

    /// Componentes de una clave pública RSA PKCS#1 (`SEQUENCE { modulus, exponent }`), sin ceros a la izquierda.
    static func rsaPublicKeyComponents(pkcs1 data: Data) throws -> (modulus: Data, exponent: Data) {
        let outer = try read(data)
        guard outer.tag == 0x30 else { throw RemoteError.invalidResponse("clave RSA no es SEQUENCE") }
        let modulus = try read(outer.content)
        let exponent = try read(modulus.rest)
        guard modulus.tag == 0x02, exponent.tag == 0x02 else {
            throw RemoteError.invalidResponse("clave RSA sin módulo/exponente")
        }
        return (stripSign(modulus.content), stripSign(exponent.content))
    }

    private static func stripSign(_ value: Data) -> Data {
        value.count > 1 && value[value.startIndex] == 0 ? Data(value.dropFirst()) : value
    }
}

/// Certificado X.509 v3 autofirmado (SHA256withRSA) codificado a mano: iOS no expone un generador.
enum SelfSignedCertificate {
    /// 1.2.840.113549.1.1.11
    static let sha256WithRSA = Data([0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0B])
    /// 1.2.840.113549.1.1.1
    static let rsaEncryption = Data([0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01])
    /// 2.5.4.3
    static let commonNameOID = Data([0x06, 0x03, 0x55, 0x04, 0x03])
    /// 2.5.29.19
    static let basicConstraintsOID = Data([0x06, 0x03, 0x55, 0x1D, 0x13])

    /// Parte firmada (`TBSCertificate`).
    static func tbs(publicKeyPKCS1: Data, commonName: String, serial: Data, notBefore: Date, notAfter: Date) -> Data {
        let algorithm = DER.sequence(sha256WithRSA, Data([0x05, 0x00]))
        let name = DER.sequence(DER.set(DER.sequence(commonNameOID, DER.tlv(0x0C, Data(commonName.utf8)))))
        let subjectPublicKeyInfo = DER.sequence(
            DER.sequence(rsaEncryption, Data([0x05, 0x00])),
            DER.tlv(0x03, Data([0]) + publicKeyPKCS1)
        )
        // basicConstraints (cA = false) para que sea un certificado de entidad final v3 válido.
        let extensions = DER.tlv(0xA3, DER.sequence(DER.sequence(basicConstraintsOID, DER.tlv(0x04, DER.sequence()))))
        return DER.sequence(
            DER.tlv(0xA0, DER.tlv(0x02, Data([2]))),
            DER.unsignedInteger(serial),
            algorithm,
            name,
            DER.sequence(DER.utcTime(notBefore), DER.utcTime(notAfter)),
            name,
            subjectPublicKeyInfo,
            extensions
        )
    }

    static func certificate(tbs: Data, signature: Data) -> Data {
        DER.sequence(tbs, DER.sequence(sha256WithRSA, Data([0x05, 0x00])), DER.tlv(0x03, Data([0]) + signature))
    }
}
