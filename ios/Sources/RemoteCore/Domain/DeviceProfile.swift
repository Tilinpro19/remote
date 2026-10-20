import Foundation

public enum DeviceKind: String, Codable, Sendable, CaseIterable {
    case roku
    case androidTV
    case lgWebOS
    case samsungTizen
    case infrared
    case bluetooth

    /// Puerto de control por defecto para dispositivos IP.
    public var defaultControlPort: UInt16? {
        switch self {
        case .roku: 8060
        case .androidTV: 6466      // comandos; el emparejamiento va por 6467
        case .lgWebOS: 3001        // SSAP sobre WSS; 3000 en firmwares antiguos
        case .samsungTizen: 8002
        case .infrared, .bluetooth: nil
        }
    }
}

public enum DeviceTransport: Codable, Sendable, Hashable {
    case lan(host: String, port: UInt16)
    case infrared(codesetID: String)
    case bluetooth(peripheralID: UUID)
}

/// Perfil persistido de un equipo emparejado.
public struct DeviceProfile: Codable, Sendable, Hashable, Identifiable {
    public let id: UUID
    public var name: String
    public var kind: DeviceKind
    public var transport: DeviceTransport
    /// Se aprende en el primer emparejamiento para poder enviar Wake-on-LAN.
    public var macAddress: MACAddress?
    public var model: String?
    public var serialNumber: String?
    /// SHA-256 del certificado hoja aceptado en el primer emparejamiento TLS (TOFU).
    public var pinnedCertificateSHA256: Data?
    /// Token de emparejamiento (client-key de webOS, token de Tizen, huella del certificado de cliente ATV).
    public var pairingToken: String?
    public var lastConnectedAt: Date?

    public init(
        id: UUID = UUID(),
        name: String,
        kind: DeviceKind,
        transport: DeviceTransport,
        macAddress: MACAddress? = nil,
        model: String? = nil,
        serialNumber: String? = nil,
        pinnedCertificateSHA256: Data? = nil,
        pairingToken: String? = nil,
        lastConnectedAt: Date? = nil
    ) {
        self.id = id
        self.name = name
        self.kind = kind
        self.transport = transport
        self.macAddress = macAddress
        self.model = model
        self.serialNumber = serialNumber
        self.pinnedCertificateSHA256 = pinnedCertificateSHA256
        self.pairingToken = pairingToken
        self.lastConnectedAt = lastConnectedAt
    }

    public var lanHost: String? {
        if case .lan(let host, _) = transport { host } else { nil }
    }
}
