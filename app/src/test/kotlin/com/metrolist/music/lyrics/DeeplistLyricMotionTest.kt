package com.metrolist.music.lyrics

import org.junit.Assert.*
import org.junit.Test

class DeeplistLyricMotionTest {
    @Test fun `uneven wrapped lines keep a stable nearest visible row`() {
        val centers = floatArrayOf(45f, 190f, 470f, 580f)
        assertEquals(0, deeplistNearestCenter(centers, -20f))
        assertEquals(1, deeplistNearestCenter(centers, 200f))
        assertEquals(2, deeplistNearestCenter(centers, 450f))
        assertEquals(3, deeplistNearestCenter(centers, 900f))
        assertEquals(0, deeplistNearestCenter(floatArrayOf(), 100f))
    }

    @Test fun `scroll stays bounded and converges at 60 and 120 Hz`() {
        for (hz in listOf(60, 120)) {
            val spring = DeeplistLyricSpring()
            var previous = 0f
            repeat(hz * 2) {
                val next = spring.advance(1f, 1f / hz)
                assertTrue(next in previous..1f)
                previous = next
            }
            assertEquals(1f, spring.position, .001f)
        }
    }

    @Test fun `rapid line changes and reverse direction do not overshoot`() {
        val spring = DeeplistLyricSpring()
        repeat(10) { spring.advance(1f, 1f / 120) }
        val before = spring.position
        val after = spring.advance(2f, 1f / 120)
        assertTrue(after >= before && after - before < .1f)
        repeat(240) { assertTrue(spring.advance(2f, 1f / 120) <= 2f) }
        repeat(240) { assertTrue(spring.advance(0f, 1f / 120) >= 0f) }
        assertEquals(0f, spring.position, .001f)
        spring.reset(7f)
        assertEquals(7f, spring.advance(7f, 1f / 120), 0f)
    }

    @Test fun `word lift attacks and releases continuously outside the word`() {
        assertEquals(0f, deeplistWordEmphasis(999, 1000, 1500), 0f)
        assertEquals(0f, deeplistWordEmphasis(1000, 1000, 1500), 0f)
        assertEquals(1f, deeplistWordEmphasis(1200, 1000, 1500), 0f)
        assertTrue(deeplistWordEmphasis(1550, 1000, 1500) > 0f)
        assertEquals(0f, deeplistWordEmphasis(1720, 1000, 1500), 0f)
        for (now in 990L..1740L) assertTrue(deeplistWordEmphasis(now, 1000, 1500) in 0f..1f)
        assertTrue(deeplistWordEmphasis(1, 0, 1).isFinite())
    }
}
