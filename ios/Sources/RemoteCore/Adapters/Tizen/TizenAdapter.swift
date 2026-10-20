#if canImport(Network)
import Foundation

/// Samsung Tizen (canal WebSocket `samsung.remote.control`, wss://host:8002).
///
/// La primera conexión muestra «Permitir» en el TV; al aceptarlo el TV entrega un token que se
/// guarda como `pairingToken` y se envía en adelante para conectar sin aviso.
public actor TizenAdapter: RemoteAdapter {
    public nonisolated let profileID: UUID
    public nonisolated let capabilities = Set(RemoteCapability.allCases)
    public nonisolated let events: AsyncStream<AdapterEvent>

    private static let legacyPort: UInt16 = 8001
    private static let clientName = "UniversalRemote"
    private static let authFastPath: Duration = .seconds(3)
    private static let wakePollAttempts = 20

    private let eventSink: AsyncStream<AdapterEvent>.Continuation
    private let host: String
    private let port: UInt16
    private let mutex = AsyncMutex()
    private var profile: DeviceProfile
    private var state: ConnectionState = .idle
    private var socket: WebSocketConnection?
    private var reader: Task<Void, Never>?
    private var authorized = false
    private var authFailure: RemoteError?

    public init(profile: DeviceProfile) throws {
        guard profile.kind == .samsungTizen, case .lan(let host, let port) = profile.transport else {
            throw RemoteError.invalidConfiguration("el perfil \(profile.name) no es un TV Samsung en LAN")
        }
        self.profile = profile
        self.profileID = profile.id
        self.host = host
        self.port = port
        let (stream, sink) = AsyncStream.makeStream(of: AdapterEvent.self, bufferingPolicy: .bufferingNewest(32))
        self.events = stream
        self.eventSink = sink
    }

    deinit {
        reader?.cancel()
        eventSink.finish()
    }

    public nonisolated func supports(_ command: RemoteCommand) -> Bool {
        switch command {
        case .text, .touchMove, .touchTap, .powerOn: true
        default: Self.key(for: command) != nil
        }
    }

    // MARK: - API

    public func connect() async throws {
        await mutex.acquire()
        defer { mutex.release() }
        try await connectLocked()
    }

    public func disconnect() async {
        teardown(.idle)
    }

    public func send(_ command: RemoteCommand) async throws {
        await mutex.acquire()
        defer { mutex.release() }

        if command.canWakeDevice, state != .connected {
            try await wake()
            return
        }
        guard state == .connected else { throw RemoteError.notConnected }

        switch command {
        case .powerOn:
            break
        case .text(let text):
            try await control([
                "Cmd": Data(text.utf8).base64EncodedString(),
                "DataOfCmd": "base64",
                "TypeOfRemote": "SendInputString",
            ])
            try await control(["TypeOfRemote": "SendInputEnd"])
        case .touchMove(let dx, let dy):
            try await control([
                "Cmd": "Move",
                "Position": [
                    "x": Int(dx.rounded()),
                    "y": Int(dy.rounded()),
                    "Time": String(Int(Date().timeIntervalSince1970 * 1_000)),
                ] as [String: Any],
                "TypeOfRemote": "ProcessMouseDevice",
            ])
        case .touchTap:
            try await control(["Cmd": "LeftClick", "TypeOfRemote": "ProcessMouseDevice"])
        default:
            guard let key = Self.key(for: command) else { throw RemoteError.unsupportedCommand(command.wireName) }
            try await control(["Cmd": "Click", "DataOfCmd": key, "Option": "false", "TypeOfRemote": "SendRemoteKey"])
        }
    }

    // MARK: - Sesión

    private var isAwaitingPairing: Bool {
        if case .awaitingPairing = state { true } else { false }
    }

    private func connectLocked() async throws {
        if state == .connected || isAwaitingPairing { return }
        transition(to: .connecting)
        do {
            let ws = try await WebSocketConnection.open(url: try channelURL(), pinnedSHA256: profile.pinnedCertificateSHA256)
            socket = ws
            authorized = false
            authFailure = nil
            reader = Task { [weak self] in await self?.readLoop(ws) }

            let clock = ContinuousClock()
            let deadline = clock.now + Self.authFastPath
            while !authorized, authFailure == nil, socket === ws, clock.now < deadline {
                try await Task.sleep(for: .milliseconds(50))
            }
            if let authFailure { throw authFailure }
            if !authorized, state == .connecting {
                transition(to: .awaitingPairing(PairingRequest(
                    method: .confirmOnScreen,
                    message: "Pulsa «Permitir» en el TV Samsung"
                )))
            }
        } catch {
            let failure = RemoteError.wrap(error)
            teardown(failure == .cancelled ? .idle : .failed(failure))
            throw failure
        }
    }

    private func channelURL() throws -> URL {
        var components = URLComponents()
        components.scheme = port == Self.legacyPort ? "ws" : "wss"
        components.host = host
        components.port = Int(port)
        components.path = "/api/v2/channels/samsung.remote.control"
        var query = [URLQueryItem(name: "name", value: Data(Self.clientName.utf8).base64EncodedString())]
        if let token = profile.pairingToken { query.append(URLQueryItem(name: "token", value: token)) }
        components.queryItems = query
        guard let url = components.url else { throw RemoteError.invalidConfiguration("host \(host)") }
        return url
    }

    private func readLoop(_ ws: WebSocketConnection) async {
        do {
            while !Task.isCancelled {
                handle(JSONText.decode(try await ws.receive()), from: ws)
            }
        } catch {
            socketLost(ws, error: RemoteError.wrap(error))
        }
    }

    private func handle(_ message: [String: Any], from ws: WebSocketConnection) {
        switch message["event"] as? String {
        case "ms.channel.connect":
            var updated = profile
            if let token = (message["data"] as? [String: Any])?["token"] as? String, !token.isEmpty {
                updated.pairingToken = token
            }
            if let seen = ws.peerCertificateSHA256 { updated.pinnedCertificateSHA256 = seen }
            updated.lastConnectedAt = Date()
            updateProfile(updated)
            authorized = true
            transition(to: .connected)
        case "ms.channel.unauthorized":
            authFailure = .pairingRejected
            transition(to: .failed(.pairingRejected))
        case "ms.channel.timeOut":
            let failure = RemoteError.timeout("autorización en el TV Samsung")
            authFailure = failure
            transition(to: .failed(failure))
        case "ms.remote.imeStart":
            eventSink.yield(.keyboardRequested(initialText: ""))
        case "ms.remote.imeEnd":
            eventSink.yield(.keyboardDismissed)
        default:
            break
        }
    }

    private func socketLost(_ ws: WebSocketConnection, error: RemoteError) {
        guard socket === ws else { return }
        socket = nil
        if state != .idle { transition(to: .failed(error == .cancelled ? .hostUnreachable(host: host) : error)) }
    }

    private func teardown(_ newState: ConnectionState) {
        reader?.cancel()
        reader = nil
        socket?.close()
        socket = nil
        transition(to: newState)
    }

    private func wake() async throws {
        guard let mac = profile.macAddress else {
            try await connectLocked()
            return
        }
        transition(to: .waking)
        try await WakeOnLAN.wake(mac, lastKnownHost: host)
        for _ in 0..<Self.wakePollAttempts {
            try await Task.sleep(for: .seconds(1))
            if await WebOSAdapter.isReachable(host: host, port: port) {
                transition(to: .idle)
                try await connectLocked()
                return
            }
        }
        let failure = RemoteError.timeout("despertar \(profile.name) por Wake-on-LAN")
        transition(to: .failed(failure))
        throw failure
    }

    private func control(_ params: [String: Any]) async throws {
        guard let ws = socket else { throw RemoteError.notConnected }
        try await ws.send(JSONText.encode(["method": "ms.remote.control", "params": params]))
    }

    static func key(for command: RemoteCommand) -> String? {
        switch command {
        case .power, .powerOff: "KEY_POWER"
        case .volumeUp: "KEY_VOLUP"
        case .volumeDown: "KEY_VOLDOWN"
        case .mute: "KEY_MUTE"
        case .channelUp: "KEY_CHUP"
        case .channelDown: "KEY_CHDOWN"
        case .digit(let value): "KEY_\(value)"
        case .dpadUp: "KEY_UP"
        case .dpadDown: "KEY_DOWN"
        case .dpadLeft: "KEY_LEFT"
        case .dpadRight: "KEY_RIGHT"
        case .dpadCenter, .enter: "KEY_ENTER"
        case .back: "KEY_RETURN"
        case .home: "KEY_HOME"
        case .menu: "KEY_MENU"
        case .info: "KEY_INFO"
        case .input: "KEY_SOURCE"
        case .playPause, .play: "KEY_PLAY"
        case .pause: "KEY_PAUSE"
        case .stop: "KEY_STOP"
        case .rewind: "KEY_REWIND"
        case .fastForward: "KEY_FF"
        case .backspace: "KEY_BACKSPACE"
        case .raw(let key): key
        default: nil
        }
    }

    private func updateProfile(_ updated: DeviceProfile) {
        guard updated != profile else { return }
        profile = updated
        eventSink.yield(.profileUpdated(updated))
    }

    private func transition(to newState: ConnectionState) {
        guard newState != state else { return }
        state = newState
        eventSink.yield(.stateChanged(newState))
    }
}
#endif
