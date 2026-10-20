import Foundation

/// Señal IR lista para transmitir: portadora + duraciones alternas encendido/apagado en µs.
public struct IRSignal: Sendable, Equatable {
    /// Frecuencia portadora en Hz (≈30–60 kHz; 38 kHz típico).
    public let carrierFrequency: Int
    /// Secuencia de una sola vez (empieza con pulso encendido).
    public let introPattern: [Int]
    /// Secuencia que se repite mientras el botón sigue pulsado.
    public let repeatPattern: [Int]
    /// `false` para Pronto `0100` (sin modulación).
    public let isModulated: Bool

    public init(carrierFrequency: Int, introPattern: [Int], repeatPattern: [Int], isModulated: Bool = true) {
        self.carrierFrequency = carrierFrequency
        self.introPattern = introPattern
        self.repeatPattern = repeatPattern
        self.isModulated = isModulated
    }

    /// Patrón para `ConsumerIrManager.transmit(freq, pattern)` o un puente IR:
    /// intro + `repeats` copias de la secuencia de repetición. Si no hay intro, la secuencia
    /// de repetición se emite al menos una vez.
    public func transmissionPattern(repeats: Int = 0) -> [Int] {
        let copies = introPattern.isEmpty ? max(repeats, 1) : max(repeats, 0)
        var pattern = introPattern
        for _ in 0..<copies { pattern += repeatPattern }
        return pattern
    }

    public var totalDurationMicroseconds: Int {
        transmissionPattern().reduce(0, +)
    }
}

/// Conversión del formato Pronto Hex (CCF) "raw" `0000`/`0100`.
///
/// Estructura: `[formato] [código de frecuencia] [pares intro] [pares repetición] [duraciones…]`.
/// - Periodo de portadora = código × 0.241246 µs  → frecuencia = 1 000 000 / periodo.
/// - Cada duración está expresada en ciclos de portadora → µs = ciclos × periodo.
public enum ProntoHex {
    public static let clockMicroseconds = 0.241246

    public static func parse(_ code: String) throws -> IRSignal {
        let tokens = code.split(whereSeparator: { $0.isWhitespace || $0 == "," })
        guard tokens.count >= 4 else {
            throw RemoteError.invalidProntoCode("se esperaban al menos 4 palabras, hay \(tokens.count)")
        }

        var words: [Int] = []
        words.reserveCapacity(tokens.count)
        for (index, token) in tokens.enumerated() {
            let digits = token.hasPrefix("0x") || token.hasPrefix("0X") ? token.dropFirst(2) : token[...]
            guard (1...4).contains(digits.count), let word = UInt16(digits, radix: 16) else {
                throw RemoteError.invalidProntoCode("palabra \(index) inválida: \(token)")
            }
            words.append(Int(word))
        }

        let format = words[0]
        guard format == 0x0000 || format == 0x0100 else {
            let hex = String(format, radix: 16, uppercase: true)
            throw RemoteError.invalidProntoCode(
                "formato 0x\(String(repeating: "0", count: max(0, 4 - hex.count)))\(hex) no es raw (solo 0000/0100)"
            )
        }

        let frequencyCode = words[1]
        guard frequencyCode > 0 else {
            throw RemoteError.invalidProntoCode("código de frecuencia 0")
        }

        let introPairs = words[2]
        let repeatPairs = words[3]
        guard introPairs + repeatPairs > 0 else {
            throw RemoteError.invalidProntoCode("sin pares de ráfaga")
        }
        let expected = 4 + 2 * (introPairs + repeatPairs)
        guard words.count == expected else {
            throw RemoteError.invalidProntoCode("se esperaban \(expected) palabras según la cabecera, hay \(words.count)")
        }

        let periodMicros = Double(frequencyCode) * clockMicroseconds
        let durations = try words[4...].enumerated().map { offset, cycles -> Int in
            guard cycles > 0 else {
                throw RemoteError.invalidProntoCode("duración cero en la posición \(offset + 4)")
            }
            return Int((Double(cycles) * periodMicros).rounded())
        }

        let introLength = introPairs * 2
        return IRSignal(
            carrierFrequency: Int((1_000_000 / periodMicros).rounded()),
            introPattern: Array(durations[..<introLength]),
            repeatPattern: Array(durations[introLength...]),
            isModulated: format == 0x0000
        )
    }

    /// Codifica una señal de vuelta a Pronto raw (útil para exportar códigos aprendidos).
    public static func encode(_ signal: IRSignal) throws -> String {
        guard signal.carrierFrequency > 0 else {
            throw RemoteError.invalidProntoCode("frecuencia portadora 0")
        }
        guard signal.introPattern.count.isMultiple(of: 2), signal.repeatPattern.count.isMultiple(of: 2) else {
            throw RemoteError.invalidProntoCode("las secuencias deben tener pares encendido/apagado")
        }
        let frequencyCode = Int((1_000_000 / (Double(signal.carrierFrequency) * clockMicroseconds)).rounded())
        let periodMicros = Double(frequencyCode) * clockMicroseconds
        let cycles = (signal.introPattern + signal.repeatPattern).map { max(1, Int((Double($0) / periodMicros).rounded())) }
        let words = [
            signal.isModulated ? 0x0000 : 0x0100,
            frequencyCode,
            signal.introPattern.count / 2,
            signal.repeatPattern.count / 2,
        ] + cycles

        guard words.allSatisfy({ $0 <= 0xFFFF }) else {
            throw RemoteError.invalidProntoCode("duración fuera de rango para 16 bits")
        }
        return words.map { word in
            let hex = String(word, radix: 16, uppercase: true)
            return String(repeating: "0", count: max(0, 4 - hex.count)) + hex
        }.joined(separator: " ")
    }
}
