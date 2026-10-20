import Foundation

/// Garantiza que una continuación se reanude exactamente una vez cuando compiten
/// varios callbacks (estado, timeout, cancelación) en colas distintas.
final class ResumeGate: @unchecked Sendable {
    private let lock = NSLock()
    private var claimed = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard !claimed else { return false }
        claimed = true
        return true
    }
}

/// Valor mutable protegido por cerrojo para callbacks de Network.framework.
final class LockedBox<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: Value

    init(_ value: Value) {
        storage = value
    }

    var value: Value {
        lock.lock()
        defer { lock.unlock() }
        return storage
    }

    func mutate<Result>(_ body: (inout Value) -> Result) -> Result {
        lock.lock()
        defer { lock.unlock() }
        return body(&storage)
    }
}

/// Exclusión mutua asíncrona (FIFO) para serializar operaciones de un actor que cruzan `await`
/// (la reentrada del actor permitiría intercalar un `send` en mitad de un `connect`).
///
/// Uso: `await mutex.acquire(); defer { mutex.release() }`.
final class AsyncMutex: @unchecked Sendable {
    private let lock = NSLock()
    private var isLocked = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func acquire() async {
        await withCheckedContinuation { enqueue($0) }
    }

    func release() {
        lock.lock()
        let next = waiters.isEmpty ? nil : waiters.removeFirst()
        if next == nil { isLocked = false }
        lock.unlock()
        next?.resume()
    }

    private func enqueue(_ continuation: CheckedContinuation<Void, Never>) {
        lock.lock()
        if isLocked {
            waiters.append(continuation)
            lock.unlock()
        } else {
            isLocked = true
            lock.unlock()
            continuation.resume()
        }
    }
}

/// Ejecuta `body` con un límite de tiempo; al vencer lo cancela y lanza `RemoteError.timeout` sin
/// esperar a que termine (las esperas de Network.framework no siempre atienden la cancelación).
func withTimeout<T: Sendable>(
    _ limit: Duration,
    operation: String,
    _ body: @escaping @Sendable () async throws -> T
) async throws -> T {
    let work = Task { try await body() }
    let gate = ResumeGate()
    return try await withTaskCancellationHandler {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<T, any Error>) in
            Task {
                do {
                    let value = try await work.value
                    if gate.claim() { continuation.resume(returning: value) }
                } catch {
                    if gate.claim() { continuation.resume(throwing: error) }
                }
            }
            Task {
                try? await Task.sleep(for: limit)
                guard gate.claim() else { return }
                work.cancel()
                continuation.resume(throwing: RemoteError.timeout(operation))
            }
        }
    } onCancel: {
        work.cancel()
    }
}
