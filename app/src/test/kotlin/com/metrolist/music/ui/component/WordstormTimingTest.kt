package com.metrolist.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordstormTimingTest {
    @Test fun animationProgressIsClampedAcrossSeekAndInvalidDuration() {
        assertEquals(0f, deeplistProgress(50L, 100L, 200L), 0.001f)
        assertEquals(0.5f, deeplistProgress(150L, 100L, 200L), 0.001f)
        assertEquals(1f, deeplistProgress(300L, 100L, 200L), 0.001f)
        assertEquals(0f, deeplistProgress(100L, 100L, 100L), 0.001f)
    }
    @Test fun websiteIntegrationRejectsNonWebAndMalformedLinks() {
        assertEquals("https://example.com/music", deeplistWebsiteUrl(" https://example.com/music "))
        assertNull(deeplistWebsiteUrl("javascript:alert(1)"))
        assertNull(deeplistWebsiteUrl("http://example.com"))
        assertNull(deeplistWebsiteUrl("https://"))
        assertNull(deeplistWebsiteUrl("https://user:password@example.com"))
        assertNull(deeplistWebsiteUrl("not a link"))
    }
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
