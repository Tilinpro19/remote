#if canImport(Network)
import Foundation
@preconcurrency import Network

/// Tipos Bonjour declarados en `NSBonjourServices` (Resources/Info.plist).
/// Navegar un tipo no declarado falla con `NoAuth (-65555)` en iOS 14+.
public enum BonjourServiceType {
    public static let googleCast = "_googlecast._tcp"
    public static let androidTVRemote = "_androidtvremote2._tcp"
    public static let airPlay = "_airplay._tcp"
    public static let webOS = "_webostv._tcp"
    public static let samsung = "_samsungtv._tcp"
    static let preflight = "_urpreflight._tcp"

    public static let discoverable = [androidTVRemote, googleCast, webOS, samsung, airPlay]

    public static func deviceKind(for type: String) -> DeviceKind? {
        switch type {
        case androidTVRemote, googleCast: .androidTV
        case webOS: .lgWebOS
        case samsung: .samsungTizen
        default: nil
        }
    }
}

public struct ResolvedEndpoint: Sendable, Equatable {
    public let host: String
    public let port: UInt16
}

public enum BonjourDiscovery {
    private static let queue = DispatchQueue(label: "remote.bonjour")

    /// Stream continuo con la lista completa de servicios vigentes (por tipo, sin duplicados).
    public static func services(
        ofTypes types: [String],
        domain: String = "local."
    ) -> AsyncThrowingStream<[DiscoveredDevice], any Error> {
        AsyncThrowingStream { continuation in
            let snapshot = LockedBox<[String: [DiscoveredDevice]]>([:])
            var browsers: [NWBrowser] = []

            for type in types {
                let parameters = NWParameters()
                parameters.includePeerToPeer = false
                let browser = NWBrowser(for: .bonjourWithTXTRecord(type: type, domain: domain), using: parameters)

                browser.stateUpdateHandler = { state in
                    switch state {
                    case .waiting(let error), .failed(let error):
                        if case .dns(let code) = error, Int32(code) == dnsServicePolicyDenied {
                            continuation.finish(throwing: RemoteError.localNetworkDenied)
                        } else if case .failed = state {
                            continuation.finish(throwing: RemoteError.from(error, host: type, path: nil))
                        }
                    default:
                        break
                    }
                }

                browser.browseResultsChangedHandler = { results, _ in
                    let devices = results.compactMap { BonjourDiscovery.device(from: $0, type: type) }
                    let merged = snapshot.mutate { all -> [DiscoveredDevice] in
                        all[type] = devices
                        return all.values.flatMap { $0 }.sorted { $0.name < $1.name }
                    }
                    continuation.yield(merged)
                }

                browser.start(queue: queue)
                browsers.append(browser)
            }

            let active = browsers
            continuation.onTermination = { _ in
                active.forEach { $0.cancel() }
            }
        }
    }

    /// Resuelve un servicio a IPv4 + puerto abriendo una conexión TCP efímera.
    public static func resolve(name: String, type: String, domain: String, timeout: TimeInterval = 5) async throws -> ResolvedEndpoint {
        let parameters = NWParameters.tcp
        if let ip = parameters.defaultProtocolStack.internetProtocol as? NWProtocolIP.Options {
            ip.version = .v4
        }
        let endpoint = NWEndpoint.service(name: name, type: type, domain: domain, interface: nil)
        let connection = NWConnection(to: endpoint, using: parameters)
        defer { connection.cancel() }
        try await connection.startAndAwaitReady(on: queue, timeout: timeout, host: name)
        guard case .hostPort(let host, let port)? = connection.currentPath?.remoteEndpoint else {
            throw RemoteError.invalidResponse("no se pudo resolver \(name)")
        }
        return ResolvedEndpoint(host: host.literal, port: port.rawValue)
    }

    private static func device(from result: NWBrowser.Result, type: String) -> DiscoveredDevice? {
        guard case .service(let name, let serviceType, let domain, _) = result.endpoint else { return nil }
        var txt: [String: String] = [:]
        if case .bonjour(let record) = result.metadata {
            txt = record.dictionary
        }
        // Android TV publica el nombre amigable en el TXT `fn` de _googlecast.
        let displayName = txt["fn"] ?? name
        return DiscoveredDevice(
            id: "bonjour:\(serviceType):\(name)",
            name: displayName,
            kind: BonjourServiceType.deviceKind(for: type),
            source: .bonjour(name: name, type: serviceType, domain: domain, txt: txt)
        )
    }
}
#endif
