import Foundation

/// Evento genérico emitido por la UI. Cada `RemoteAdapter` lo traduce a su protocolo.
public enum RemoteCommand: Hashable, Sendable {
    // Energía
    case power, powerOn, powerOff
    // Audio
    case volumeUp, volumeDown, mute
    // Canales
    case channelUp, channelDown
    case digit(UInt8)
    // Navegación
    case dpadUp, dpadDown, dpadLeft, dpadRight, dpadCenter
    case back, home, menu, info, input, search
    // Multimedia
    case playPause, play, pause, stop, rewind, fastForward, next, previous
    // Puntero / touchpad (deltas en puntos de pantalla, +y hacia abajo)
    case touchMove(dx: Double, dy: Double)
    case touchTap
    case scroll(dx: Double, dy: Double)
    // Texto
    case text(String)
    case backspace, enter
    /// Tecla específica del dispositivo (p. ej. `InputHDMI1` en Roku).
    case raw(String)
}

/// Familia funcional a la que pertenece un comando; los adaptadores la declaran como capacidad.
public enum RemoteCapability: String, Sendable, Hashable, CaseIterable {
    case power, wake, volume, channels, digits, navigation, media, pointer, keyboard
}

/// Intensidad háptica según la criticidad del botón.
public enum HapticWeight: Sendable, Equatable {
    case none, selection, light, medium, heavy
}

extension RemoteCommand {
    /// Identificador estable usado por la UI (`CMD_POWER`, `CMD_VOL_UP`, …).
    public var wireName: String {
        switch self {
        case .power: "CMD_POWER"
        case .powerOn: "CMD_POWER_ON"
        case .powerOff: "CMD_POWER_OFF"
        case .volumeUp: "CMD_VOL_UP"
        case .volumeDown: "CMD_VOL_DOWN"
        case .mute: "CMD_MUTE"
        case .channelUp: "CMD_CH_UP"
        case .channelDown: "CMD_CH_DOWN"
        case .digit(let value): "CMD_DIGIT_\(value)"
        case .dpadUp: "CMD_DPAD_UP"
        case .dpadDown: "CMD_DPAD_DOWN"
        case .dpadLeft: "CMD_DPAD_LEFT"
        case .dpadRight: "CMD_DPAD_RIGHT"
        case .dpadCenter: "CMD_DPAD_CENTER"
        case .back: "CMD_BACK"
        case .home: "CMD_HOME"
        case .menu: "CMD_MENU"
        case .info: "CMD_INFO"
        case .input: "CMD_INPUT"
        case .search: "CMD_SEARCH"
        case .playPause: "CMD_PLAY_PAUSE"
        case .play: "CMD_PLAY"
        case .pause: "CMD_PAUSE"
        case .stop: "CMD_STOP"
        case .rewind: "CMD_REWIND"
        case .fastForward: "CMD_FAST_FORWARD"
        case .next: "CMD_NEXT"
        case .previous: "CMD_PREVIOUS"
        case .touchMove: "CMD_TOUCH_MOVE"
        case .touchTap: "CMD_TOUCH_TAP"
        case .scroll: "CMD_SCROLL"
        case .text: "CMD_TEXT"
        case .backspace: "CMD_BACKSPACE"
        case .enter: "CMD_ENTER"
        case .raw(let key): "CMD_RAW_\(key)"
        }
    }

    /// Comandos sin parámetros, indexados por `wireName`.
    public static let parameterless: [RemoteCommand] = [
        .power, .powerOn, .powerOff, .volumeUp, .volumeDown, .mute, .channelUp, .channelDown,
        .dpadUp, .dpadDown, .dpadLeft, .dpadRight, .dpadCenter,
        .back, .home, .menu, .info, .input, .search,
        .playPause, .play, .pause, .stop, .rewind, .fastForward, .next, .previous,
        .touchTap, .backspace, .enter,
    ]

    private static let byWireName: [String: RemoteCommand] = Dictionary(
        uniqueKeysWithValues: parameterless.map { ($0.wireName, $0) }
    )

    /// Construye un comando a partir del identificador que emite la UI.
    /// - Parameters:
    ///   - dx, dy: deltas para `CMD_TOUCH_MOVE` y `CMD_SCROLL`.
    ///   - text: contenido para `CMD_TEXT`.
    public init?(wireName: String, dx: Double = 0, dy: Double = 0, text: String? = nil) {
        if let command = Self.byWireName[wireName] {
            self = command
            return
        }
        switch wireName {
        case "CMD_TOUCH_MOVE":
            self = .touchMove(dx: dx, dy: dy)
        case "CMD_SCROLL":
            self = .scroll(dx: dx, dy: dy)
        case "CMD_TEXT":
            guard let text, !text.isEmpty else { return nil }
            self = .text(text)
        default:
            if wireName.hasPrefix("CMD_DIGIT_"),
               let value = UInt8(wireName.dropFirst("CMD_DIGIT_".count)), value <= 9 {
                self = .digit(value)
            } else if wireName.hasPrefix("CMD_RAW_") {
                let key = String(wireName.dropFirst("CMD_RAW_".count))
                guard !key.isEmpty else { return nil }
                self = .raw(key)
            } else {
                return nil
            }
        }
    }

    public var requiredCapability: RemoteCapability {
        switch self {
        case .power, .powerOn, .powerOff: .power
        case .volumeUp, .volumeDown, .mute: .volume
        case .channelUp, .channelDown: .channels
        case .digit: .digits
        case .dpadUp, .dpadDown, .dpadLeft, .dpadRight, .dpadCenter,
             .back, .home, .menu, .info, .input, .search, .raw: .navigation
        case .playPause, .play, .pause, .stop, .rewind, .fastForward, .next, .previous: .media
        case .touchMove, .touchTap, .scroll: .pointer
        case .text, .backspace, .enter: .keyboard
        }
    }

    public var hapticWeight: HapticWeight {
        switch self {
        case .power, .powerOn, .powerOff: .heavy
        case .home, .back, .input, .mute, .dpadCenter, .touchTap, .enter: .medium
        case .volumeUp, .volumeDown, .channelUp, .channelDown, .digit,
             .dpadUp, .dpadDown, .dpadLeft, .dpadRight,
             .playPause, .play, .pause, .stop, .rewind, .fastForward, .next, .previous: .light
        case .menu, .info, .search, .backspace, .text, .raw: .selection
        case .touchMove, .scroll: .none
        }
    }

    /// Comandos de alta frecuencia que el gestor fusiona antes de encolar.
    public var isContinuous: Bool {
        switch self {
        case .touchMove, .scroll: true
        default: false
        }
    }

    /// Comandos que pueden enviarse sin conexión porque el adaptador intentará despertar el equipo.
    public var canWakeDevice: Bool {
        switch self {
        case .power, .powerOn: true
        default: false
        }
    }
}
