package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerciTimelineTest {
    @Test fun `real syllable timings join into words and ignore backing vocals`() {
        val words = buildVerciWords(listOf(
            LyricsEntry(1000, "Hello world", listOf(
                WordTimestamp("Hel", 1.0, 1.2, false),
                WordTimestamp("lo", 1.2, 1.5, true),
                WordTimestamp("world", 1.5, 2.0),
            )),
            LyricsEntry(1100, "ooh", isBackground = true),
        ))
        assertEquals(listOf("Hello", "world"), words.map { it.text })
        assertEquals(1000L, words.first().start)
        assertEquals(1500L, words.first().end)
        assertFalse(words.any { it.estimated })
    }

    @Test fun `estimated words do not stretch over an instrumental break`() {
        val words = buildVerciWords(listOf(LyricsEntry(1000, "love you"), LyricsEntry(20000, "home")))
        assertTrue(words.first().estimated)
        assertTrue(words[1].end < 4000)
        assertEquals("❤️", words.first().emoji)
        assertEquals(1f, verciScrollPosition(words, 10000), 0f)
    }

    @Test fun `seek rewind and exact boundaries locate the correct word`() {
        val words = buildVerciWords(listOf(LyricsEntry(1000, "one"), LyricsEntry(2000, "two")))
        assertEquals(-1, verciWordIndex(words, 999))
        assertEquals(0, verciWordIndex(words, 1000))
        assertEquals(1, verciWordIndex(words, 2000))
        assertEquals(0, verciWordIndex(words, 1100))
        assertEquals(1f, verciScrollPosition(words, 2000), 0f)
        assertTrue(verciScrollPosition(words, 1999) > .99f)
        assertEquals(-1, verciWordIndex(emptyList(), 0))
    }

    @Test fun `short line gaps and malformed word ends stay finite and ordered`() {
        val words = buildVerciWords(listOf(
            LyricsEntry(1000, "a b c"), LyricsEntry(1001, "d"),
            LyricsEntry(2000, "broken", listOf(WordTimestamp("broken", 2.0, 1.0))),
        ))
        assertTrue(words.all { it.end > it.start })
        assertTrue(words.zipWithNext().all { (a, b) -> a.start <= b.start })
        for (time in 0L..3000L) assertTrue(verciScrollPosition(words, time).isFinite())
    }

    @Test fun `empty lyrics and punctuation are harmless`() {
        assertTrue(buildVerciWords(emptyList()).isEmpty())
        assertTrue(buildVerciWords(listOf(LyricsEntry(0, "♪ —"))).isEmpty())
        assertEquals("❤️", verciEmoji("LOVE!"))
        assertEquals("", verciEmoji("table"))
    }
}
