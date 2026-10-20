import Foundation

public struct PairingRequest: Sendable, Equatable {
    public enum Method: Sendable, Equatable {
        /// El TV muestra un PIN que el usuario introduce en el móvil.
        case pin(length: Int)
        /// El usuario acepta un diálogo en el TV.
        case confirmOnScreen
    }

    public let method: Method
    public let message: String

    public init(method: Method, message: String) {
        self.method = method
        self.message = message
    }
}

public enum ConnectionState: Sendable, Equatable {
    case idle
    case connecting
    /// Wake-on-LAN enviado; esperando a que el equipo responda.
    case waking
    case awaitingPairing(PairingRequest)
    case connected
    case reconnecting(attempt: Int)
    case failed(RemoteError)

    public var isConnected: Bool { self == .connected }

    public var isBusy: Bool {
        switch self {
        case .connecting, .waking, .reconnecting, .awaitingPairing: true
        default: false
        }
    }
}

/// Solicitud para abrir el teclado nativo cuando el TV enfoca un campo de texto.
public struct KeyboardRequest: Sendable, Equatable, Identifiable {
    public let id: UUID
    public let initialText: String

    public init(id: UUID = UUID(), initialText: String) {
        self.id = id
        self.initialText = initialText
    }
}
