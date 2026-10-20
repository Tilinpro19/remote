import Foundation
import Observation

public struct ReconnectPolicy: Sendable {
    public var maxAttempts: Int
    public var baseDelay: Duration
    public var maxDelay: Duration

    public init(maxAttempts: Int = 6, baseDelay: Duration = .milliseconds(500), maxDelay: Duration = .seconds(10)) {
        self.maxAttempts = maxAttempts
        self.baseDelay = baseDelay
        self.maxDelay = maxDelay
    }

    public static let `default` = ReconnectPolicy()

    /// Backoff exponencial: 0.5 s, 1 s, 2 s, 4 s… acotado a `maxDelay`.
    public func delay(forAttempt attempt: Int) -> Duration {
        let factor = 1 << min(max(attempt - 1, 0), 16)
        return min(baseDelay * factor, maxDelay)
    }
}

/// Fachada que consume la UI: envía comandos genéricos y publica el estado de conexión.
///
/// Uso desde SwiftUI:
/// ```swift
/// @Environment(RemoteControllerManager.self) private var remote
/// Button { remote.send(.volumeUp) } label: { … }
/// ```
@MainActor
@Observable
public final class RemoteControllerManager {
    public private(set) var profiles: [DeviceProfile] = []
    public private(set) var activeProfile: DeviceProfile?
    public private(set) var connectionState: ConnectionState = .idle
    public private(set) var lastError: RemoteError?
    public private(set) var keyboardRequest: KeyboardRequest?
    public private(set) var discovery = DiscoverySnapshot()
    public private(set) var isDiscovering = false

    public var activeCapabilities: Set<RemoteCapability> { session?.adapter.capabilities ?? [] }

    private enum QueueItem: Sendable {
        case command(RemoteCommand)
        case flushPointer
        case flushScroll
    }

    private struct ActiveSession {
        let adapter: any RemoteAdapter
        let queue: AsyncStream<QueueItem>.Continuation
        let commandLoop: Task<Void, Never>
        let eventLoop: Task<Void, Never>
    }

    private static let queueCapacity = 128
    /// Reconexión previa a un comando: acotada para no dejar la cola bloqueada.
    private static let reconnectTimeout: Duration = .seconds(6)
    /// Envío de un comando ya conectado; encender queda fuera (incluye Wake-on-LAN y sondeo).
    private static let commandTimeout: Duration = .seconds(2)

    @ObservationIgnored private let registry: AdapterRegistry
    @ObservationIgnored private let store: ProfileStore?
    @ObservationIgnored private let haptics: (any HapticFeedbackProviding)?
    @ObservationIgnored private let reconnectPolicy: ReconnectPolicy
    @ObservationIgnored private var session: ActiveSession?
    @ObservationIgnored private var reconnectTask: Task<Void, Never>?
    @ObservationIgnored private var discoveryTask: Task<Void, Never>?
    @ObservationIgnored private var relocateTask: Task<Void, Never>?
    /// Candidatos ya comprobados en el escaneo actual para no resolverlos de nuevo.
    @ObservationIgnored private var relocationChecked: Set<String> = []
    @ObservationIgnored private var saveTask: Task<Void, Never>?
    @ObservationIgnored private var isForeground = true
    @ObservationIgnored private var hasBootstrapped = false
    @ObservationIgnored private var touchpad = TouchpadTracker()
    @ObservationIgnored private var pendingPointer = (dx: 0.0, dy: 0.0)
    @ObservationIgnored private var pendingScroll = (dx: 0.0, dy: 0.0)
    @ObservationIgnored private var pointerFlushQueued = false
    @ObservationIgnored private var scrollFlushQueued = false

    public init(
        registry: AdapterRegistry = .standard,
        store: ProfileStore? = .shared,
        haptics: (any HapticFeedbackProviding)? = nil,
        reconnectPolicy: ReconnectPolicy = .default
    ) {
        self.registry = registry
        self.store = store
        self.reconnectPolicy = reconnectPolicy
        #if canImport(UIKit)
        self.haptics = haptics ?? UIKitHaptics()
        #else
        self.haptics = haptics
        #endif
    }

    // MARK: - Ciclo de vida

