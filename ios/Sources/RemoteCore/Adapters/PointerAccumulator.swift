import Foundation

/// Emula un cursor en equipos sin puntero (Roku, IR) convirtiendo deltas acumulados en pasos de D-Pad.
/// Solo emite en el eje dominante y descarta el residuo del otro para evitar diagonales accidentales.
public struct PointerAccumulator: Sendable {
    public enum Step: Sendable, Equatable {
        case up, down, left, right
    }

    public let threshold: Double
    public let maxStepsPerUpdate: Int
    private var x: Double = 0
    private var y: Double = 0

    public init(threshold: Double, maxStepsPerUpdate: Int = 6) {
        precondition(threshold > 0, "threshold debe ser positivo")
        self.threshold = threshold
        self.maxStepsPerUpdate = maxStepsPerUpdate
    }

    public mutating func consume(dx: Double, dy: Double) -> [Step] {
        x += dx
        y += dy
        var steps: [Step] = []
        if abs(x) >= abs(y) {
            while abs(x) >= threshold, steps.count < maxStepsPerUpdate {
                steps.append(x > 0 ? .right : .left)
                x -= x > 0 ? threshold : -threshold
            }
            if !steps.isEmpty { y = 0 }
        } else {
            while abs(y) >= threshold, steps.count < maxStepsPerUpdate {
                steps.append(y > 0 ? .down : .up)
                y -= y > 0 ? threshold : -threshold
            }
            if !steps.isEmpty { x = 0 }
        }
        if steps.count == maxStepsPerUpdate {
            x = 0
            y = 0
        }
        return steps
    }

    public mutating func reset() {
        x = 0
        y = 0
    }
}
