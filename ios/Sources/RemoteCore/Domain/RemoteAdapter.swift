import Foundation

/// Eventos que un adaptador publica hacia el gestor.
public enum AdapterEvent: Sendable, Equatable {
    case stateChanged(ConnectionState)
    /// El adaptador aprendió datos persistibles (MAC, modelo, token, huella TLS).
    case profileUpdated(DeviceProfile)
    case keyboardRequested(initialText: String)
    case keyboardDismissed
}

/// Estrategia por protocolo. Las implementaciones son `actor` para serializar el acceso al socket.
///
/// Contrato:
/// - `connect()` es idempotente y puede llamarse de nuevo tras `disconnect()` o un fallo.
/// - `events` es un único stream de por vida del adaptador (un solo consumidor: el gestor).
/// - `send(_:)` lanza `RemoteError.notConnected` si no hay sesión y el comando no puede despertar al equipo.
public protocol RemoteAdapter: AnyObject, Sendable {
    var profileID: UUID { get }
    var capabilities: Set<RemoteCapability> { get }
    var events: AsyncStream<AdapterEvent> { get }

    func connect() async throws
    func disconnect() async
    func send(_ command: RemoteCommand) async throws
    func supports(_ command: RemoteCommand) -> Bool
    func submitPairingCode(_ code: String) async throws
}

extension RemoteAdapter {
    public func supports(_ command: RemoteCommand) -> Bool {
        capabilities.contains(command.requiredCapability)
    }

    public func submitPairingCode(_ code: String) async throws {
        throw RemoteError.unsupportedCommand("PAIRING_CODE")
    }
}

/// Registro tipo → fábrica de adaptador.
public struct AdapterRegistry: Sendable {
    public typealias Factory = @Sendable (DeviceProfile) throws -> any RemoteAdapter

    private var factories: [DeviceKind: Factory]

    public init(_ factories: [DeviceKind: Factory] = [:]) {
        self.factories = factories
    }

    public mutating func register(_ kind: DeviceKind, factory: @escaping Factory) {
        factories[kind] = factory
    }

    public func makeAdapter(for profile: DeviceProfile) throws -> any RemoteAdapter {
        guard let factory = factories[profile.kind] else {
            throw RemoteError.unsupportedDevice(profile.kind.rawValue)
        }
        return try factory(profile)
    }

    public func canHandle(_ kind: DeviceKind) -> Bool {
        factories[kind] != nil
    }

    /// Adaptadores incluidos en esta versión.
    public static var standard: AdapterRegistry {
        var registry = AdapterRegistry()
        #if canImport(Network)
        registry.register(.roku) { try RokuAdapter(profile: $0) }
        registry.register(.androidTV) { try AndroidTVAdapter(profile: $0) }
        registry.register(.lgWebOS) { try WebOSAdapter(profile: $0) }
        registry.register(.samsungTizen) { try TizenAdapter(profile: $0) }
        #endif
        return registry
    }
}
