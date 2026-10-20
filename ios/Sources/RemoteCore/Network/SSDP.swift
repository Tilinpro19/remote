import Foundation

public struct SSDPResponse: Sendable, Hashable {
    public let location: URL
    public let searchTarget: String
    public let usn: String
    public let server: String?
    public let headers: [String: String]
}

/// Construcción y parseo de mensajes SSDP (UPnP 1.1 sobre UDP 239.255.255.250:1900).
public enum SSDPMessage {
    public static let multicastHost = "239.255.255.250"
    public static let multicastPort: UInt16 = 1900

    public static func searchRequest(target: String, mx: Int = 2) -> Data {
        let message = [
            "M-SEARCH * HTTP/1.1",
            "HOST: \(multicastHost):\(multicastPort)",
            "MAN: \"ssdp:discover\"",
            "MX: \(max(1, min(mx, 5)))",
            "ST: \(target)",
            "", "",
        ].joined(separator: "\r\n")
        return Data(message.utf8)
    }

    /// Acepta respuestas `HTTP/1.1 200 OK` a M-SEARCH y anuncios `NOTIFY * HTTP/1.1` (ssdp:alive).
    public static func parse(_ data: Data) -> SSDPResponse? {
        guard let text = String(data: data, encoding: .utf8) else { return nil }
        let lines = text.split(whereSeparator: \.isNewline)
        guard let status = lines.first?.uppercased(),
              status.hasPrefix("HTTP/1.1 200") || status.hasPrefix("NOTIFY * HTTP/1.1") else { return nil }

        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = line[..<colon].trimmingCharacters(in: .whitespaces).uppercased()
            let value = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
            headers[key] = value
        }
        if headers["NTS"] == "ssdp:byebye" { return nil }

        guard let rawLocation = headers["LOCATION"],
              let location = URL(string: rawLocation),
              let target = headers["ST"] ?? headers["NT"],
              let usn = headers["USN"] else { return nil }

        return SSDPResponse(location: location, searchTarget: target, usn: usn, server: headers["SERVER"], headers: headers)
    }
}

#if canImport(Network)
@preconcurrency import Network

/// Búsqueda SSDP activa. Requiere `com.apple.developer.networking.multicast` en iOS 14+.
public enum SSDPDiscovery {
    public static func search(target: String, timeout: TimeInterval = 3, mx: Int = 2) async throws -> [SSDPResponse] {
        guard let port = NWEndpoint.Port(rawValue: SSDPMessage.multicastPort) else {
            throw RemoteError.invalidConfiguration("puerto SSDP")
        }
        let group = try NWMulticastGroup(for: [.hostPort(host: NWEndpoint.Host(SSDPMessage.multicastHost), port: port)])
        let parameters = NWParameters.udp
        parameters.allowLocalEndpointReuse = true
        parameters.prohibitedInterfaceTypes = [.cellular]

        let connectionGroup = NWConnectionGroup(with: group, using: parameters)
        let queue = DispatchQueue(label: "remote.ssdp")
        let collected = LockedBox<[String: SSDPResponse]>([:])
        let sendFailure = LockedBox<RemoteError?>(nil)
        let gate = ResumeGate()
        let request = SSDPMessage.searchRequest(target: target, mx: mx)

        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[SSDPResponse], any Error>) in
                connectionGroup.setReceiveHandler(maximumMessageSize: 8192, rejectOversizedMessages: true) { _, content, _ in
                    guard let content, let response = SSDPMessage.parse(content) else { return }
                    guard target == "ssdp:all" || response.searchTarget == target else { return }
                    collected.mutate { $0[response.usn] = response }
                }

                connectionGroup.stateUpdateHandler = { state in
                    switch state {
                    case .ready:
                        // UDP sin garantía de entrega: tres M-SEARCH espaciados, como recomienda UPnP.
                        for delay in [0.0, 0.35, 0.9] {
                            queue.asyncAfter(deadline: .now() + delay) {
                                connectionGroup.send(content: request) { error in
                                    guard let error else { return }
                                    sendFailure.mutate {
                                        $0 = RemoteError.from(error, host: SSDPMessage.multicastHost, path: nil)
                                    }
                                }
                            }
                        }
                    case .waiting(let error), .failed(let error):
                        guard gate.claim() else { return }
                        connectionGroup.cancel()
                        continuation.resume(throwing: RemoteError.from(error, host: SSDPMessage.multicastHost, path: nil))
                    case .cancelled:
                        if gate.claim() { continuation.resume(throwing: RemoteError.cancelled) }
                    default:
                        break
                    }
                }

                queue.asyncAfter(deadline: .now() + timeout) {
                    guard gate.claim() else { return }
                    connectionGroup.cancel()
                    let responses = Array(collected.value.values)
                    if responses.isEmpty, let failure = sendFailure.value {
                        continuation.resume(throwing: failure)
                    } else {
                        continuation.resume(returning: responses)
                    }
                }

                connectionGroup.start(queue: queue)
            }
        } onCancel: {
            connectionGroup.cancel()
        }
    }
}
#endif
