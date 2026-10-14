package com.metrolist.music.lyrics

import org.junit.Assert.*
import org.junit.Test

class VerciHeartbeatTest {
    @Test fun `continuous scroll moves between words and has matching boundary velocity`() {
        val words = (0..5).map { VerciWord("word", it * 1000L, (it + 1) * 1000L, "") }
        assertTrue(verciScrollPosition(words, 400) > .3f)
        val before = verciScrollPosition(words, 1999) - verciScrollPosition(words, 1998)
        val after = verciScrollPosition(words, 2002) - verciScrollPosition(words, 2001)
        assertEquals(before, after, .00001f)
        var previous = -1f
        for (t in 0L..5000L) {
            val position = verciScrollPosition(words, t)
            assertTrue(position >= previous)
            previous = position
        }
    }
    @Test fun `explicit instrumental holds previous phrase without inventing lyrics`() {
        val gaps = buildVerciGaps(listOf(LyricsEntry(1000, "love you"), LyricsEntry(3000, ""), LyricsEntry(8000, "home")))
        assertEquals(listOf(VerciGap(3000, 8000, "love you")), gaps)
    }
    @Test fun `heartbeat detects a transient then decays and resets`() {
        val heartbeat = VerciHeartbeat()
        repeat(100) { heartbeat.sample(.02f, it * 10L, .01f) }
        heartbeat.sample(.8f, 1100, .01f)
        assertTrue(heartbeat.sample(.8f, 1155, .01f) > .8f)
        assertEquals(0f, heartbeat.sample(0f, 4000, .01f), .001f)
        heartbeat.reset()
        assertEquals(0f, heartbeat.sample(0f, 4010, .01f), 0f)
    }
    @Test fun `mega respects toggles and preserves meaning`() {
        assertEquals(emptyList<String>(), verciEmojiBundle("love", false, false, true))
        assertEquals(listOf("❤️"), verciEmojiBundle("love", false, true, false))
        assertEquals(listOf("❤️", "❤️‍🔥"), verciEmojiBundle("love", false, true, true))
        assertEquals(emptyList<String>(), verciEmojiBundle("the", true, true, true))
    }
}
