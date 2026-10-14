package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class VerciPlaybackClockTest {
    @Test fun `coarse samples still advance at every 120 Hz display frame`() {
        val clock = VerciPlaybackClock()
        var previous = -1L
        for (frame in 0..120) {
            val nanos = frame * 8_333_333L
            val raw = (nanos / 100_000_000) * 100
            val position = clock.position(raw, nanos, 1f)
            assertTrue("Frame $frame must move", position > previous)
            assertTrue(kotlin.math.abs(position - nanos / 1_000_000) < 12)
            previous = position
        }
    }

    @Test fun `seeks reset immediately and speed changes use the new speed`() {
        val clock = VerciPlaybackClock()
        clock.position(1000, 0, 1f)
        clock.reset()
        assertEquals(200L, clock.position(200, 100_000_000, 2f))
        assertEquals(400L, clock.position(200, 200_000_000, 2f))
    }

    @Test fun `damping is frame rate independent without overshoot`() {
        var sixty = 0f
        var oneTwenty = 0f
        repeat(60) { sixty = verciDampPosition(sixty, 1f, 16_666_666) }
        repeat(120) { oneTwenty = verciDampPosition(oneTwenty, 1f, 8_333_333) }
        assertEquals(sixty, oneTwenty, .00001f)
        assertTrue(sixty in 0f..1f)
        assertEquals(.5f, verciDampPosition(.5f, 1f, 0), 0f)
    }

    @Test fun `refresh preference selects 120 when available and respects slower hardware`() {
        assertEquals(120f, lyricsRefreshRate(listOf(60f, 90f, 120f, 144f))!!, 0f)
        assertEquals(90f, lyricsRefreshRate(listOf(60f, 90f))!!, 0f)
        assertEquals(60f, lyricsRefreshRate(listOf(60f))!!, 0f)
        assertNull(lyricsRefreshRate(emptyList()))
    }
}
