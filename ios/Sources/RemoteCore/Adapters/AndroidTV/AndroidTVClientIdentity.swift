#if canImport(Network)
import CryptoKit
import Foundation
import Security

/// Certificado de cliente con el que el iPhone se identifica ante los Android TV.
///
/// La clave RSA 2048 y el certificado autofirmado viven en el llavero (solo este dispositivo). Es
/// común a todos los TVs: el TV recuerda el certificado tras el emparejamiento. Si cambia (p. ej.
/// reinstalación o llavero borrado) hay que re-emparejar, lo que el adaptador detecta comparando
/// `fingerprint` con el token guardado en el perfil.
public final class AndroidTVClientIdentity: @unchecked Sendable {
    public static let shared = AndroidTVClientIdentity()

    private static let label = "atv-remote-client"
    private static let keyTag = Data("com.universalremote.atv.client".utf8)
    private static let commonName = "atvremote/UniversalRemote"
    private static let validity: TimeInterval = 20 * 365 * 86_400

    private let lock = NSLock()
    private var cached: Loaded?

    struct Loaded {
        let identity: SecIdentity
        let certificateDER: Data
        let publicKeyPKCS1: Data
    }

    init() {}

    /// SHA-256 (hex) del certificado; se guarda como `pairingToken` tras emparejar.
    public func fingerprint() throws -> String {
        try load().certificateDER.sha256Hex
    }

    func tlsIdentity() throws -> TLSClientIdentity {
        TLSClientIdentity(try load().identity)
    }

    func publicKeyComponents() throws -> (modulus: Data, exponent: Data) {
        try DER.rsaPublicKeyComponents(pkcs1: load().publicKeyPKCS1)
    }

    private func load() throws -> Loaded {
        lock.lock()
        defer { lock.unlock() }
        if let cached { return cached }
        let loaded = try existing() ?? generate()
        cached = loaded
        return loaded
    }

    private func existing() throws -> Loaded? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecAttrLabel as String: Self.label,
            kSecReturnRef as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let result, CFGetTypeID(result) == SecIdentityGetTypeID() else {
            return nil
        }
        let identity = result as! SecIdentity
        var certificate: SecCertificate?
        var privateKey: SecKey?
        guard SecIdentityCopyCertificate(identity, &certificate) == errSecSuccess, let certificate,
              SecIdentityCopyPrivateKey(identity, &privateKey) == errSecSuccess, let privateKey,
              let publicKey = SecKeyCopyPublicKey(privateKey),
              let pkcs1 = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else {
            return nil
        }
        return Loaded(identity: identity, certificateDER: SecCertificateCopyData(certificate) as Data, publicKeyPKCS1: pkcs1)
    }

    private func generate() throws -> Loaded {
        removeLeftovers()

        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeySizeInBits as String: 2048,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: Self.keyTag,
                kSecAttrLabel as String: Self.label,
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ],
        ]
        var error: Unmanaged<CFError>?
        guard let privateKey = SecKeyCreateRandomKey(attributes as CFDictionary, &error),
              let publicKey = SecKeyCopyPublicKey(privateKey),
              let pkcs1 = SecKeyCopyExternalRepresentation(publicKey, &error) as Data? else {
            throw RemoteError.storage("no se pudo generar la clave RSA: \(Self.describe(error))")
        }

        var serial = Data(count: 8)
        _ = serial.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 8, $0.baseAddress!) }
        serial[serial.startIndex] &= 0x7F
        serial[serial.startIndex] |= 0x01
        let now = Date()
        let tbs = SelfSignedCertificate.tbs(
            publicKeyPKCS1: pkcs1,
            commonName: Self.commonName,
            serial: serial,
            notBefore: now.addingTimeInterval(-86_400),
            notAfter: now.addingTimeInterval(Self.validity)
        )
        guard let signature = SecKeyCreateSignature(privateKey, .rsaSignatureMessagePKCS1v15SHA256, tbs as CFData, &error) as Data? else {
            throw RemoteError.storage("no se pudo firmar el certificado: \(Self.describe(error))")
        }
        let der = SelfSignedCertificate.certificate(tbs: tbs, signature: signature)
        guard let certificate = SecCertificateCreateWithData(nil, der as CFData) else {
            throw RemoteError.storage("certificado de cliente inválido")
        }

        let add: [String: Any] = [
            kSecClass as String: kSecClassCertificate,
            kSecValueRef as String: certificate,
            kSecAttrLabel as String: Self.label,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemAdd(add as CFDictionary, nil)
        guard status == errSecSuccess || status == errSecDuplicateItem else {
            throw RemoteError.storage("llavero OSStatus \(status)")
        }
        // El llavero enlaza certificado y clave por el hash de la clave pública.
        guard let loaded = try existing() else {
            throw RemoteError.storage("el llavero no devolvió la identidad de cliente")
        }
        return loaded
    }

    /// Restos de un intento anterior (clave sin certificado o viceversa) impedirían formar la identidad.
    private func removeLeftovers() {
        SecItemDelete([
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: Self.keyTag,
        ] as CFDictionary)
        SecItemDelete([
            kSecClass as String: kSecClassCertificate,
            kSecAttrLabel as String: Self.label,
        ] as CFDictionary)
    }

    private static func describe(_ error: Unmanaged<CFError>?) -> String {
        error.map { String(describing: $0.takeRetainedValue()) } ?? "desconocido"
    }
}

extension Data {
    var sha256Hex: String {
        SHA256.hash(data: self).map { byte in
            let digits = String(byte, radix: 16)
            return digits.count == 1 ? "0" + digits : digits
        }.joined()
    }
}
#endif
