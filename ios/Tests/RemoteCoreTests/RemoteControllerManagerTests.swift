import Foundation
import Testing
@testable import RemoteCore

private actor FakeAdapter: RemoteAdapter {
    nonisolated let profileID: UUID
    nonisolated let capabilities = Set(RemoteCapability.allCases)
    nonisolated let events: AsyncStream<AdapterEvent>
    private let sink: AsyncStream<AdapterEvent>.Continuation
    private(set) var received: [RemoteCommand] = []
    private(set) var connectCount = 0

    init(profileID: UUID) {
        self.profileID = profileID
        let (stream, sink) = AsyncStream.makeStream(of: AdapterEvent.self)
        self.events = stream
        self.sink = sink
    }

    func connect() async throws {
        connectCount += 1
        sink.yield(.stateChanged(.connected))
    }

    func disconnect() async {
        sink.yield(.stateChanged(.idle))
    }

    func send(_ command: RemoteCommand) async throws {
        received.append(command)
    }

    func requestKeyboard(_ text: String) {
        sink.yield(.keyboardRequested(initialText: text))
    }
}

@MainActor
private func waitUntil(timeout: Duration = .seconds(2), _ condition: () async -> Bool) async throws {
    let clock = ContinuousClock()
    let deadline = clock.now + timeout
    while !(await condition()) {
        guard clock.now < deadline else {
            Issue.record("condición no cumplida en \(timeout)")
            return
        }
        try await Task.sleep(for: .milliseconds(10))
    }
}

@MainActor
struct RemoteControllerManagerTests {
    private func makeManager() -> (RemoteControllerManager, FakeAdapter, DeviceProfile) {
        let profile = DeviceProfile(name: "Fake", kind: .roku, transport: .lan(host: "127.0.0.1", port: 8060))
        let adapter = FakeAdapter(profileID: profile.id)
        let manager = RemoteControllerManager(
            registry: AdapterRegistry([.roku: { _ in adapter }]),
            store: nil,
            haptics: nil
        )
        return (manager, adapter, profile)
    }

    @Test func deliversCommandsInOrderAndCoalescesPointer() async throws {
        let (manager, adapter, profile) = makeManager()
        await manager.activate(profile)
        try await waitUntil { manager.connectionState == .connected }

        manager.send(.volumeUp)
        manager.send(.volumeUp)
        manager.send(.touchMove(dx: 3, dy: 1))
        manager.send(.touchMove(dx: 2, dy: 1))
        manager.send(.mute)

        try await waitUntil { await adapter.received.count == 4 }
        #expect(await adapter.received == [.volumeUp, .volumeUp, .touchMove(dx: 5, dy: 2), .mute])
    }

    @Test func rejectsCommandsWithoutSession() {
        let (manager, _, _) = makeManager()
        manager.send(.home)
        #expect(manager.lastError == .notConnected)
        #expect(manager.send(wireName: "CMD_DOES_NOT_EXIST") == false)
    }

    @Test func reconnectsWhenReturningFromBackground() async throws {
        let (manager, adapter, profile) = makeManager()
        await manager.activate(profile)
        try await waitUntil { manager.connectionState == .connected }

        manager.applicationDidEnterBackground()
        try await waitUntil { manager.connectionState == .idle }
        manager.applicationDidBecomeActive()
        try await waitUntil { await adapter.connectCount == 2 }
        try await waitUntil { manager.connectionState == .connected }
    }

    @Test func surfacesKeyboardRequests() async throws {
        let (manager, adapter, profile) = makeManager()
        await manager.activate(profile)
        await adapter.requestKeyboard("netf")
        try await waitUntil { manager.keyboardRequest?.initialText == "netf" }
    }
}