    /// Carga perfiles y reconecta con el último dispositivo usado. Idempotente.
    public func bootstrap() async {
        guard !hasBootstrapped else { return }
        hasBootstrapped = true
        if let store {
            do {
                profiles = try await store.load()
            } catch {
                lastError = .wrap(error)
            }
        }
        let lastUsed = profiles
            .filter { $0.lastConnectedAt != nil }
            .max { ($0.lastConnectedAt ?? .distantPast) < ($1.lastConnectedAt ?? .distantPast) }
        if let lastUsed, registry.canHandle(lastUsed.kind) {
            await activate(lastUsed)
        }
    }

    public func applicationDidEnterBackground() {
        isForeground = false
        reconnectTask?.cancel()
        reconnectTask = nil
        touchpad.cancel()
        guard let adapter = session?.adapter else { return }
        Task { await adapter.disconnect() }
    }

    public func applicationDidBecomeActive() {
        let resumingFromBackground = !isForeground
        isForeground = true
        guard resumingFromBackground, session != nil else { return }
        Task { await self.connectActiveAdapter() }
    }

    // MARK: - Perfiles

    public func activate(_ profile: DeviceProfile) async {
        await teardownSession()
        upsert(profile)
        activeProfile = profile
        lastError = nil
        do {
            try startSession(for: profile)
        } catch {
            let failure = RemoteError.wrap(error)
            lastError = failure
            connectionState = .failed(failure)
            return
        }
        await connectActiveAdapter()
    }

    public func activate(profileID: UUID) async {
        guard let profile = profiles.first(where: { $0.id == profileID }) else {
            lastError = .invalidConfiguration("perfil \(profileID) inexistente")
            return
        }
        await activate(profile)
    }

    public func deactivate() async {
        await teardownSession()
        activeProfile = nil
    }

    public func saveProfile(_ profile: DeviceProfile) {
        upsert(profile)
        if activeProfile?.id == profile.id { activeProfile = profile }
    }

    public func removeProfile(id: UUID) async {
        if activeProfile?.id == id { await deactivate() }
        profiles.removeAll { $0.id == id }
        persistProfiles()
    }

    /// El equipo cambió de IP (DHCP): se persiste la nueva y, si es el activo, se recrea su
    /// adaptador (el host se fija al construirlo) y se reconecta.
    public func relocate(profileID: UUID, host: String) async {
        guard var profile = profiles.first(where: { $0.id == profileID }),
              case .lan(let oldHost, let port) = profile.transport, oldHost != host else { return }
        profile.transport = .lan(host: host, port: port)
        if activeProfile?.id == profileID {
            await activate(profile)
        } else {
            upsert(profile)
        }
    }

    /// Olvida el emparejamiento del equipo activo para pedir de nuevo el PIN o la aceptación en el TV
    /// (acción «Vincular» ante errores SSL; ver `RemoteError.requiresRepairing`).
    public func resetPairing() async {
        guard var profile = activeProfile else { return }
        profile.pairingToken = nil
        profile.pinnedCertificateSHA256 = nil
        await activate(profile)
    }

    public func submitPairingCode(_ code: String) async {
        guard let adapter = session?.adapter else {
            lastError = .notConnected
            return
        }
        do {
            try await adapter.submitPairingCode(code)
        } catch {
            lastError = .wrap(error)
            haptics?.playFailure()
        }
    }

    // MARK: - Comandos

    /// Punto de entrada principal de la UI. No bloquea: encola en orden y vuelve.
    public func send(_ command: RemoteCommand) {
        guard let session else {
            reject(command, reason: .notConnected)
            return
        }
        guard session.adapter.supports(command) else {
            reject(command, reason: .unsupportedCommand(command.wireName))
            return
        }
        // Sin conexión se encola igual: el despacho reconecta antes de enviar. Mientras el TV espera
        // el PIN o la aceptación no tiene sentido reintentar.
        if case .awaitingPairing = connectionState, !command.canWakeDevice {
            reject(command, reason: .notConnected)
            return
        }
        haptics?.play(command.hapticWeight)

        switch command {
        case .touchMove(let dx, let dy):
            pendingPointer.dx += dx
            pendingPointer.dy += dy
            if !pointerFlushQueued {
                pointerFlushQueued = true
                session.queue.yield(.flushPointer)
            }
        case .scroll(let dx, let dy):
            pendingScroll.dx += dx
            pendingScroll.dy += dy
            if !scrollFlushQueued {
                scrollFlushQueued = true
                session.queue.yield(.flushScroll)
            }
        default:
            session.queue.yield(.command(command))
        }
    }

