package com.metrolist.music.lyrics

import com.metrolist.music.constants.VerciFont
import com.metrolist.music.constants.VerciStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class VerciStudioTest {
    @Test fun `English word forms and Hindi vowel marks match emojis`() {
        assertEquals(verciEmoji("love"), verciEmoji("loving"))
        assertEquals("🐈", verciEmoji("CATS!"))
        assertEquals("📖", verciEmoji("stories"))
        assertEquals("🌳", verciEmoji("leaves"))
        assertEquals("👀", verciEmoji("आँखें"))
        assertEquals("❤️", verciEmoji("प्यार"))
        assertEquals("🌙", verciEmoji("रातों"))
        assertEquals("", verciEmoji("unmatchedword"))
    }
    @Test fun `advanced emojis are optional and match Hindi and plural forms`() {
        assertEquals("🫪", verciEmoji("panic", true))
        assertEquals("🫯", verciEmoji("brawls", true))
        assertEquals("🐈", verciEmoji("CATS", true, false))
        assertEquals("😡", verciEmoji("angry", true, false))
        assertEquals("😡", verciEmoji("angry", true))
        assertEquals("😡", verciEmoji("गुस्सा!", true))
        assertEquals("🧛🏻", verciEmoji("vampires", true))
        assertEquals("", verciEmoji("vampires"))
        assertEquals("🖕🏻", verciEmoji("fucking", true))
        assertEquals("", verciEmoji("hello", true))
    }
    @Test fun `extended presets preserve all optional effects and read legacy presets`() {
        val p = VerciPreset(VerciStyle.SHIP, VerciFont.BOLD, 52f, 1f, .8f, 0, true, false, true, true, true, true, true)
        assertEquals(p, VerciPreset.decode(p.encode()))
        assertEquals(p.copy(size = 160f), VerciPreset.decode(p.copy(size = 160f).encode()))
        assertEquals(p.copy(size = 200f), VerciPreset.decode(p.copy(size = 200f).encode()))
        assertNull(VerciPreset.decode(p.copy(size = 201f).encode()))
        val stack = p.copy(style = VerciStyle.NEON_STACK, spacing = .45f)
        assertEquals(stack, VerciPreset.decode(stack.encode()))
        assertFalse(VerciPreset.decode(p.encode().split('|').take(9).joinToString("|"))!!.advanced)
    }
    @Test fun `PCM level reads are non destructive and silence stays zero`() {
        val buffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        repeat(256) { buffer.putShort(8192.toShort()) }
        buffer.flip()
        val bytes = buffer.array().clone()
        assertEquals(.75f, pcmEnergy(buffer, 2, false), .001f)
        assertEquals(0, buffer.position())
        assertArrayEquals(bytes, buffer.array())
        assertEquals(0f, pcmEnergy(ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN), 2, false), 0f)
    }
    @Test fun `floating PCM rejects non finite samples`() {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(Float.NaN).putFloat(0f).putFloat(0f).putFloat(0f).flip()
        assertEquals(0f, pcmEnergy(buffer, 4, true), 0f)
    }
    @Test fun `presets round trip and malformed values are rejected`() {
        val p = VerciPreset(VerciStyle.SHIP, VerciFont.ROUNDED, 56f, 1.1f, .8f, 2, true, true, true)
        assertEquals(p, VerciPreset.decode(p.encode()))
        assertNull(VerciPreset.decode(p.encode().replace("56.0", "NaN")))
        assertNull(VerciPreset.decode("broken"))
        assertNull(VerciPreset.decode(p.encode().replace("ROUNDED", "UNKNOWN")))
    }
}
