import Foundation

/// Convierte coordenadas absolutas del componente Touchpad en deltas de cursor y taps.
public struct TouchpadTracker: Sendable {
    public struct Point: Sendable, Equatable {
        public var x: Double
        public var y: Double

        public init(x: Double, y: Double) {
            self.x = x
            self.y = y
        }
    }

    public enum Output: Sendable, Equatable {
        case move(dx: Double, dy: Double)
        case tap
    }

    /// Multiplicador aplicado a cada delta (aceleración lineal).
    public var sensitivity: Double
    /// Desplazamiento total máximo (pt) para que un toque cuente como tap.
    public var tapSlop: Double
    public var tapMaxDuration: Duration

    private var last: Point?
    private var startedAt: ContinuousClock.Instant?
    private var travelled: Double = 0

    public init(sensitivity: Double = 1.4, tapSlop: Double = 10, tapMaxDuration: Duration = .milliseconds(250)) {
        self.sensitivity = sensitivity
        self.tapSlop = tapSlop
        self.tapMaxDuration = tapMaxDuration
    }

    public mutating func began(at point: Point, time: ContinuousClock.Instant = .now) {
        last = point
        startedAt = time
        travelled = 0
    }

    public mutating func moved(to point: Point) -> Output? {
        guard let previous = last else {
            began(at: point)
            return nil
        }
        last = point
        let dx = point.x - previous.x
        let dy = point.y - previous.y
        guard dx != 0 || dy != 0 else { return nil }
        travelled += (dx * dx + dy * dy).squareRoot()
        return .move(dx: dx * sensitivity, dy: dy * sensitivity)
    }

    public mutating func ended(at point: Point, time: ContinuousClock.Instant = .now) -> [Output] {
        var outputs: [Output] = []
        if let move = moved(to: point) { outputs.append(move) }
        if let startedAt, travelled <= tapSlop, time - startedAt <= tapMaxDuration {
            outputs = [.tap]
        }
        last = nil
        startedAt = nil
        travelled = 0
        return outputs
    }

    public mutating func cancel() {
        last = nil
        startedAt = nil
        travelled = 0
    }
}
