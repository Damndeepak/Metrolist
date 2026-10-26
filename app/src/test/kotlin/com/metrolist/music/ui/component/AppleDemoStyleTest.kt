package com.metrolist.music.ui.component

import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.WordTimestamp
import org.junit.Assert.*
import org.junit.Test

class AppleDemoStyleTest {
    @Test fun wordFillKeepsPastWordsWhiteAndFutureWordsDimAndRewindsOnSeek() {
        val entry = LyricsEntry(1000, "Hello hello", listOf(
            WordTimestamp("Hello", 1.0, 2.0), WordTimestamp("hello", 2.0, 3.0)))
        val words = deeplistMotionWords(entry, 3000)
        assertEquals(.5f, appleDemoWordFill(1500, words[0].start, words[0].end), .001f)
        assertEquals(0f, appleDemoWordFill(1500, words[1].start, words[1].end), 0f)
        assertEquals(1f, appleDemoWordFill(2500, words[0].start, words[0].end), 0f)
        assertEquals(0f, appleDemoWordFill(900, words[0].start, words[0].end), 0f)
        assertTrue(appleDemoWordFill(1000, 1000, 1000).isFinite())
        assertEquals(1f, appleDemoWordFill(2000, 1000, 1001), 0f)
    }

    @Test fun scrollingMatchesWebsiteDurationAt60And120HzAndCanRetarget() {
        fun sample(hz: Int): Float {
            val scroll = AppleDemoScroll()
            scroll.reset(0f)
            scroll.advance(200f, 0L)
            for (i in 1..hz / 2) scroll.advance(200f, i * 1_000_000_000L / hz)
            val middle = scroll.position
            assertTrue(middle in 180f..200f)
            assertEquals(200f, scroll.advance(200f, 700_000_000L), 0f)
            scroll.advance(400f, 800_000_000L)
            scroll.advance(400f, 900_000_000L)
            val before = scroll.position
            assertEquals(before, scroll.advance(50f, 900_000_000L), .05f)
            assertEquals(50f, scroll.advance(50f, 1_600_000_000L), 0f)
            scroll.reset(100f)
            assertEquals(100f, scroll.position, 0f)
            assertEquals(20f, scroll.advance(20f, 2_000_000_000L, intensity = 0f), 0f)
            return middle
        }
        assertEquals(sample(60), sample(120), .05f)
    }

    @Test fun neighbourAndFarLineOpacityMatchTheDemo() {
        assertEquals(1f, appleDemoLineAlpha(0f), 0f)
        assertEquals(.8f, appleDemoLineAlpha(1f), 0f)
        assertEquals(.55f, appleDemoLineAlpha(2f), .001f)
        assertEquals(.55f, appleDemoLineAlpha(20f), .001f)
    }
}