    /// Variante para UIs que emiten identificadores (`"CMD_VOL_UP"`).
    @discardableResult
    public func send(wireName: String, dx: Double = 0, dy: Double = 0, text: String? = nil) -> Bool {
        guard let command = RemoteCommand(wireName: wireName, dx: dx, dy: dy, text: text) else {
            lastError = .unsupportedCommand(wireName)
            haptics?.playFailure()
            return false
        }
        send(command)
        return true
    }

    public func touchpadBegan(x: Double, y: Double) {
        touchpad.began(at: .init(x: x, y: y))
    }

    public func touchpadMoved(x: Double, y: Double) {
        if case .move(let dx, let dy) = touchpad.moved(to: .init(x: x, y: y)) {
            send(.touchMove(dx: dx, dy: dy))
        }
    }

    public func touchpadEnded(x: Double, y: Double) {
        for output in touchpad.ended(at: .init(x: x, y: y)) {
            switch output {
            case .move(let dx, let dy): send(.touchMove(dx: dx, dy: dy))
            case .tap: send(.touchTap)
            }
        }
    }

    public func submitText(_ text: String) {
        guard !text.isEmpty else { return }
        send(.text(text))
    }

    public func dismissKeyboard() {
        keyboardRequest = nil
    }

    public func clearError() {
        lastError = nil
    }

    // MARK: - Sesión

    private func startSession(for profile: DeviceProfile) throws {
        let adapter = try registry.makeAdapter(for: profile)
        let (stream, queue) = AsyncStream.makeStream(
            of: QueueItem.self,
            bufferingPolicy: .bufferingNewest(Self.queueCapacity)
        )
        // Un único consumidor serializa los envíos: VOL+ ×3 llega como VOL+ ×3 y en orden.
        let commandLoop = Task { [weak self] in
            for await item in stream {
                guard let command = self?.resolve(item) else { continue }
                await self?.dispatch(command, to: adapter)
            }
        }
        let eventLoop = Task { [weak self] in
            for await event in adapter.events {
                self?.handle(event, from: adapter)
            }
        }
        session = ActiveSession(adapter: adapter, queue: queue, commandLoop: commandLoop, eventLoop: eventLoop)
    }

    private func teardownSession() async {
        reconnectTask?.cancel()
        reconnectTask = nil
        guard let current = session else { return }
        session = nil
        current.queue.finish()
        current.commandLoop.cancel()
        current.eventLoop.cancel()
        await current.adapter.disconnect()
        pendingPointer = (0, 0)
        pendingScroll = (0, 0)
        pointerFlushQueued = false
        scrollFlushQueued = false
        touchpad.cancel()
        keyboardRequest = nil
        connectionState = .idle
    }

    private func connectActiveAdapter() async {
        guard let adapter = session?.adapter else { return }
        do {
            try await adapter.connect()
        } catch {
            let failure = RemoteError.wrap(error)
            guard failure != .cancelled else { return }
            lastError = failure
            connectionState = .failed(failure)
            noteFailure(failure)
            scheduleReconnect()
        }
    }

    private func resolve(_ item: QueueItem) -> RemoteCommand? {
        switch item {
        case .command(let command):
            return command
        case .flushPointer:
            pointerFlushQueued = false
            let delta = pendingPointer
            pendingPointer = (0, 0)
            return delta == (0, 0) ? nil : .touchMove(dx: delta.dx, dy: delta.dy)
        case .flushScroll:
            scrollFlushQueued = false
            let delta = pendingScroll
            pendingScroll = (0, 0)
            return delta == (0, 0) ? nil : .scroll(dx: delta.dx, dy: delta.dy)
        }
    }

