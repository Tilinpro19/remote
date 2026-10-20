import Foundation

public enum RemoteError: Error, Sendable, Equatable, LocalizedError {
    case notConnected
    case unsupportedCommand(String)
    case unsupportedDevice(String)
    case invalidConfiguration(String)
    case timeout(String)
    case connectionRefused(host: String)
    case hostUnreachable(host: String)
    case network(String)
    case httpStatus(Int)
    case invalidResponse(String)
    case tlsFailure(String)
    case certificateMismatch
    case localNetworkDenied
    case permissionDenied(String)
    case wakeFailed
    case invalidMACAddress(String)
    case invalidProntoCode(String)
    case pairingRejected
    case invalidCode(String)
    case storage(String)
    case cancelled

    // Los mensajes van tal cual a la UI: breves y sin texto crudo de la plataforma; el detalle
    // asociado (OSStatus, host, POSIX) queda en el caso para depuración.
    public var errorDescription: String? {
        switch self {
        case .notConnected: "No hay conexión con el dispositivo."
        case .unsupportedCommand(let name): "El dispositivo no admite \(name)."
        case .unsupportedDevice(let kind): "No hay adaptador disponible para \(kind)."
        case .invalidConfiguration(let detail): "Configuración inválida: \(detail)."
        case .timeout: "Tiempo de espera agotado."
        case .connectionRefused, .hostUnreachable: "TV apagada o fuera de red."
        case .network: "Se perdió la conexión con el TV."
        case .httpStatus(let code): "El dispositivo respondió HTTP \(code)."
        case .invalidResponse(let detail): "Respuesta inválida: \(detail)."
        case .tlsFailure: "Error de vinculación SSL (toca Reintentar o vuelve a vincular)."
        case .certificateMismatch: "El certificado del TV cambió desde el emparejamiento. Vuelve a emparejar."
        case .localNetworkDenied: "Permiso de red local denegado. Actívalo en Ajustes › Privacidad › Red local."
        case .permissionDenied(let detail): detail
        case .wakeFailed: "No se pudo enviar el paquete Wake-on-LAN."
        case .invalidMACAddress(let value): "Dirección MAC inválida: \(value)."
        case .invalidProntoCode(let detail): "Código Pronto inválido: \(detail)."
        case .pairingRejected: "El emparejamiento fue rechazado en el TV."
        case .invalidCode(let detail): detail
        case .storage(let detail): "Error de almacenamiento: \(detail)."
        case .cancelled: "Operación cancelada."
        }
    }

    /// Fallos que justifican un reintento automático de conexión.
    public var isRetryable: Bool {
        switch self {
        case .notConnected, .timeout, .connectionRefused, .hostUnreachable, .network:
            true
        case .httpStatus(let code):
            code >= 500
        default:
            false
        }
    }

    /// El host no está accesible (apagado, en reposo profundo o fuera de la red).
    public var indicatesUnreachableHost: Bool {
        switch self {
        case .timeout, .connectionRefused, .hostUnreachable, .network: true
        default: false
        }
    }

    /// Fallo de la vinculación TLS: la UI ofrece «Vincular» para volver a emparejar.
    public var requiresRepairing: Bool {
        switch self {
        case .tlsFailure, .certificateMismatch, .pairingRejected: true
        default: false
        }
    }

    public static func wrap(_ error: any Error) -> RemoteError {
        if let remote = error as? RemoteError { return remote }
        if error is CancellationError { return .cancelled }
        return .network(String(describing: error))
    }
}
