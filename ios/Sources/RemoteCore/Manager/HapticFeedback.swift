import Foundation

@MainActor
public protocol HapticFeedbackProviding: AnyObject {
    func play(_ weight: HapticWeight)
    func playFailure()
}

#if canImport(UIKit)
import UIKit

/// Generadores pre-calentados por intensidad. Los pulsos `light` se limitan a uno cada 30 ms
/// para que mantener pulsado VOL+/D-Pad no sature el Taptic Engine.
@MainActor
public final class UIKitHaptics: HapticFeedbackProviding {
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private let notification = UINotificationFeedbackGenerator()
    private let clock = ContinuousClock()
    private var lastLightPulse: ContinuousClock.Instant?
    private let lightPulseInterval: Duration = .milliseconds(30)

    public init() {
        selection.prepare()
        light.prepare()
        medium.prepare()
        heavy.prepare()
        notification.prepare()
    }

    public func play(_ weight: HapticWeight) {
        switch weight {
        case .none:
            return
        case .selection:
            selection.selectionChanged()
            selection.prepare()
        case .light:
            let now = clock.now
            if let lastLightPulse, now - lastLightPulse < lightPulseInterval { return }
            lastLightPulse = now
            light.impactOccurred()
            light.prepare()
        case .medium:
            medium.impactOccurred()
            medium.prepare()
        case .heavy:
            heavy.impactOccurred(intensity: 1.0)
            heavy.prepare()
        }
    }

    public func playFailure() {
        notification.notificationOccurred(.error)
        notification.prepare()
    }
}
#endif