    private func dispatch(_ command: RemoteCommand, to adapter: any RemoteAdapter) async {
        do {
            if !connectionState.isConnected, !command.canWakeDevice {
                try await withTimeout(Self.reconnectTimeout, operation: "reconexión") { try await adapter.connect() }
            }
            if command.canWakeDevice {
                try await adapter.send(command)
            } else {
                try await withTimeout(Self.commandTimeout, operation: command.wireName) { try await adapter.send(command) }
            }
        } catch {
            let failure = RemoteError.wrap(error)
            guard failure != .cancelled else { return }
            lastError = failure
            if !command.isContinuous { haptics?.playFailure() }
            noteFailure(failure)
            if failure.isRetryable { scheduleReconnect() }
        }
    }

    private func reject(_ command: RemoteCommand, reason: RemoteError) {
        lastError = reason
        if !command.isContinuous { haptics?.playFailure() }
    }

    private func handle(_ event: AdapterEvent, from adapter: any RemoteAdapter) {
        guard let session, session.adapter === adapter else { return }
        switch event {
        case .stateChanged(let state):
            if reconnectTask != nil, case .failed = state { return }
            connectionState = state
            if case .failed(let failure) = state {
                lastError = failure
                noteFailure(failure)
                if failure.isRetryable { scheduleReconnect() }
            }
        case .profileUpdated(let profile):
            upsert(profile)
            if activeProfile?.id == profile.id { activeProfile = profile }
        case .keyboardRequested(let initialText):
            keyboardRequest = KeyboardRequest(initialText: initialText)
        case .keyboardDismissed:
            keyboardRequest = nil
        }
    }

    private func scheduleReconnect() {
        guard isForeground, reconnectTask == nil, let adapter = session?.adapter else { return }
        let policy = reconnectPolicy
        reconnectTask = Task { [weak self] in
            var lastFailure: RemoteError = .notConnected
            for attempt in 1...max(policy.maxAttempts, 1) {
                self?.connectionState = .reconnecting(attempt: attempt)
                try? await Task.sleep(for: policy.delay(forAttempt: attempt))
                if Task.isCancelled { return }
                do {
                    try await adapter.connect()
                    self?.reconnectTask = nil
                    self?.connectionState = .connected
                    self?.lastError = nil
                    return
                } catch {
                    lastFailure = .wrap(error)
                    if lastFailure == .cancelled || !lastFailure.isRetryable { break }
                }
            }
            guard !Task.isCancelled else { return }
            self?.reconnectTask = nil
            self?.lastError = lastFailure
            self?.connectionState = .failed(lastFailure)
            self?.noteFailure(lastFailure)
        }
    }

    /// Un "no se alcanza" puede ser solo un cambio de IP: se busca el equipo en segundo plano.
    private func noteFailure(_ failure: RemoteError) {
        #if canImport(Network)
        if failure.indicatesUnreachableHost { relocateSilently() }
        #endif
    }

    // MARK: - Persistencia

    private func upsert(_ profile: DeviceProfile) {
        if let index = profiles.firstIndex(where: { $0.id == profile.id }) {
            guard profiles[index] != profile else { return }
            profiles[index] = profile
        } else {
            profiles.append(profile)
        }
        persistProfiles()
    }

    /// Encadena escrituras para que el último snapshot sea siempre el que queda en disco.
    private func persistProfiles() {
        guard let store else { return }
        let snapshot = profiles
        let previous = saveTask
        saveTask = Task { [weak self] in
            await previous?.value
            do {
                try await store.save(snapshot)
            } catch {
                self?.lastError = .wrap(error)
            }
        }
    }
}

// MARK: - Descubrimiento

#if canImport(Network)
extension RemoteControllerManager {
    /// Inicia mDNS continuo + una ronda SSDP. Llama a `stopDiscovery()` al salir de la pantalla de escaneo.
    public func startDiscovery() {
        stopDiscovery()
        isDiscovering = true
        discovery = DiscoverySnapshot()
        relocationChecked = []
        discoveryTask = Task { [weak self] in
            do {
                for try await snapshot in DeviceDiscoveryService.discover() {
                    self?.discovery = snapshot
                    Task { await self?.relocateSavedDevices(snapshot.devices) }
                }
            } catch {
                let failure = RemoteError.wrap(error)
                if failure != .cancelled { self?.lastError = failure }
            }
            self?.isDiscovering = false
        }
    }

