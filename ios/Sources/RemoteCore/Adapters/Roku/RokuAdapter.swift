#if canImport(Network)
import Foundation

/// Adaptador Roku: ECP (HTTP 8060) + Wake-on-LAN para Roku TV en reposo profundo.
///
/// ECP no mantiene sesión, así que la "conexión" es un sondeo `device-info` periódico que
/// detecta caídas y alimenta la reconexión automática del gestor.
public actor RokuAdapter: RemoteAdapter {
    public nonisolated let profileID: UUID
    public nonisolated let capabilities: Set<RemoteCapability> = [
        .power, .wake, .volume, .channels, .digits, .navigation, .media, .pointer, .keyboard,
    ]
    public nonisolated let events: AsyncStream<AdapterEvent>

    private let eventSink: AsyncStream<AdapterEvent>.Continuation
    private let client: RokuECPClient
    private let host: String
    private let heartbeatInterval: Duration
    private let wakeTimeout: Duration
    private var profile: DeviceProfile
    private var deviceInfo: RokuDeviceInfo?
    private var state: ConnectionState = .idle
    private var connectTask: Task<Void, any Error>?
    private var heartbeat: Task<Void, Never>?
    private var pointer = PointerAccumulator(threshold: 36)
    private var scroller = PointerAccumulator(threshold: 48)

    public init(
        profile: DeviceProfile,
        heartbeatInterval: Duration = .seconds(8),
        wakeTimeout: Duration = .seconds(20)
    ) throws {
        guard profile.kind == .roku, case .lan(let host, let port) = profile.transport else {
            throw RemoteError.invalidConfiguration("el perfil \(profile.name) no es un Roku en LAN")
        }
        self.profile = profile
        self.profileID = profile.id
        self.host = host
        self.client = RokuECPClient(host: host, port: port)
        self.heartbeatInterval = heartbeatInterval
        self.wakeTimeout = wakeTimeout
        let (stream, sink) = AsyncStream.makeStream(of: AdapterEvent.self, bufferingPolicy: .bufferingNewest(32))
        self.events = stream
        self.eventSink = sink
    }

    deinit {
        heartbeat?.cancel()
        connectTask?.cancel()
        eventSink.finish()
    }

    public nonisolated func supports(_ command: RemoteCommand) -> Bool {
        switch command {
        case .text, .touchMove, .scroll: true
        default: RokuKey(command: command) != nil
        }
    }

    // MARK: - Conexión

    public func connect() async throws {
        if state == .connected { return }
        if let connectTask {
            return try await connectTask.value
        }
        let task = Task { try await self.performConnect() }
        connectTask = task
        defer { connectTask = nil }
        try await withTaskCancellationHandler {
            try await task.value
        } onCancel: {
            task.cancel()
        }
    }

    public func disconnect() async {
        connectTask?.cancel()
        connectTask = nil
        stopHeartbeat()
        pointer.reset()
        scroller.reset()
        transition(to: .idle)
    }

    private func performConnect() async throws {
        transition(to: .connecting)
        do {
            let info = try await client.deviceInfo()
            didReach(info)
        } catch {
            let failure = RemoteError.wrap(error)
            if failure == .cancelled {
                transition(to: .idle)
                throw failure
            }
            guard failure.indicatesUnreachableHost, let mac = profile.macAddress else {
                transition(to: .failed(failure))
                throw failure
            }
            do {
                try await wake(mac: mac)
            } catch {
                let wakeFailure = RemoteError.wrap(error)
                transition(to: wakeFailure == .cancelled ? .idle : .failed(wakeFailure))
                throw wakeFailure
            }
        }
    }

    private func wake(mac: MACAddress) async throws {
        transition(to: .waking)
        try await WakeOnLAN.wake(mac, lastKnownHost: host)
        let clock = ContinuousClock()
        let deadline = clock.now + wakeTimeout
        while clock.now < deadline {
            try Task.checkCancellation()
            if let info = try? await client.deviceInfo(timeout: 1.5) {
                didReach(info)
                return
            }
            try await Task.sleep(for: .seconds(1))
        }
        throw RemoteError.timeout("despertar \(profile.name) por Wake-on-LAN")
    }

    private func didReach(_ info: RokuDeviceInfo) {
        deviceInfo = info
        var updated = profile
        if let mac = info.macAddress { updated.macAddress = mac }
        if updated.model == nil { updated.model = info.modelName }
        if updated.serialNumber == nil { updated.serialNumber = info.serialNumber }
        updated.lastConnectedAt = Date()
        profile = updated
        eventSink.yield(.profileUpdated(updated))
        transition(to: .connected)
        startHeartbeat()
    }

    private func transition(to newState: ConnectionState) {
        guard newState != state else { return }
        state = newState
        eventSink.yield(.stateChanged(newState))
    }

    // MARK: - Heartbeat

    private func startHeartbeat() {
        heartbeat?.cancel()
        let interval = heartbeatInterval
        heartbeat = Task { [weak self] in
            var misses = 0
            while !Task.isCancelled {
                try? await Task.sleep(for: interval)
                guard let self, !Task.isCancelled else { return }
                misses = await self.probe() ? 0 : misses + 1
                if misses >= 2 {
                    await self.connectionLost()
                    return
                }
            }
        }
    }

    private func stopHeartbeat() {
        heartbeat?.cancel()
        heartbeat = nil
    }

    private func probe() async -> Bool {
        do {
            deviceInfo = try await client.deviceInfo(timeout: 2)
            return true
        } catch {
            return false
        }
    }

    private func connectionLost() {
        heartbeat = nil
        guard state == .connected else { return }
        transition(to: .failed(.hostUnreachable(host: host)))
    }

    // MARK: - Comandos

    public func send(_ command: RemoteCommand) async throws {
        switch command {
        case .power, .powerOn:
            if state != .connected {
                try await connect()
            } else if command == .power, let fresh = try? await client.deviceInfo(timeout: 1.5) {
                deviceInfo = fresh
            }
            // Roku TV en "inicio rápido" responde a ECP con la pantalla apagada: si no está en
            // PowerOn se envía PowerOn (determinista); si lo está, Power alterna a reposo.
            let key: RokuKey = command == .powerOn || deviceInfo?.powerMode != "PowerOn" ? .powerOn : .power
            try await press(key)
            return
        default:
            break
        }

        guard state == .connected else { throw RemoteError.notConnected }

        switch command {
        case .text(let text):
            for character in text {
                try await press(.literal(character))
            }
        case .touchMove(let dx, let dy):
            for step in pointer.consume(dx: dx, dy: dy) {
                try await press(RokuKey(step))
            }
        case .scroll(_, let dy):
            for step in scroller.consume(dx: 0, dy: dy) {
                try await press(RokuKey(step))
            }
        default:
            guard let key = RokuKey(command: command) else {
                throw RemoteError.unsupportedCommand(command.wireName)
            }
            try await press(key)
        }
    }

    private func press(_ key: RokuKey) async throws {
        do {
            try await client.keypress(key)
        } catch {
            let failure = RemoteError.wrap(error)
            if failure.indicatesUnreachableHost {
                stopHeartbeat()
                transition(to: .failed(failure))
            }
            throw failure
        }
    }
}
#endif
