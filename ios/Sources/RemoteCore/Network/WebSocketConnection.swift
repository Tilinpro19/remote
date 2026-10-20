#if canImport(Network)
import CryptoKit
import Foundation
import Security

/// WebSocket (`URLSessionWebSocketTask`) con TLS "trust on first use" para TVs con certificado
/// autofirmado (webOS, Tizen).
///
/// Sin huella fijada se acepta cualquier certificado y se recuerda en `peerCertificateSHA256` para
/// que el adaptador la persista; con huella fijada solo se acepta ese mismo certificado hoja.
final class WebSocketConnection: NSObject, URLSessionWebSocketDelegate, @unchecked Sendable {
    private let pinnedSHA256: Data?
    private let host: String
    private let seenLeaf = LockedBox<Data?>(nil)
    private let rejectedPin = LockedBox(false)
    private let openWaiter = LockedBox<CheckedContinuation<Void, any Error>?>(nil)
    private var session: URLSession!
    private var task: URLSessionWebSocketTask!

    private init(url: URL, pinnedSHA256: Data?) {
        self.pinnedSHA256 = pinnedSHA256
        self.host = url.host() ?? url.absoluteString
        super.init()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.allowsCellularAccess = false
        configuration.timeoutIntervalForRequest = 3_600
        session = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
        task = session.webSocketTask(with: url)
    }

    /// Abre el socket y espera al handshake.
    static func open(url: URL, pinnedSHA256: Data?, timeout: TimeInterval = 5) async throws -> WebSocketConnection {
        let socket = WebSocketConnection(url: url, pinnedSHA256: pinnedSHA256)
        do {
            try await withTimeout(.milliseconds(Int(timeout * 1_000)), operation: "conexión a \(socket.host)") {
                try await socket.awaitOpen()
            }
        } catch {
            socket.close()
            throw socket.refine(RemoteError.wrap(error))
        }
        return socket
    }

    /// SHA-256 del certificado hoja aceptado en el handshake (`nil` en `ws://`).
    var peerCertificateSHA256: Data? { seenLeaf.value }

    func send(_ text: String) async throws {
        do {
            try await task.send(.string(text))
        } catch {
            throw translate(error)
        }
    }

    /// Siguiente mensaje de texto; lanza cuando el socket se cierra.
    func receive() async throws -> String {
        do {
            switch try await task.receive() {
            case .string(let text): return text
            case .data(let data): return String(decoding: data, as: UTF8.self)
            @unknown default: return ""
            }
        } catch {
            throw translate(error)
        }
    }

    func close() {
        task.cancel(with: .normalClosure, reason: nil)
        session.invalidateAndCancel()
        resumeOpen(throwing: RemoteError.cancelled)
    }

    // MARK: - Apertura

    private func awaitOpen() async throws {
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
                openWaiter.mutate { $0 = continuation }
                task.resume()
            }
        } onCancel: {
            self.resumeOpen(throwing: RemoteError.cancelled)
        }
    }

    private func resumeOpen(throwing error: (any Error)? = nil) {
        guard let waiter = openWaiter.mutate({ value -> CheckedContinuation<Void, any Error>? in
            defer { value = nil }
            return value
        }) else { return }
        if let error { waiter.resume(throwing: error) } else { waiter.resume() }
    }

    private func refine(_ error: RemoteError) -> RemoteError {
        rejectedPin.value ? .certificateMismatch : error
    }

    private func translate(_ error: any Error) -> RemoteError {
        if let remote = error as? RemoteError { return remote }
        let nsError = error as NSError
        guard nsError.domain == NSURLErrorDomain else { return .wrap(error) }
        switch nsError.code {
        case NSURLErrorTimedOut:
            return .timeout("conexión a \(host)")
        case NSURLErrorCannotConnectToHost, NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed:
            return .connectionRefused(host: host)
        case NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost:
            return .hostUnreachable(host: host)
        case NSURLErrorCancelled:
            return refine(.cancelled)
        case NSURLErrorServerCertificateUntrusted, NSURLErrorServerCertificateHasBadDate,
             NSURLErrorServerCertificateHasUnknownRoot, NSURLErrorServerCertificateNotYetValid,
             NSURLErrorSecureConnectionFailed, NSURLErrorClientCertificateRejected:
            return refine(.tlsFailure("URLError \(nsError.code) con \(host)"))
        default:
            return .network("URLError \(nsError.code) con \(host)")
        }
    }

    // MARK: - URLSessionWebSocketDelegate

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping @Sendable (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust,
              let leaf = TLSTrust.leafDER(of: trust) else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        let digest = Data(SHA256.hash(data: leaf))
        if let pinnedSHA256, pinnedSHA256 != digest {
            rejectedPin.mutate { $0 = true }
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        seenLeaf.mutate { $0 = digest }
        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        resumeOpen()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: (any Error)?) {
        resumeOpen(throwing: error.map { self.translate($0) } ?? RemoteError.network("\(host) cerró la conexión"))
    }
}
#endif
