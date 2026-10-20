import Testing
@testable import RemoteCore

struct ProntoHexTests {
    // NEC abreviado: líder 9 ms / 4.5 ms, un bit, y secuencia de repetición.
    let necSample = "0000 006D 0002 0001 0157 00AC 0015 0016 0015 05ED"

    @Test func parsesCarrierAndDurations() throws {
        let signal = try ProntoHex.parse(necSample)
        #expect(signal.carrierFrequency == 38029)
        #expect(signal.isModulated)
        #expect(signal.introPattern == [9019, 4523, 552, 579])
        #expect(signal.repeatPattern == [552, 39891])
    }

    @Test func transmissionPatternAppendsRepeats() throws {
        let signal = try ProntoHex.parse(necSample)
        #expect(signal.transmissionPattern() == [9019, 4523, 552, 579])
        #expect(signal.transmissionPattern(repeats: 2) == [9019, 4523, 552, 579, 552, 39891, 552, 39891])
    }

    @Test func repeatOnlyCodeEmitsRepeatOnce() throws {
        let signal = try ProntoHex.parse("0000 006D 0000 0001 0015 05ED")
        #expect(signal.introPattern.isEmpty)
        #expect(signal.transmissionPattern() == [552, 39891])
    }

    @Test func roundTripsThroughEncoder() throws {
        let signal = try ProntoHex.parse(necSample)
        #expect(try ProntoHex.encode(signal) == necSample)
    }

    @Test func acceptsLowercaseAndPrefixes() throws {
        let signal = try ProntoHex.parse("0x0000,0x006d,0x0000,0x0001,0x0015,0x05ed")
        #expect(signal.repeatPattern == [552, 39891])
    }

    @Test(arguments: [
        "0000 006D 0002",                       // cabecera incompleta
        "0000 006D 0002 0001 0157 00AC",        // longitud no coincide
        "5000 0073 0000 0001 0001 0001",        // RC5 codificado, no raw
        "0000 0000 0000 0001 0015 05ED",        // frecuencia 0
        "0000 006D 0000 0001 0015 0000",        // duración cero
        "0000 006D 0000 0001 0015 ZZZZ",        // hex inválido
    ])
    func rejectsMalformedCodes(code: String) {
        #expect(throws: RemoteError.self) { try ProntoHex.parse(code) }
    }
}
