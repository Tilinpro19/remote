#if canImport(Network)
import CryptoKit
import Foundation
@preconcurrency import Network
import Security

/// Política de confianza para TLS contra equipos de la LAN.
public enum TLSTrustPolicy: Sendable, Equatable {
    /// Validación estándar contra los anclajes del sistema.
    case system
    /// TVs con certificado autofirmado (Android TV, Samsung, webOS): se acepta cualquier hoja
    /// y se registra su SHA-256 para fijarla tras el primer emparejamiento.
    case acceptSelfSigned
    /// Trust-on-first-use: solo se acepta la hoja cuyo SHA-256 coincide.
    case pinned(sha256: Data)
}

/// Identidad de cliente TLS (Android TV Remote v2 exige certificado de cliente).
public struct TLSClientIdentity: @unchecked Sendable {
    public let identity: SecIdentity

    public init(_ identity: SecIdentity) {
        self.identity = identity
    }
}

enum TLSTrust {
    static func makeOptions(
        policy: TLSTrustPolicy,
        clientIdentity: TLSClientIdentity?,
        observedLeaf: LockedBox<Data?>,
        queue: DispatchQueue
    ) -> NWProtocolTLS.Options {
        let options = NWProtocolTLS.Options()
        let security = options.securityProtocolOptions
        sec_protocol_options_set_min_tls_protocol_version(security, .TLSv12)

        if let clientIdentity, let identity = sec_identity_create(clientIdentity.identity) {
            sec_protocol_options_set_local_identity(security, identity)
            // TLS 1.3 obliga a firmar el CertificateVerify con RSA-PSS, que varios Android TV no
            // negocian bien: se fija TLS 1.2, el mismo que usa la app oficial.
            sec_protocol_options_set_max_tls_protocol_version(security, .TLSv12)
        }

        sec_protocol_options_set_verify_block(security, { _, trustRef, complete in
            let trust = sec_trust_copy_ref(trustRef).takeRetainedValue()
            let leafDER = TLSTrust.leafDER(of: trust)
            let leafDigest = leafDER.map { Data(SHA256.hash(data: $0)) }
            observedLeaf.mutate { $0 = leafDER }

            switch policy {
            case .system:
                var error: CFError?
                complete(SecTrustEvaluateWithError(trust, &error))
            case .acceptSelfSigned:
                complete(leafDigest != nil)
            case .pinned(let expected):
                complete(leafDigest == expected)
            }
        }, queue)

        return options
    }

    /// Certificado hoja (DER) presentado por el servidor.
    static func leafDER(of trust: SecTrust) -> Data? {
        guard let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              let leaf = chain.first else { return nil }
        return SecCertificateCopyData(leaf) as Data
    }

    /// Componentes RSA (módulo, exponente) de la clave pública de un certificado DER.
    static func rsaPublicKeyComponents(certificateDER: Data) throws -> (modulus: Data, exponent: Data) {
        guard let certificate = SecCertificateCreateWithData(nil, certificateDER as CFData),
              let key = SecCertificateCopyKey(certificate),
              let pkcs1 = SecKeyCopyExternalRepresentation(key, nil) as Data? else {
            throw RemoteError.invalidResponse("el TV no presentó una clave RSA")
        }
        return try DER.rsaPublicKeyComponents(pkcs1: pkcs1)
    }
}
#endif
