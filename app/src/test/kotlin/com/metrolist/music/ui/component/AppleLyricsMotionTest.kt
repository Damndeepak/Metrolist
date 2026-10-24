package com.metrolist.music.ui.component

import org.junit.Assert.*
import org.junit.Test

class AppleLyricsMotionTest {
    @Test fun `late lyrics move upward without restarting playback`() {
        var times = emptyList<Long>()
        val motion = AppleLyricsMotion { times }
        assertEquals(0f, motion.advance(6500, 0), 0f)
        times = listOf(1000, 3000, 5000, 7000)
        repeat(120) { motion.advance(6500, (it + 1) * 8_333_333L) }
        assertEquals(2f, motion.position, .04f)
        repeat(120) { motion.advance(7500, (it + 121) * 8_333_333L) }
        assertEquals(3f, motion.position, .04f)
    }

    @Test fun `scroll is smooth at 60 and 120 Hz and seeks return to old lyrics`() {
        fun sample(hz: Int): Float {
            val motion = AppleLyricsMotion { listOf(0, 1000, 2000) }
            motion.advance(0, 0)
            val first = motion.advance(1500, 1_000_000_000L / hz)
            assertTrue(first > 0f && first < 1f)
            for (i in 2..hz) motion.advance(1500, i * 1_000_000_000L / hz)
            val end = motion.position
            assertEquals(0f, motion.advance(0, 2_000_000_000L, preview = true), 0f)
            return end
        }
        assertEquals(sample(60), sample(120), .0001f)
    }
}
