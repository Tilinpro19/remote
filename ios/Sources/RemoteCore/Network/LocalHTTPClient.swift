#if canImport(Network)
import Foundation

/// Cliente HTTP para APIs REST de la LAN (Roku ECP, metadatos Samsung). Sin caché, sin cookies
/// y con timeouts cortos: un TV que no responde en 3 s se trata como inaccesible.
public struct LocalHTTPClient: Sendable {
    private let session: URLSession

    public init(timeout: TimeInterval = 3) {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = timeout
        configuration.timeoutIntervalForResource = timeout * 2
        configuration.waitsForConnectivity = false
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.httpMaximumConnectionsPerHost = 2
        configuration.allowsCellularAccess = false
        session = URLSession(configuration: configuration)
    }

    public func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let host = request.url?.host() ?? "dispositivo"
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw RemoteError.invalidResponse("respuesta no HTTP de \(host)")
            }
            return (data, http)
        } catch let error as RemoteError {
            throw error
        } catch let error as URLError {
            throw Self.map(error, host: host)
        } catch {
            throw RemoteError.wrap(error)
        }
    }

    static func map(_ error: URLError, host: String) -> RemoteError {
        switch error.code {
        case .timedOut:
            .timeout("HTTP a \(host)")
        case .cannotConnectToHost:
            .connectionRefused(host: host)
        case .cannotFindHost, .networkConnectionLost, .notConnectedToInternet, .dnsLookupFailed:
            .hostUnreachable(host: host)
        case .cancelled:
            .cancelled
        case .appTransportSecurityRequiresSecureConnection:
            .permissionDenied("ATS bloqueó HTTP hacia \(host): falta NSAllowsLocalNetworking en Info.plist.")
        default:
            .network("URLError \(error.code.rawValue) con \(host)")
        }
    }
}
#endif
