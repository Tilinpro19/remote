#if canImport(Network)
import CryptoKit
import Foundation
@preconcurrency import Network

/// Socket TCP/TLS persistente para protocolos de TV (Android TV Remote v2, Samsung, webOS).
public actor TCPConnection {
    public enum Security: Sendable {
        case plaintext
        case tls(TLSTrustPolicy, clientIdentity: TLSClientIdentity? = nil)
    }

    public let host: String
    public let port: UInt16
    private let security: Security
    private let queue: DispatchQueue
    private let observedLeaf = LockedBox<Data?>(nil)
    private var connection: NWConnection?

    /// Emite cuando una conexión ya establecida se pierde (no tras `close()`).
    public nonisolated let terminations: AsyncStream<RemoteError>
    private let terminationSink: AsyncStream<RemoteError>.Continuation

    public init(host: String, port: UInt16, security: Security) {
        self.host = host
        self.port = port
        self.security = security
        self.queue = DispatchQueue(label: "remote.tcp.\(host).\(port)")
        let (stream, sink) = AsyncStream.makeStream(of: RemoteError.self, bufferingPolicy: .bufferingNewest(4))
        self.terminations = stream
        self.terminationSink = sink
    }

    deinit {
        connection?.cancel()
        terminationSink.finish()
    }

    public var isOpen: Bool { connection?.state == .ready }

    /// SHA-256 del certificado hoja presentado por el servidor en el último handshake.
    public var peerCertificateSHA256: Data? { observedLeaf.value.map { Data(SHA256.hash(data: $0)) } }

    /// Certificado hoja (DER) presentado por el servidor en el último handshake.
    public var peerCertificateDER: Data? { observedLeaf.value }

    public func open(timeout: TimeInterval = 5) async throws {
        close()
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            throw RemoteError.invalidConfiguration("puerto \(port)")
        }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: makeParameters())
        self.connection = connection
        do {
            try await connection.startAndAwaitReady(on: queue, timeout: timeout, host: host)
        } catch {
            if self.connection === connection { self.connection = nil }
            throw refine(RemoteError.wrap(error))
        }
        let sink = terminationSink
        let host = host
        connection.stateUpdateHandler = { [weak connection] state in
            switch state {
            case .failed(let error), .waiting(let error):
                sink.yield(RemoteError.from(error, host: host, path: connection?.currentPath))
                connection?.cancel()
            default:
                break
            }
        }
    }

    public func send(_ data: Data) async throws {
        guard let connection, connection.state == .ready else { throw RemoteError.notConnected }
        try await connection.sendAsync(data, host: host)
    }

    /// Lee entre `minimum` y `maximum` bytes; lanza si el par cierra la conexión.
    public func receive(minimum: Int = 1, maximum: Int = 65_536) async throws -> Data {
        guard let connection else { throw RemoteError.notConnected }
        return try await connection.receiveAsync(minimum: minimum, maximum: maximum, host: host)
    }

    /// Lee exactamente `count` bytes (tramas con prefijo de longitud).
    public func receive(exactly count: Int) async throws -> Data {
        var buffer = Data()
        buffer.reserveCapacity(count)
        while buffer.count < count {
            let chunk = try await receive(minimum: 1, maximum: count - buffer.count)
            buffer.append(chunk)
        }
        return buffer
    }

    public func close() {
        guard let connection else { return }
        connection.stateUpdateHandler = nil
        connection.cancel()
        self.connection = nil
    }

    private func makeParameters() -> NWParameters {
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        tcp.connectionTimeout = 5
        tcp.enableKeepalive = true
        tcp.keepaliveIdle = 10
        tcp.keepaliveInterval = 5
        tcp.keepaliveCount = 3

        let parameters: NWParameters
        switch security {
        case .plaintext:
            parameters = NWParameters(tls: nil, tcp: tcp)
        case .tls(let policy, let clientIdentity):
            let tls = TLSTrust.makeOptions(
                policy: policy,
                clientIdentity: clientIdentity,
                observedLeaf: observedLeaf,
                queue: queue
            )
            parameters = NWParameters(tls: tls, tcp: tcp)
        }
        // TVs locales: nunca salir por datos móviles ni por VPN.
        parameters.prohibitedInterfaceTypes = [.cellular]
        parameters.serviceClass = .responsiveData
        return parameters
    }

    /// Distingue un certificado cambiado de un fallo TLS genérico.
    private func refine(_ error: RemoteError) -> RemoteError {
        guard case .tlsFailure = error,
              case .tls(.pinned(let expected), _) = security,
              let observed = peerCertificateSHA256,
              observed != expected else { return error }
        return .certificateMismatch
    }
}
#endif
