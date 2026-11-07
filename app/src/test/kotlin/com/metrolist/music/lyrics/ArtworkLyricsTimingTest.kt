package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkLyricsTimingTest {
    private val words = listOf(WordTimestamp("Hello", 1.0, 1.5), WordTimestamp("world", 2.0, 3.0))

    @Test fun highlightsOnlyDuringRealWordIntervals() {
        assertEquals(-1, artworkActiveWord(words, 999))
        assertEquals(0, artworkActiveWord(words, 1000))
        assertEquals(-1, artworkActiveWord(words, 1500))
        assertEquals(1, artworkActiveWord(words, 2000))
        assertEquals(-1, artworkActiveWord(words, 3000))
        assertEquals(0, artworkActiveWord(words, 1200)) // Backward seek.
    }

    @Test fun doesNotInventWordTimingForLineLyrics() {
        assertEquals(-1, artworkActiveWord(null, 1200))
        assertEquals(-1, artworkActiveWord(emptyList(), 1200))
        assertEquals(-1, artworkActiveWord(listOf(WordTimestamp("bad", Double.NaN, 3.0)), 1200))
        assertEquals(-1, artworkActiveWord(listOf(WordTimestamp("bad", 3.0, 1.0)), 1200))
    }
}