    public func stopDiscovery() {
        discoveryTask?.cancel()
        discoveryTask = nil
        isDiscovering = false
    }

    /// Crea el perfil (resolviendo IP/MAC) y conecta con él.
    @discardableResult
    public func pair(with device: DiscoveredDevice) async -> DeviceProfile? {
        do {
            var profile = try await DeviceDiscoveryService.makeProfile(for: device)
            // Mismo TV ya guardado (quizá con otra IP): se actualiza en lugar de duplicarlo y se
            // conservan su emparejamiento y su huella TLS.
            if let existing = savedMatch(for: profile) {
                var merged = existing
                merged.transport = profile.transport
                merged.macAddress = profile.macAddress ?? existing.macAddress
                merged.model = existing.model ?? profile.model
                merged.serialNumber = existing.serialNumber ?? profile.serialNumber
                profile = merged
            }
            stopDiscovery()
            await activate(profile)
            return profile
        } catch {
            lastError = .wrap(error)
            haptics?.playFailure()
            return nil
        }
    }

    /// Alta manual por IP cuando el descubrimiento no está disponible.
    @discardableResult
    public func addManualDevice(name: String, kind: DeviceKind, host: String, port: UInt16? = nil) async -> DeviceProfile? {
        guard let resolvedPort = port ?? kind.defaultControlPort else {
            lastError = .invalidConfiguration("\(kind.rawValue) no usa transporte IP")
            return nil
        }
        var profile = DeviceProfile(name: name, kind: kind, transport: .lan(host: host, port: resolvedPort))
        if let existing = profiles.first(where: { $0.kind == kind && $0.lanHost == host }) {
            profile = existing
            profile.transport = .lan(host: host, port: resolvedPort)
        }
        await activate(profile)
        return profile
    }

    private func savedMatch(for profile: DeviceProfile) -> DeviceProfile? {
        let sameKind = profiles.filter { $0.kind == profile.kind && $0.lanHost != nil }
        return sameKind.first { $0.lanHost == profile.lanHost }
            ?? sameKind.first { $0.serialNumber != nil && $0.serialNumber == profile.serialNumber }
            ?? sameKind.first { $0.name.caseInsensitiveCompare(profile.name) == .orderedSame }
    }

    /// Tras un "no se alcanza", escanea unos segundos por si el TV solo cambió de IP.
    fileprivate func relocateSilently() {
        guard relocateTask == nil, discoveryTask == nil else { return }
        relocationChecked = []
        relocateTask = Task { [weak self] in
            let scan = Task { [weak self] in
                for try await snapshot in DeviceDiscoveryService.discover() {
                    await self?.relocateSavedDevices(snapshot.devices)
                }
            }
            try? await Task.sleep(for: .seconds(5))
            scan.cancel()
            self?.relocateTask = nil
        }
    }

    /// Un equipo guardado reaparece con el mismo tipo y nombre (o número de serie) en otra IP:
    /// se actualiza su perfil.
    func relocateSavedDevices(_ devices: [DiscoveredDevice]) async {
        for device in devices {
            guard let kind = device.kind, !relocationChecked.contains(device.id) else { continue }
            let sameKind = profiles.filter { $0.kind == kind && $0.lanHost != nil }
            let match = sameKind.first { profile in
                if case .ssdp(_, let usn) = device.source, let serial = profile.serialNumber {
                    return usn.hasSuffix(serial)
                }
                return profile.name.caseInsensitiveCompare(device.name) == .orderedSame
            }
            guard let match else { continue }
            relocationChecked.insert(device.id)
            var host = device.knownHost
            if host == nil, case .bonjour(let name, let type, let domain, _) = device.source {
                host = try? await BonjourDiscovery.resolve(name: name, type: type, domain: domain).host
            }
            if let host, host != match.lanHost {
                await relocate(profileID: match.id, host: host)
            }
        }
    }

    /// Solicita (o verifica) el permiso de red local de iOS antes de escanear.
    public func requestLocalNetworkAccess() async -> LocalNetworkAuthorization.Status {
        let status = await LocalNetworkAuthorization.request()
        if status == .denied { lastError = .localNetworkDenied }
        return status
    }
}
#endif
