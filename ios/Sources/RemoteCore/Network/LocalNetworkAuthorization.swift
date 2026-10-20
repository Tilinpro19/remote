#if canImport(Network)
import Foundation
@preconcurrency import Network

/// iOS no expone una API para consultar el permiso de red local. Se publica un servicio Bonjour propio
/// y se navega su tipo: verlo implica permiso concedido; `PolicyDenied` implica denegado.
/// La primera llamada muestra el diálogo del sistema.
public enum LocalNetworkAuthorization {
    public enum Status: Sendable, Equatable {
        case granted
        case denied
        /// El usuario no respondió dentro del plazo o el sondeo no pudo arrancar.
        case undetermined
    }

    public static func request(timeout: TimeInterval = 30) async -> Status {
        let queue = DispatchQueue(label: "remote.lan-authorization")
        let serviceName = "UR-\(UUID().uuidString.prefix(8))"
        let listener: NWListener
        do {
            listener = try NWListener(using: .tcp)
        } catch {
            return .undetermined
        }
        listener.service = NWListener.Service(name: serviceName, type: BonjourServiceType.preflight)
        listener.newConnectionHandler = { $0.cancel() }

        let browser = NWBrowser(for: .bonjour(type: BonjourServiceType.preflight, domain: nil), using: NWParameters())
        let gate = ResumeGate()

        return await withCheckedContinuation { (continuation: CheckedContinuation<Status, Never>) in
            let finish: @Sendable (Status) -> Void = { status in
                guard gate.claim() else { return }
                browser.cancel()
                listener.cancel()
                continuation.resume(returning: status)
            }

            listener.stateUpdateHandler = { state in
                if case .failed = state { finish(.undetermined) }
            }
            browser.stateUpdateHandler = { state in
                switch state {
                case .waiting(let error):
                    if case .dns(let code) = error, Int32(code) == dnsServicePolicyDenied { finish(.denied) }
                case .failed(let error):
                    if case .dns(let code) = error, Int32(code) == dnsServicePolicyDenied {
                        finish(.denied)
                    } else {
                        finish(.undetermined)
                    }
                default:
                    break
                }
            }
            browser.browseResultsChangedHandler = { results, _ in
                let foundSelf = results.contains { result in
                    if case .service(let name, _, _, _) = result.endpoint { return name == serviceName }
                    return false
                }
                if foundSelf { finish(.granted) }
            }

            queue.asyncAfter(deadline: .now() + timeout) { finish(.undetermined) }
            listener.start(queue: queue)
            browser.start(queue: queue)
        }
    }
}
#endif
