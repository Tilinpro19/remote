#if canImport(Network)
import Foundation

/// Roku External Control Protocol: REST sobre HTTP, puerto 8060.
public struct RokuECPClient: Sendable {
    public let host: String
    public let port: UInt16
    private let http: LocalHTTPClient

    public init(host: String, port: UInt16 = 8060, http: LocalHTTPClient = LocalHTTPClient(timeout: 3)) {
        self.host = host
        self.port = port
        self.http = http
    }

    public func keypress(_ key: RokuKey) async throws {
        try await post("keypress/\(key.path)")
    }

    public func deviceInfo(timeout: TimeInterval = 3) async throws -> RokuDeviceInfo {
        var request = try makeRequest(path: "query/device-info", method: "GET")
        request.timeoutInterval = timeout
        let (data, response) = try await http.send(request)
        try validate(response)
        return try RokuDeviceInfo(xml: data)
    }

    private func post(_ path: String) async throws {
        let request = try makeRequest(path: path, method: "POST")
        let (_, response) = try await http.send(request)
        try validate(response)
    }

    private func makeRequest(path: String, method: String) throws -> URLRequest {
        let authority = host.contains(":") ? "[\(host)]" : host
        guard let url = URL(string: "http://\(authority):\(port)/\(path)") else {
            throw RemoteError.invalidConfiguration("URL ECP inválida para \(host)")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        if method == "POST" {
            request.httpBody = Data()
            request.setValue("0", forHTTPHeaderField: "Content-Length")
        }
        return request
    }

    private func validate(_ response: HTTPURLResponse) throws {
        switch response.statusCode {
        case 200..<300:
            return
        case 403:
            throw RemoteError.permissionDenied(
                "El Roku bloquea el control por red. Actívalo en Ajustes › Sistema › Configuración avanzada › Control por apps móviles › Acceso a la red: Predeterminado."
            )
        default:
            throw RemoteError.httpStatus(response.statusCode)
        }
    }
}
#endif
