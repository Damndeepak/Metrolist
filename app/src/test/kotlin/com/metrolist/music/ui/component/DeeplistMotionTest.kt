package com.metrolist.music.ui.component

import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeeplistMotionTest {
    @Test fun lineOnlyLyricsHaveBoundedSequentialHighlights() {
        val words = deeplistMotionWords(LyricsEntry(1000L, "Hello world"), 5000L)
        assertEquals(2, words.size)
        assertEquals(1000L, words.first().start)
        assertEquals(5000L, words.last().end)
        assertEquals(words.first().end, words.last().start)
        assertTrue(deeplistMotionWords(LyricsEntry(0L, " "), null).isEmpty())
    }
    @Test fun nativeSyllablesAndRepeatedWordsKeepTheirRealTiming() {
        val entry = LyricsEntry(1000L, "Hello hello", listOf(
            WordTimestamp("Hel", 1.0, 1.2), WordTimestamp("lo", 1.2, 1.7),
            WordTimestamp("hello", 2.0, 2.8)))
        val words = deeplistMotionWords(entry, 5000L)
        assertEquals(1000L, words[0].start)
        assertEquals(1700L, words[0].end)
        assertEquals(2000L, words[1].start)
        assertEquals(2800L, words[1].end)
    }
    @Test fun arrivalStaggersAreClampedAndFinishForBothModes() {
        assertEquals(0f, deeplistArrival(-1L, 0, true), 0.001f)
        assertEquals(1f, deeplistArrival(2000L, 3, true), 0.001f)
        assertEquals(1f, deeplistArrival(2000L, 3, false), 0.001f)
        assertTrue(deeplistArrival(100L, 0, true) > deeplistArrival(100L, 2, true))
    }
    @Test fun letterMotionKeepsCombiningMarksAndSurrogatePairsTogether() {
        val text = "A\u0301 \uD83D\uDE00"
        val ranges = deeplistLetterRanges(text)
        assertEquals(listOf(0..1, 2..2, 3..4), ranges)
        assertEquals(text, ranges.joinToString("") { text.substring(it) })
        assertEquals(1f, deeplistArrival(1500L, 95, true), 0.001f)
    }
}
