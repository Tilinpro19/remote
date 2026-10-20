#if canImport(Network)
import Foundation
@preconcurrency import Network

/// kDNSServiceErr_PolicyDenied: el usuario denegó el permiso de red local.
let dnsServicePolicyDenied: Int32 = -65570

extension RemoteError {
    static func from(_ error: NWError, host: String, path: NWPath?) -> RemoteError {
        if path?.unsatisfiedReason == .localNetworkDenied { return .localNetworkDenied }
        switch error {
        case .posix(let code):
            switch code {
            case .ECONNREFUSED:
                return .connectionRefused(host: host)
            case .EHOSTUNREACH, .ENETUNREACH, .EHOSTDOWN, .ENETDOWN:
                return .hostUnreachable(host: host)
            case .ETIMEDOUT:
                return .timeout("conexión a \(host)")
            case .ECANCELED:
                return .cancelled
            case .EPERM, .EACCES:
                return .permissionDenied(
                    "iOS bloqueó la operación de red hacia \(host) (multicast/broadcast requiere com.apple.developer.networking.multicast)."
                )
            default:
                return .network("POSIX \(code.rawValue) hacia \(host)")
            }
        case .dns(let code):
            return Int32(code) == dnsServicePolicyDenied ? .localNetworkDenied : .network("DNS-SD \(code) para \(host)")
        case .tls(let status):
            return .tlsFailure("OSStatus \(status) con \(host)")
        @unknown default:
            return .network("\(error) hacia \(host)")
        }
    }
}

extension NWEndpoint.Host {
    /// Representación literal apta para URLs (sin sufijo de interfaz IPv6).
    var literal: String {
        switch self {
        case .ipv4(let address):
            return "\(address)"
        case .ipv6(let address):
            let text = "\(address)"
            return text.split(separator: "%").first.map(String.init) ?? text
        case .name(let name, _):
            return name
        @unknown default:
            return "\(self)"
        }
    }
}

extension NWConnection {
    /// Arranca la conexión y espera `.ready`. Falla rápido en `.waiting` (habitual en LAN cuando
    /// el TV está apagado o el permiso de red local está denegado) en lugar de esperar indefinidamente.
    func startAndAwaitReady(on queue: DispatchQueue, timeout: TimeInterval, host: String) async throws {
        let gate = ResumeGate()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
                self.stateUpdateHandler = { [weak self] state in
                    switch state {
                    case .ready:
                        if gate.claim() { continuation.resume() }
                    case .waiting(let error):
                        guard gate.claim() else { return }
                        let failure = RemoteError.from(error, host: host, path: self?.currentPath)
                        self?.cancel()
                        continuation.resume(throwing: failure)
                    case .failed(let error):
                        guard gate.claim() else { return }
                        continuation.resume(throwing: RemoteError.from(error, host: host, path: self?.currentPath))
                    case .cancelled:
                        if gate.claim() { continuation.resume(throwing: RemoteError.cancelled) }
                    case .setup, .preparing:
                        break
                    @unknown default:
                        break
                    }
                }
                queue.asyncAfter(deadline: .now() + timeout) { [weak self] in
                    guard gate.claim() else { return }
                    self?.cancel()
                    continuation.resume(throwing: RemoteError.timeout("conexión a \(host)"))
                }
                self.start(queue: queue)
            }
        } onCancel: {
            self.cancel()
        }
    }

    func sendAsync(_ data: Data, host: String) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
            send(content: data, completion: .contentProcessed { [weak self] error in
                if let error {
                    continuation.resume(throwing: RemoteError.from(error, host: host, path: self?.currentPath))
                } else {
                    continuation.resume()
                }
            })
        }
    }

    func receiveAsync(minimum: Int, maximum: Int, host: String) async throws -> Data {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, any Error>) in
            receive(minimumIncompleteLength: minimum, maximumLength: maximum) { [weak self] content, _, isComplete, error in
                if let error {
                    continuation.resume(throwing: RemoteError.from(error, host: host, path: self?.currentPath))
                } else if let content, !content.isEmpty {
                    continuation.resume(returning: content)
                } else if isComplete {
                    continuation.resume(throwing: RemoteError.network("\(host) cerró la conexión"))
                } else {
                    continuation.resume(returning: Data())
                }
            }
        }
    }
}
#endif
