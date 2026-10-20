#if canImport(Network)
import Foundation

/// LG webOS (SSAP sobre WebSocket, wss://host:3001).
///
/// El primer registro muestra un aviso en el TV; al aceptarlo el TV devuelve un `client-key` que se
/// guarda como `pairingToken` y evita el aviso en adelante. Las teclas de navegación viajan por un
/// segundo socket ("pointer input") que se abre bajo demanda.
public actor WebOSAdapter: RemoteAdapter {
    public nonisolated let profileID: UUID
    public nonisolated let capabilities = Set(RemoteCapability.allCases)
    public nonisolated let events: AsyncStream<AdapterEvent>

    private static let legacyPort: UInt16 = 3000
    private static let registerID = "register_0"
    private static let keyboardID = "keyboard_0"
    private static let registerFastPath: Duration = .seconds(3)
    private static let requestTimeout: Duration = .seconds(5)
    private static let wakePollAttempts = 20
    private static let permissions = [
        "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO", "CONTROL_DISPLAY",
        "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_TEXT", "CONTROL_INPUT_TV", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER",
        "CONTROL_TV_SCREEN", "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST",
        "READ_INSTALLED_APPS", "READ_NETWORK_STATE", "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST",
        "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "READ_COUNTRY_INFO",
    ]

    private let eventSink: AsyncStream<AdapterEvent>.Continuation
    private let host: String
    private let port: UInt16
    private let mutex = AsyncMutex()
    private var profile: DeviceProfile
    private var state: ConnectionState = .idle
    private var socket: WebSocketConnection?
    private var pointerSocket: WebSocketConnection?
    private var reader: Task<Void, Never>?
    private var registered = false
    private var registerFailure: RemoteError?
    private var nextID = 0
    private var pending: [String: CheckedContinuation<String, any Error>] = [:]
    private var playing = false

    public init(profile: DeviceProfile) throws {
        guard profile.kind == .lgWebOS, case .lan(let host, let port) = profile.transport else {
            throw RemoteError.invalidConfiguration("el perfil \(profile.name) no es un TV LG en LAN")
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
        case .search, .next, .previous: false
        default: capabilities.contains(command.requiredCapability)
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
        case .power, .powerOff: try await request("ssap://system/turnOff")
        case .powerOn: break
        case .volumeUp: try await request("ssap://audio/volumeUp")
        case .volumeDown: try await request("ssap://audio/volumeDown")
        case .channelUp: try await request("ssap://tv/channelUp")
        case .channelDown: try await request("ssap://tv/channelDown")
        case .play: try await media("play")
        case .pause: try await media("pause")
        case .playPause: try await media(playing ? "pause" : "play")
        case .stop: try await media("stop")
        case .rewind: try await media("rewind")
        case .fastForward: try await media("fastForward")
        case .input: try await request("ssap://system.launcher/launch", ["id": "com.webos.app.inputpicker"])
        case .text(let text): try await request("ssap://com.webos.service.ime/insertText", ["text": text, "replace": 0])
        case .backspace: try await request("ssap://com.webos.service.ime/deleteCharacters", ["count": 1])
        case .enter: try await request("ssap://com.webos.service.ime/sendEnterKey")
        case .touchMove(let dx, let dy):
            try await pointer("type:move\ndx:\(Int(dx.rounded()))\ndy:\(Int(dy.rounded()))\ndown:0\n\n")
        case .scroll(let dx, let dy):
            try await pointer("type:scroll\ndx:\(Int(dx.rounded()))\ndy:\(Int(dy.rounded()))\n\n")
        case .touchTap: try await pointer("type:click\n\n")
        default:
            guard let name = Self.button(for: command) else { throw RemoteError.unsupportedCommand(command.wireName) }
            try await pointer("type:button\nname:\(name)\n\n")
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
            let scheme = port == Self.legacyPort ? "ws" : "wss"
            guard let url = URL(string: "\(scheme)://\(host):\(port)/") else {
                throw RemoteError.invalidConfiguration("host \(host)")
            }
            let ws = try await WebSocketConnection.open(url: url, pinnedSHA256: profile.pinnedCertificateSHA256)
            socket = ws
            registered = false
            registerFailure = nil
            reader = Task { [weak self] in await self?.readLoop(ws) }
            try await ws.send(registerMessage())

            // Con client-key válido el TV responde "registered" al instante; si no, muestra el aviso.
            let clock = ContinuousClock()
            let deadline = clock.now + Self.registerFastPath
            while !registered, registerFailure == nil, socket === ws, clock.now < deadline {
                try await Task.sleep(for: .milliseconds(50))
            }
            if let registerFailure { throw registerFailure }
            if !registered, state == .connecting {
                transition(to: .awaitingPairing(PairingRequest(
                    method: .confirmOnScreen,
                    message: "Acepta la solicitud que aparece en el TV LG"
                )))
            }
        } catch {
            let failure = RemoteError.wrap(error)
            teardown(failure == .cancelled ? .idle : .failed(failure))
            throw failure
        }
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
        let id = message["id"] as? String ?? ""
        let type = message["type"] as? String ?? ""
        let payload = message["payload"] as? [String: Any] ?? [:]
        switch (id, type) {
        case (Self.registerID, "registered"):
            var updated = profile
            if let key = payload["client-key"] as? String, !key.isEmpty { updated.pairingToken = key }
            if let seen = ws.peerCertificateSHA256 { updated.pinnedCertificateSHA256 = seen }
            updated.lastConnectedAt = Date()
            updateProfile(updated)
            registered = true
            transition(to: .connected)
            subscribeKeyboard(ws)
        case (Self.registerID, "error"):
            registerFailure = .pairingRejected
            transition(to: .failed(.pairingRejected))
        case (Self.registerID, _):
            break
        case (Self.keyboardID, _):
            if let widget = payload["currentWidget"] as? [String: Any] {
                if widget["focus"] as? Bool == true {
                    eventSink.yield(.keyboardRequested(initialText: widget["value"] as? String ?? ""))
                } else {
                    eventSink.yield(.keyboardDismissed)
                }
            }
        default:
            pending.removeValue(forKey: id)?.resume(returning: JSONText.encode(message))
        }
    }

    private func subscribeKeyboard(_ ws: WebSocketConnection) {
        let message = JSONText.encode([
            "type": "subscribe",
            "id": Self.keyboardID,
            "uri": "ssap://com.webos.service.ime/registerRemoteKeyboard",
        ])
        Task { try? await ws.send(message) }
    }

    private func socketLost(_ ws: WebSocketConnection, error: RemoteError) {
        guard socket === ws else { return }
        failPending(error)
        socket = nil
        pointerSocket?.close()
        pointerSocket = nil
        if state != .idle { transition(to: .failed(error == .cancelled ? .hostUnreachable(host: host) : error)) }
    }

    private func teardown(_ newState: ConnectionState) {
        reader?.cancel()
        reader = nil
        pointerSocket?.close()
        pointerSocket = nil
        socket?.close()
        socket = nil
        failPending(.notConnected)
        transition(to: newState)
    }

    private func failPending(_ error: RemoteError) {
        let waiting = pending
        pending.removeAll()
        waiting.values.forEach { $0.resume(throwing: error) }
    }

    private func wake() async throws {
        guard let mac = profile.macAddress else {
            // Sin MAC aprendida solo se puede encender si el TV sigue en la red (modo "Quick Start").
            try await connectLocked()
            return
        }
        transition(to: .waking)
        try await WakeOnLAN.wake(mac, lastKnownHost: host)
        for _ in 0..<Self.wakePollAttempts {
            try await Task.sleep(for: .seconds(1))
            if await Self.isReachable(host: host, port: port) {
                transition(to: .idle)
                try await connectLocked()
                return
            }
        }
        let failure = RemoteError.timeout("despertar \(profile.name) por Wake-on-LAN")
        transition(to: .failed(failure))
        throw failure
    }

    static func isReachable(host: String, port: UInt16) async -> Bool {
        let probe = TCPConnection(host: host, port: port, security: .plaintext)
        defer { Task { await probe.close() } }
        return (try? await probe.open(timeout: 2.5)) != nil
    }

    // MARK: - SSAP

    @discardableResult
    private func request(_ uri: String, _ payload: [String: Any]? = nil) async throws -> [String: Any] {
        guard let ws = socket else { throw RemoteError.notConnected }
        nextID += 1
        let id = "req_\(nextID)"
        var message: [String: Any] = ["type": "request", "id": id, "uri": uri]
        if let payload { message["payload"] = payload }
        let text = JSONText.encode(message)

        let timeout = Self.requestTimeout
        let expiry = Task { [weak self] in
            try? await Task.sleep(for: timeout)
            await self?.expire(id)
        }
        defer { expiry.cancel() }
        let responseText: String = try await withCheckedThrowingContinuation { continuation in
            pending[id] = continuation
            Task {
                do {
                    try await ws.send(text)
                } catch {
                    await self.expire(id, error: RemoteError.wrap(error))
                }
            }
        }
        let response = JSONText.decode(responseText)
        if response["type"] as? String == "error" {
            throw RemoteError.invalidResponse("el TV LG rechazó \(uri): \(response["error"] as? String ?? "")")
        }
        return response["payload"] as? [String: Any] ?? [:]
    }

    private func expire(_ id: String, error: RemoteError? = nil) {
        pending.removeValue(forKey: id)?.resume(throwing: error ?? RemoteError.timeout("respuesta del TV LG"))
    }

    private func media(_ action: String) async throws {
        try await request("ssap://media.controls/\(action)")
        if action == "play" { playing = true }
        if action == "pause" || action == "stop" { playing = false }
    }

    private func pointer(_ frame: String) async throws {
        let ws: WebSocketConnection
        if let pointerSocket {
            ws = pointerSocket
        } else {
            ws = try await openPointer()
        }
        do {
            try await ws.send(frame)
        } catch {
            // El TV cierra el socket de puntero tras un rato inactivo: se reabre una vez.
            pointerSocket?.close()
            pointerSocket = nil
            try await openPointer().send(frame)
        }
    }

    private func openPointer() async throws -> WebSocketConnection {
        let payload = try await request("ssap://com.webos.service.networkinput/getPointerInputSocket")
        guard let path = payload["socketPath"] as? String, let url = URL(string: path) else {
            throw RemoteError.invalidResponse("el TV LG no ofreció socket de puntero")
        }
        let ws = try await WebSocketConnection.open(url: url, pinnedSHA256: profile.pinnedCertificateSHA256)
        pointerSocket = ws
        return ws
    }

    private func registerMessage() -> String {
        var payload: [String: Any] = [
            "forcePairing": false,
            "pairingType": "PROMPT",
            "manifest": [
                "manifestVersion": 1,
                "appVersion": "1.0",
                "permissions": Self.permissions,
            ] as [String: Any],
        ]
        if let key = profile.pairingToken { payload["client-key"] = key }
        return JSONText.encode(["type": "register", "id": Self.registerID, "payload": payload])
    }

    /// Nombres de botón del socket "pointer input" de webOS.
    static func button(for command: RemoteCommand) -> String? {
        switch command {
        case .dpadUp: "UP"
        case .dpadDown: "DOWN"
        case .dpadLeft: "LEFT"
        case .dpadRight: "RIGHT"
        case .dpadCenter: "ENTER"
        case .back: "BACK"
        case .home: "HOME"
        case .menu: "MENU"
        case .info: "INFO"
        case .mute: "MUTE"
        case .digit(let value): String(value)
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
