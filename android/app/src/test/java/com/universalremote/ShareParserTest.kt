package com.universalremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareParserTest {

    @Test
    fun `enlaces de YouTube devuelven el id del video`() {
        val expected = SharedMedia(Kind.YOUTUBE, "abc123")
        assertEquals(expected, parseSharedUrl("https://youtu.be/abc123"))
        assertEquals(expected, parseSharedUrl("https://www.youtube.com/watch?v=abc123"))
        assertEquals(expected, parseSharedUrl("https://m.youtube.com/watch?feature=share&v=abc123"))
        assertEquals(expected, parseSharedUrl("https://youtube.com/shorts/abc123?si=xyz"))
    }

    @Test
    fun `la URL se encuentra dentro del texto compartido`() {
        assertEquals(
            SharedMedia(Kind.YOUTUBE, "abc123"),
            parseSharedUrl("Mira este video: https://youtu.be/abc123?si=TRACK gracioso"),
        )
    }

    @Test
    fun `otras URL se reconocen como OTHER`() {
        assertEquals(SharedMedia(Kind.OTHER, "https://example.com/x"), parseSharedUrl("https://example.com/x"))
    }

    @Test
    fun `texto sin URL devuelve null`() {
        assertNull(parseSharedUrl("hola, sin enlaces"))
    }
}
