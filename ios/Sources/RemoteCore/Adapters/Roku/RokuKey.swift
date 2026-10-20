import Foundation

/// Tecla ECP (`POST /keypress/{path}`).
public struct RokuKey: Hashable, Sendable, CustomStringConvertible {
    public let path: String

    private init(path: String) {
        self.path = path
    }

    /// Tecla arbitraria validada (`InputHDMI1`, `FindRemote`…). Rechaza caracteres que alterarían la URL.
    public init?(rawName: String) {
        guard !rawName.isEmpty,
              rawName.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "_") }) else { return nil }
        self.path = rawName
    }

    public var description: String { path }

    public static let home = RokuKey(path: "Home")
    public static let back = RokuKey(path: "Back")
    public static let select = RokuKey(path: "Select")
    public static let up = RokuKey(path: "Up")
    public static let down = RokuKey(path: "Down")
    public static let left = RokuKey(path: "Left")
    public static let right = RokuKey(path: "Right")
    public static let info = RokuKey(path: "Info")
    public static let search = RokuKey(path: "Search")
    public static let play = RokuKey(path: "Play")
    public static let rewind = RokuKey(path: "Rev")
    public static let fastForward = RokuKey(path: "Fwd")
    public static let instantReplay = RokuKey(path: "InstantReplay")
    public static let backspace = RokuKey(path: "Backspace")
    public static let enter = RokuKey(path: "Enter")
    public static let volumeUp = RokuKey(path: "VolumeUp")
    public static let volumeDown = RokuKey(path: "VolumeDown")
    public static let volumeMute = RokuKey(path: "VolumeMute")
    public static let channelUp = RokuKey(path: "ChannelUp")
    public static let channelDown = RokuKey(path: "ChannelDown")
    public static let power = RokuKey(path: "Power")
    public static let powerOn = RokuKey(path: "PowerOn")
    public static let powerOff = RokuKey(path: "PowerOff")

    private static let unreserved = CharacterSet(
        charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    )

    /// Carácter literal para teclado en pantalla: `Lit_a`, `Lit_%C3%B1`.
    public static func literal(_ character: Character) -> RokuKey {
        let encoded = String(character).addingPercentEncoding(withAllowedCharacters: unreserved) ?? ""
        return RokuKey(path: "Lit_" + encoded)
    }

    public init(_ step: PointerAccumulator.Step) {
        switch step {
        case .up: self = .up
        case .down: self = .down
        case .left: self = .left
        case .right: self = .right
        }
    }

    /// Traducción directa de comandos de tecla única. `nil` = no existe en ECP.
    public init?(command: RemoteCommand) {
        switch command {
        case .power: self = .power
        case .powerOn: self = .powerOn
        case .powerOff: self = .powerOff
        case .volumeUp: self = .volumeUp
        case .volumeDown: self = .volumeDown
        case .mute: self = .volumeMute
        case .channelUp: self = .channelUp
        case .channelDown: self = .channelDown
        case .digit(let value): self = .literal(Character(String(value)))
        case .dpadUp: self = .up
        case .dpadDown: self = .down
        case .dpadLeft: self = .left
        case .dpadRight: self = .right
        case .dpadCenter, .touchTap: self = .select
        case .back: self = .back
        case .home: self = .home
        case .menu, .info: self = .info
        case .search: self = .search
        case .playPause, .play, .pause: self = .play
        case .rewind: self = .rewind
        case .fastForward, .next: self = .fastForward
        case .previous: self = .instantReplay
        case .backspace: self = .backspace
        case .enter: self = .enter
        case .raw(let name):
            guard let key = RokuKey(rawName: name) else { return nil }
            self = key
        case .input, .stop, .touchMove, .scroll, .text:
            return nil
        }
    }
}
