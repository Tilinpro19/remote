import Foundation

/// Magic Packet: 6 × 0xFF seguidos de 16 repeticiones de la MAC (102 bytes).
public enum WakeOnLANPacket {
    public static func make(for mac: MACAddress) -> Data {
        var packet = Data(repeating: 0xFF, count: 6)
        packet.reserveCapacity(102)
        for _ in 0..<16 { packet.append(contentsOf: mac.bytes) }
        return packet
    }
}

#if canImport(Network)
import Darwin
@preconcurrency import Network

/// Envío de Wake-on-LAN.
///
/// Network.framework no admite broadcast UDP (`NWConnection` a 255.255.255.255 no entrega), así que el
/// broadcast va por socket BSD con `SO_BROADCAST`; además se envía unicast por `NWConnection` a la última
/// IP conocida, que despierta equipos cuya entrada ARP sigue en la caché del router.
/// En iOS 14+ el broadcast exige `com.apple.developer.networking.multicast`.
public enum WakeOnLAN {
    private static let queue = DispatchQueue(label: "remote.wol")

    public static func wake(
        _ mac: MACAddress,
        lastKnownHost: String? = nil,
        ports: [UInt16] = [9, 7],
        bursts: Int = 3
    ) async throws {
        let packet = WakeOnLANPacket.make(for: mac)
        let targets = Array(Set(LocalInterfaces.ipv4BroadcastAddresses() + ["255.255.255.255"])).sorted()
        var delivered = false
        var lastFailure: RemoteError?

        for burst in 0..<max(bursts, 1) {
            for target in targets {
                for port in ports {
                    do {
                        try BroadcastSocket.send(packet, to: target, port: port)
                        delivered = true
                    } catch {
                        lastFailure = .wrap(error)
                    }
                }
            }
            if let lastKnownHost, let port = ports.first {
                do {
                    try await sendUnicast(packet, host: lastKnownHost, port: port)
                    delivered = true
                } catch {
                    lastFailure = .wrap(error)
                }
            }
            if burst < bursts - 1 {
                try await Task.sleep(for: .milliseconds(150))
            }
        }
        if !delivered { throw lastFailure ?? RemoteError.wakeFailed }
    }

    static func sendUnicast(_ packet: Data, host: String, port: UInt16) async throws {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            throw RemoteError.invalidConfiguration("puerto WoL \(port)")
        }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .udp)
        defer { connection.cancel() }
        try await connection.startAndAwaitReady(on: queue, timeout: 2, host: host)
        try await connection.sendAsync(packet, host: host)
    }
}

enum BroadcastSocket {
    static func send(_ packet: Data, to address: String, port: UInt16) throws {
        let descriptor = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard descriptor >= 0 else { throw RemoteError.network("socket() errno \(errno)") }
        defer { Darwin.close(descriptor) }

        var enable: Int32 = 1
        guard setsockopt(descriptor, SOL_SOCKET, SO_BROADCAST, &enable, socklen_t(MemoryLayout<Int32>.size)) == 0 else {
            throw RemoteError.network("SO_BROADCAST errno \(errno)")
        }

        var destination = sockaddr_in()
        destination.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        destination.sin_family = sa_family_t(AF_INET)
        destination.sin_port = in_port_t(port).bigEndian
        guard inet_pton(AF_INET, address, &destination.sin_addr) == 1 else {
            throw RemoteError.invalidConfiguration("dirección broadcast \(address)")
        }

        let sent = packet.withUnsafeBytes { buffer -> Int in
            withUnsafePointer(to: &destination) { pointer in
                pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { address in
                    sendto(descriptor, buffer.baseAddress, buffer.count, 0, address, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }
        }
        guard sent == packet.count else {
            let code = errno
            if code == EHOSTUNREACH || code == EPERM || code == EACCES {
                throw RemoteError.permissionDenied(
                    "Broadcast Wake-on-LAN bloqueado (errno \(code)): la app necesita com.apple.developer.networking.multicast."
                )
            }
            throw RemoteError.network("sendto \(address):\(port) errno \(code)")
        }
    }
}

enum LocalInterfaces {
    /// Broadcast dirigido de cada interfaz IPv4 activa (p. ej. 192.168.1.255 para en0).
    static func ipv4BroadcastAddresses() -> [String] {
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0, let first = head else { return [] }
        defer { freeifaddrs(head) }

        var results: [String] = []
        for pointer in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let interface = pointer.pointee
            let flags = Int32(interface.ifa_flags)
            guard flags & IFF_UP != 0,
                  flags & IFF_LOOPBACK == 0,
                  flags & IFF_BROADCAST != 0,
                  let address = interface.ifa_addr,
                  address.pointee.sa_family == sa_family_t(AF_INET),
                  let netmask = interface.ifa_netmask else { continue }

            let ip = address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr.s_addr }
            let mask = netmask.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr.s_addr }
            var broadcast = in_addr(s_addr: ip | ~mask)
            var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            guard inet_ntop(AF_INET, &broadcast, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else { continue }
            let bytes = buffer.prefix { $0 != 0 }.map { UInt8(bitPattern: $0) }
            results.append(String(decoding: bytes, as: UTF8.self))
        }
        return results
    }
}
#endif
