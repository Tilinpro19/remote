import Foundation

/// Candidato encontrado por el escáner (SSDP o mDNS), aún sin perfil.
public struct DiscoveredDevice: Sendable, Hashable, Identifiable {
    public enum Source: Sendable, Hashable {
        case ssdp(location: URL, usn: String)
        case bonjour(name: String, type: String, domain: String, txt: [String: String])
    }

    public let id: String
    public let name: String
    /// `nil` si el servicio no corresponde a ningún adaptador conocido (p. ej. AirPlay genérico).
    public let kind: DeviceKind?
    public let source: Source

    public init(id: String, name: String, kind: DeviceKind?, source: Source) {
        self.id = id
        self.name = name
        self.kind = kind
        self.source = source
    }
}

/// Resultado agregado del escáner híbrido.
public struct DiscoverySnapshot: Sendable, Equatable {
    public var devices: [DiscoveredDevice]
    /// SSDP falla sin el entitlement de multicast; no es fatal (Bonjour y alta manual siguen disponibles).
    public var ssdpError: RemoteError?

    public init(devices: [DiscoveredDevice] = [], ssdpError: RemoteError? = nil) {
        self.devices = devices
        self.ssdpError = ssdpError
    }
}

extension Array where Element == DiscoveredDevice {
    /// Un mismo TV suele anunciarse por varios canales (Android TV + Cast, AirPlay en LG/Samsung):
    /// se descartan las entradas sin tipo que repiten el nombre de una reconocida, se prioriza el
    /// servicio específico del protocolo y se deja una sola entrada por tipo y nombre.
    public func curated() -> [DiscoveredDevice] {
        let knownNames = Set(filter { $0.kind != nil }.map { $0.name.lowercased() })
        var seen = Set<String>()
        return filter { $0.kind != nil || !knownNames.contains($0.name.lowercased()) }
            .enumerated()
            .sorted { lhs, rhs in
                (lhs.element.rank, lhs.offset) < (rhs.element.rank, rhs.offset)
            }
            .map(\.element)
            .filter { seen.insert("\($0.kind?.rawValue ?? "-")|\($0.name.lowercased())").inserted }
    }
}

extension DiscoveredDevice {
    /// 0 = servicio del propio protocolo, 1 = servicio genérico (Cast, AirPlay), 2 = sin adaptador.
    var rank: Int {
        guard kind != nil else { return 2 }
        if case .bonjour(_, let type, _, _) = source, type.hasPrefix("_googlecast") || type.hasPrefix("_airplay") {
            return 1
        }
        return 0
    }

    /// Host ya conocido sin resolver (SSDP trae la IP en `LOCATION`; Bonjour requiere resolución).
    public var knownHost: String? {
        if case .ssdp(let location, _) = source { location.host(percentEncoded: false) } else { nil }
    }
}
