#if canImport(Network)
import Foundation

/// Escáner híbrido: mDNS continuo (Android TV, webOS, Tizen, AirPlay) + ronda SSDP para Roku.
public enum DeviceDiscoveryService {
    public static let rokuSearchTarget = "roku:ecp"

    public static func discover(ssdpTimeout: TimeInterval = 4) -> AsyncThrowingStream<DiscoverySnapshot, any Error> {
        AsyncThrowingStream { continuation in
            let state = LockedBox(MergeState())

            let task = Task {
                await withTaskGroup(of: Void.self) { group in
                    group.addTask {
                        do {
                            for try await devices in BonjourDiscovery.services(ofTypes: BonjourServiceType.discoverable) {
                                continuation.yield(state.mutate { $0.bonjour = devices; return $0.snapshot })
                            }
                        } catch {
                            continuation.finish(throwing: error)
                        }
                    }
                    group.addTask {
                        do {
                            let responses = try await SSDPDiscovery.search(
                                target: DeviceDiscoveryService.rokuSearchTarget,
                                timeout: ssdpTimeout
                            )
                            let devices = responses.compactMap(DeviceDiscoveryService.rokuDevice(from:))
                            continuation.yield(state.mutate { $0.ssdp = devices; return $0.snapshot })
                        } catch {
                            let failure = RemoteError.wrap(error)
                            guard failure != .cancelled else { return }
                            continuation.yield(state.mutate { $0.ssdpError = failure; return $0.snapshot })
                        }
                    }
                }
                continuation.finish()
            }

            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Convierte un candidato en perfil persistible. Para Roku consulta `device-info` y aprende la MAC.
    public static func makeProfile(for device: DiscoveredDevice) async throws -> DeviceProfile {
        switch device.source {
        case .ssdp(let location, _):
            guard let host = location.host(percentEncoded: false) else {
                throw RemoteError.invalidResponse("LOCATION SSDP sin host: \(location)")
            }
            let port = UInt16(clamping: location.port ?? Int(DeviceKind.roku.defaultControlPort ?? 8060))
            var profile = DeviceProfile(name: device.name, kind: .roku, transport: .lan(host: host, port: port))
            let info = try await RokuECPClient(host: host, port: port).deviceInfo()
            profile.name = info.friendlyName ?? profile.name
            profile.macAddress = info.macAddress
            profile.model = info.modelName
            profile.serialNumber = info.serialNumber
            return profile

        case .bonjour(let name, let type, let domain, _):
            guard let kind = device.kind else {
                throw RemoteError.unsupportedDevice("servicio \(type)")
            }
            let resolved = try await BonjourDiscovery.resolve(name: name, type: type, domain: domain)
            let port = kind.defaultControlPort ?? resolved.port
            return DeviceProfile(name: device.name, kind: kind, transport: .lan(host: resolved.host, port: port))
        }
    }

    static func rokuDevice(from response: SSDPResponse) -> DiscoveredDevice? {
        guard response.searchTarget == rokuSearchTarget else { return nil }
        // USN: uuid:roku:ecp:<serial>
        let serial = response.usn.split(separator: ":").last.map(String.init) ?? response.usn
        return DiscoveredDevice(
            id: "ssdp:\(response.usn)",
            name: "Roku \(serial)",
            kind: .roku,
            source: .ssdp(location: response.location, usn: response.usn)
        )
    }
}

private struct MergeState: Sendable {
    var bonjour: [DiscoveredDevice] = []
    var ssdp: [DiscoveredDevice] = []
    var ssdpError: RemoteError?

    var snapshot: DiscoverySnapshot {
        DiscoverySnapshot(devices: (ssdp + bonjour).curated(), ssdpError: ssdpError)
    }
}
#endif
