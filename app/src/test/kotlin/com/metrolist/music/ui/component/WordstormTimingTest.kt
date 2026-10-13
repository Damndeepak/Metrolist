package com.metrolist.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordstormTimingTest {
    @Test fun oldTrackLyricsAreRejected() {
        assertNull(wordstormLyricsForTrack("next", "previous", "Old words"))
        assertNull(wordstormLyricsForTrack(null, "previous", "Old words"))
        assertEquals("New words", wordstormLyricsForTrack("next", "next", "New words"))
    }
    @Test fun seekAndInstrumentalGapsKeepCorrectLine() {
        val times = listOf(1000L, 4000L, 9000L)
        assertEquals(-1, wordstormLineIndex(times, 0L))
        assertEquals(0, wordstormLineIndex(times, 3000L))
        assertEquals(1, wordstormLineIndex(times, 4000L))
        assertEquals(2, wordstormLineIndex(times, 20000L))
        assertEquals(0, wordstormLineIndex(times, 1000L))
        assertEquals(-1, wordstormLineIndex(emptyList(), 5000L))
    }
}
