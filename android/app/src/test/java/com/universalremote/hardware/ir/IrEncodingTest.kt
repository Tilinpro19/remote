package com.universalremote.hardware.ir

import com.universalremote.domain.model.IrCode
import com.universalremote.domain.model.IrProtocol
import com.universalremote.domain.model.RemoteError
import kotlin.math.roundToInt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProntoHexTest {

    private val cycle = 0x6D * 0.241246
    private fun us(cycles: Int) = (cycles * cycle).roundToInt()

    @Test
    fun `learned pronto converts cycles to microseconds`() {
        val pronto = "0000 006D 0002 0001 0010 0020 0010 0040 0010 0400"
        val signal = ProntoHex.parse(pronto)
        assertEquals(38_029, signal.frequency)
        // Pulsación única: sólo la secuencia "once"; la de repetición se añade bajo demanda.
        assertArrayEquals(intArrayOf(us(0x10), us(0x20), us(0x10), us(0x40)), signal.pattern)
        assertArrayEquals(
            intArrayOf(us(0x10), us(0x20), us(0x10), us(0x40), us(0x10), us(0x400)),
            ProntoHex.parse(pronto, repeatCount = 1).pattern,
        )
    }

    @Test
    fun `repeat-only pronto emits repeat sequence at least once`() {
        val signal = ProntoHex.parse("0000 006D 0000 0001 0010 0400")
        assertArrayEquals(intArrayOf(us(0x10), us(0x400)), signal.pattern)
        assertEquals(6, ProntoHex.parse("0000 006D 0000 0001 0010 0400", repeatCount = 3).pattern.size)
    }

    @Test(expected = RemoteError.InvalidCode::class)
    fun `inconsistent length is rejected`() {
        ProntoHex.parse("0000 006D 0002 0001 0010 0020")
    }

    @Test(expected = RemoteError.InvalidCode::class)
    fun `unmodulated pronto is rejected`() {
        ProntoHex.parse("0100 006D 0001 0000 0010 0020")
    }

    @Test(expected = RemoteError.InvalidCode::class)
    fun `non hex word is rejected`() {
        ProntoHex.parse("0000 006D 0001 0000 00G0 0020")
    }

    @Test
    fun `predefined rc5 is re-encoded at 36kHz`() {
        val signal = ProntoHex.parse("5000 0073 0000 0001 0000 000C")
        assertEquals(36_045, signal.frequency)
        assertEquals(IrProtocolEncoder.rc5(0, 12, toggle = false).pattern.toList(), signal.pattern.toList())
    }

    @Test
    fun `predefined nec validates command complement`() {
        val signal = ProntoHex.parse("900A 006D 0000 0001 04FB 08F7")
        assertEquals(IrProtocolEncoder.nec(4, 0xFB, 8).pattern.toList(), signal.pattern.toList())
    }
}

class IrProtocolEncoderTest {

    @Test
    fun `nec frame has header, 32 lsb-first bits, stop bit and 108ms period`() {
        val p = IrProtocolEncoder.nec(device = 4, subdevice = -1, function = 8).pattern
        assertEquals(68, p.size)
        assertEquals(9_024, p[0])
        assertEquals(4_512, p[1])
        assertEquals(564, p[3]) // device bit0 = 0
        assertEquals(564, p[5]) // device bit1 = 0
        assertEquals(1_692, p[7]) // device bit2 = 1
        assertEquals(108_000L, p.fold(0L) { a, b -> a + b })
    }

    @Test
    fun `necx uses short header and repeats device`() {
        val p = IrProtocolEncoder.necx(device = 7, subdevice = -1, function = 2).pattern
        assertEquals(4_512, p[0])
        assertEquals(4_512, p[1])
        // Bytes 0 y 1 idénticos (7): bits 0..2 a 1 en ambos.
        val byte0 = (0 until 8).map { p[3 + 2 * it] }
        val byte1 = (8 until 16).map { p[3 + 2 * it] }
        assertEquals(byte0, byte1)
    }

    @Test
    fun `sony12 sends three 45ms frames`() {
        val signal = IrProtocolEncoder.sony(function = 21, device = 1, extended = -1, deviceBits = 5)
        assertEquals(40_000, signal.frequency)
        assertEquals(78, signal.pattern.size)
        assertEquals(135_000L, signal.totalDurationMicros)
        assertEquals(2_400, signal.pattern[0])
        assertEquals(1_200, signal.pattern[2]) // función 21 = 0b10101, bit0 = 1
        assertEquals(600, signal.pattern[4]) // bit1 = 0
    }

    @Test
    fun `rc5 manchester uses only 1 or 2 half-bit durations and toggles`() {
        val a = IrProtocolEncoder.rc5(0, 12, toggle = false)
        val b = IrProtocolEncoder.rc5(0, 12, toggle = true)
        val body = a.pattern.dropLast(1)
        assertTrue(body.all { it == 889 || it == 1_778 })
        assertNotEquals(a.pattern.toList(), b.pattern.toList())
        assertEquals(36_000, a.frequency)
    }

    @Test
    fun `rc6 starts with 2666us leader`() {
        val p = IrProtocolEncoder.rc6(0, 12, toggle = false).pattern
        assertEquals(2_664, p[0])
        assertTrue(p[1] >= 888)
    }

    @Test
    fun `encode dispatches by protocol`() {
        val code = IrCode(1, "CMD_POWER", IrProtocol.SONY12, device = 1, function = 21)
        assertEquals(IrProtocolEncoder.sony(21, 1, -1, 5), IrProtocolEncoder.encode(code))
    }

    @Test(expected = RemoteError.InvalidCode::class)
    fun `out of range byte is rejected`() {
        IrProtocolEncoder.nec(256, -1, 1)
    }
}
