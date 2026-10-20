#if canImport(Network)
import Darwin
import Foundation

/// Android TV / Google TV (protocolo "Android TV Remote v2", el mismo de la app Google TV).
///
/// 1. Emparejamiento (puerto 6467): TLS con certificado de cliente; el TV muestra un código
///    hexadecimal de 6 caracteres que el usuario escribe en el móvil.
/// 2. Control (puerto 6466): TLS con el mismo certificado; protobuf enmarcado por longitud. El TV
///    envía pings periódicos que hay que contestar para mantener la sesión.
public actor AndroidTVAdapter: RemoteAdapter {
    public nonisolated let profileID: UUID
    /// Sin puntero: el protocolo solo inyecta teclas (Borrar/Intro sí funcionan).
    public nonisolated let capabilities: Set<RemoteCapability> = Set(RemoteCapability.allCases).subtracting([.pointer])
    public nonisolated let events: AsyncStream<AdapterEvent>

    private static let connectTimeout: TimeInterval = 2.5
    private static let readyTimeout: Duration = .seconds(5)
    private static let wakePollAttempts = 20

    private let eventSink: AsyncStream<AdapterEvent>.Continuation
    private let identity: AndroidTVClientIdentity
    private let host: String
    private let port: UInt16
    private let mutex = AsyncMutex()
    private var profile: DeviceProfile
    private var state: ConnectionState = .idle
    private var control: TCPConnection?
    private var reader: Task<Void, Never>?
    private var sessionReady = false
    private var readerFailure: RemoteError?
    private var pairing: TCPConnection?
    private var isOn = true

    /// El TV aceptó TLS pero cortó la sesión antes de configurarla: ya no reconoce el certificado.
    private struct SessionRejected: Error {}

    public init(profile: DeviceProfile, identity: AndroidTVClientIdentity = .shared) throws {
        guard profile.kind == .androidTV, case .lan(let host, let port) = profile.transport else {
            throw RemoteError.invalidConfiguration("el perfil \(profile.name) no es un Android TV en LAN")
        }
        self.profile = profile
        self.profileID = profile.id
        self.host = host
        self.port = port
        self.identity = identity
        let (stream, sink) = AsyncStream.makeStream(of: AdapterEvent.self, bufferingPolicy: .bufferingNewest(32))
        self.events = stream
        self.eventSink = sink
    }

    deinit {
        reader?.cancel()
        eventSink.finish()
    }

    public nonisolated func supports(_ command: RemoteCommand) -> Bool {
        AndroidTVKey.code(for: command) != nil
    }

    // MARK: - API

    public func connect() async throws {
        await mutex.acquire()
        defer { mutex.release() }
        try await connectLocked()
    }

    /// No toma el cerrojo: cerrar los sockets hace fallar enseguida cualquier operación en curso.
    public func disconnect() async {
        closePairing()
        closeRemote()
        transition(to: .idle)
    }

    public func send(_ command: RemoteCommand) async throws {
        await mutex.acquire()
        defer { mutex.release() }

        if command.canWakeDevice, state != .connected {
            try await wake()
            guard state == .connected else { return }
        }
        // Reconexión transparente si la sesión persistente se cayó (TV en reposo, Wi-Fi que parpadea…).
        if state != .connected, !isAwaitingPairing {
            transition(to: .idle)
            try await connectLocked()
        }
        guard state == .connected else { throw RemoteError.notConnected }
        // KEYCODE_POWER alterna: se omite si el TV ya está en el estado pedido.
        if command == .powerOn, isOn { return }
        if command == .powerOff, !isOn { return }
        guard let key = AndroidTVKey.code(for: command) else {
            throw RemoteError.unsupportedCommand(command.wireName)
        }
        let bytes = AndroidTVProtocol.keyInject(key)
        do {
            try await write(bytes)
        } catch {
            // Socket medio cerrado: un único reintento con sesión nueva antes de rendirse.
            closeRemote()
            transition(to: .idle)
            try await connectLocked()
            guard state == .connected else { throw error }
            try await write(bytes)
        }
    }

    public func submitPairingCode(_ code: String) async throws {
        await mutex.acquire()
        defer { mutex.release() }

        guard let pairing, let serverCertificate = await pairing.peerCertificateDER else {
            throw RemoteError.notConnected
        }
        let server = try TLSTrust.rsaPublicKeyComponents(certificateDER: serverCertificate)
        let client = try identity.publicKeyComponents()
        guard let secret = AndroidTVProtocol.computeSecret(client: client, server: server, code: code) else {
            throw RemoteError.invalidCode("Código incorrecto: revisa los 6 caracteres que muestra el TV.")
        }

        let status: Int64
        do {
            try await pairing.send(ProtoFraming.frame(AndroidTVProtocol.secret(secret)))
            status = AndroidTVProtocol.status(try await Self.readFrame(pairing))
        } catch {
            closePairing()
            let failure = RemoteError.wrap(error)
            transition(to: .failed(failure))
            throw failure
        }
        closePairing()

        switch status {
        case AndroidTVProtocol.statusOK:
            // Tras re-emparejar (p. ej. TV restablecido) el certificado del TV puede ser otro:
            // se vuelve a aprender en la próxima conexión de control.
            var updated = profile
            updated.pairingToken = try identity.fingerprint()
            updated.pinnedCertificateSHA256 = nil
            updateProfile(updated)
            transition(to: .idle)
            try await connectLocked()
        case AndroidTVProtocol.statusBadSecret:
            // El TV invalida el código: se pide uno nuevo.
            transition(to: .idle)
            try await startPairing()
            throw RemoteError.invalidCode("El TV rechazó el código; introduce el nuevo que aparece en pantalla.")
        default:
            transition(to: .failed(.pairingRejected))
            throw RemoteError.pairingRejected
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
            let fingerprint = try identity.fingerprint()
            if profile.pairingToken != fingerprint {
                try await startPairing()
                return
            }
            try await probe()
            do {
                try await openRemote()
            } catch let error where Self.meansCertificateRejected(error) {
                // El TCP respondió (sonda previa): un rechazo TLS o un cierre inmediato significa que
                // el TV ya no reconoce el certificado → se pide el PIN en vez de mostrar el error.
                closeRemote()
                try await startPairing()
            }
        } catch {
            closeRemote()
            closePairing()
            let failure = RemoteError.wrap(error)
            transition(to: failure == .cancelled ? .idle : .failed(failure))
            throw failure
        }
    }

    private static func meansCertificateRejected(_ error: any Error) -> Bool {
        if error is SessionRejected { return true }
        switch error as? RemoteError {
        case .tlsFailure, .certificateMismatch: return true
        default: return false
        }
    }

    private func openRemote() async throws {
        let policy: TLSTrustPolicy = profile.pinnedCertificateSHA256.map { .pinned(sha256: $0) } ?? .acceptSelfSigned
        let connection = TCPConnection(
            host: host,
            port: port,
            security: .tls(policy, clientIdentity: try identity.tlsIdentity())
        )
        try await connection.open(timeout: Self.connectTimeout)
        control = connection
        sessionReady = false
        readerFailure = nil
        reader = Task { [weak self] in await self?.readLoop(connection) }

        let clock = ContinuousClock()
        let deadline = clock.now + Self.readyTimeout
        while !sessionReady {
            if readerFailure != nil { throw SessionRejected() }
            guard clock.now < deadline else { throw RemoteError.timeout("sesión con \(profile.name)") }
            try await Task.sleep(for: .milliseconds(50))
        }
        guard control === connection else { throw RemoteError.cancelled }

        if profile.pinnedCertificateSHA256 == nil, let seen = await connection.peerCertificateSHA256 {
            var updated = profile
            updated.pinnedCertificateSHA256 = seen
            updateProfile(updated)
        }
        var updated = profile
        updated.lastConnectedAt = Date()
        updateProfile(updated)
        transition(to: .connected)
    }

    private func startPairing() async throws {
        closePairing()
        let connection = TCPConnection(
            host: host,
            port: port + 1,
            security: .tls(.acceptSelfSigned, clientIdentity: try identity.tlsIdentity())
        )
        try await connection.open(timeout: Self.connectTimeout)
        pairing = connection
        for message in [
            AndroidTVProtocol.pairingRequest(clientName: "UniversalRemote (\(Self.deviceModel))"),
            AndroidTVProtocol.options(),
            AndroidTVProtocol.configuration(),
        ] {
            try await connection.send(ProtoFraming.frame(message))
            let status = AndroidTVProtocol.status(try await Self.readFrame(connection))
            guard status == AndroidTVProtocol.statusOK else { throw RemoteError.pairingRejected }
        }
        transition(to: .awaitingPairing(PairingRequest(
            method: .pin(length: 6),
            message: "Escribe el código de 6 caracteres que muestra el TV"
        )))
    }

    /// Prueba TCP real (no ICMP): distingue "TV apagada/IP cambiada" de un fallo TLS posterior.
    private func probe() async throws {
        let connection = TCPConnection(host: host, port: port, security: .plaintext)
        try await connection.open(timeout: Self.connectTimeout)
        await connection.close()
    }

    private func readLoop(_ connection: TCPConnection) async {
        do {
            while !Task.isCancelled {
                let message = try ProtoMessage(parsing: try await Self.readFrame(connection))
                if message.has(AndroidTVProtocol.fieldConfigure) {
                    try await connection.send(ProtoFraming.frame(AndroidTVProtocol.configure(
                        model: Self.deviceModel,
                        vendor: "Apple",
                        packageName: Bundle.main.bundleIdentifier ?? "com.universalremote",
                        appVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
                    )))
                } else if message.has(AndroidTVProtocol.fieldSetActive) {
                    try await connection.send(ProtoFraming.frame(AndroidTVProtocol.setActive()))
                    sessionReady = true
                } else if let ping = message.message(AndroidTVProtocol.fieldPingRequest) {
                    try await connection.send(ProtoFraming.frame(AndroidTVProtocol.pingResponse(ping.int(1) ?? 0)))
                } else if message.has(AndroidTVProtocol.fieldStart) {
                    isOn = message.message(AndroidTVProtocol.fieldStart)?.int(1) == 1
                    sessionReady = true
                } else if message.has(AndroidTVProtocol.fieldIMEShow) {
                    eventSink.yield(.keyboardRequested(initialText: ""))
                }
            }
        } catch {
            guard control === connection else { return }
            let failure = RemoteError.wrap(error)
            if !sessionReady {
                readerFailure = failure
            } else if state == .connected {
                closeRemote()
                transition(to: .failed(failure))
            }
        }
    }

    private func write(_ bytes: Data) async throws {
        guard let control else { throw RemoteError.notConnected }
        do {
            try await control.send(ProtoFraming.frame(bytes))
        } catch {
            let failure = RemoteError.wrap(error)
            transition(to: .failed(failure))
            throw failure
        }
    }

    private func wake() async throws {
        do {
            try await connectLocked()
            return
        } catch {
            guard let mac = profile.macAddress else { throw error }
            transition(to: .waking)
            try await WakeOnLAN.wake(mac, lastKnownHost: host)
        }
        // Se sondea el puerto en estado "despertando" y solo se abre la sesión cuando responde.
        for _ in 0..<Self.wakePollAttempts {
            try await Task.sleep(for: .seconds(1))
            if (try? await probe()) != nil {
                transition(to: .idle)
                try await connectLocked()
                return
            }
        }
        let failure = RemoteError.timeout("despertar \(profile.name) por Wake-on-LAN")
        transition(to: .failed(failure))
        throw failure
    }

    private func closeRemote() {
        reader?.cancel()
        reader = nil
        sessionReady = false
        if let control {
            Task { await control.close() }
        }
        control = nil
    }

    private func closePairing() {
        if let pairing {
            Task { await pairing.close() }
        }
        pairing = nil
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

    private static func readFrame(_ connection: TCPConnection) async throws -> Data {
        try await ProtoFraming.read(
            readByte: {
                guard let byte = try await connection.receive(exactly: 1).first else {
                    throw RemoteError.network("trama vacía")
                }
                return byte
            },
            readExactly: { try await connection.receive(exactly: $0) }
        )
    }

    /// Identificador de hardware (p. ej. `iPhone15,2`) que el TV muestra en su lista de mandos.
    private static let deviceModel: String = {
        var info = utsname()
        uname(&info)
        let machine = withUnsafeBytes(of: &info.machine) { buffer in
            String(decoding: buffer.prefix { $0 != 0 }, as: UTF8.self)
        }
        return machine.isEmpty ? "iPhone" : machine
    }()
}
#endif
