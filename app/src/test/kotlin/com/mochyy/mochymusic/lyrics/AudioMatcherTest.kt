package com.mochyy.mochymusic.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class AudioMatcherTest {

    // made-up music: a new chord every quarter second, each note a few harmonics, at 44.1 kHz stereo
    private fun music(seconds: Int, seed: Int, rate: Int = 44_100): FloatArray {
        val random = Random(seed)
        val out = FloatArray(seconds * rate)
        var at = 0
        while (at < out.size) {
            val notes = List(3) { 110.0 * Math.pow(2.0, random.nextInt(36) / 12.0) }
            val length = minOf(rate / 4, out.size - at)
            for (i in 0 until length) {
                val t = (at + i).toDouble() / rate
                out[at + i] = notes.sumOf { f -> sin(2 * PI * f * t) * 0.15 + sin(4 * PI * f * t) * 0.05 }.toFloat()
            }
            at += length
        }
        return out
    }

    private fun prints(samples: FloatArray, rate: Int = 44_100, gain: Float = 1f): AudioPrints {
        val printer = AudioPrinter()
        var at = 0
        while (at < samples.size) {
            val end = minOf(samples.size, at + 4_096)
            val block = ShortBuffer.allocate((end - at) * 2)
            for (i in at until end) {
                val value = (samples[i] * gain * 20_000).toInt().coerceIn(-32_768, 32_767).toShort()
                block.put(value).put(value)
            }
            block.flip()
            printer.feed(block, 2, rate, at * 1_000_000L / rate)
            at = end
        }
        return printer.finish()!!
    }

    @Test
    fun findsTheSongInsideAVideoWithAnIntroAndASkit() {
        val song = music(120, seed = 1)
        val rate = 44_100
        // the video: a 70 s intro, the first 50 s of the song, a 20 s skit, then the rest
        val video = music(70, seed = 2) + song.copyOfRange(0, 50 * rate) + music(20, seed = 3) + song.copyOfRange(50 * rate, song.size)
        val map = assertNotNullMap(AudioMatcher.match(prints(song), prints(video, gain = 0.6f)))
        assertEquals(2, map.runs.size)
        assertTrue(abs(map.deltaAt(20_000) - 70_000) <= 100)
        assertTrue(abs(map.deltaAt(90_000) - 90_000) <= 100)
        assertTrue(abs(map.runs[0].toSongMs - 50_000) <= 300)
    }

    @Test
    fun saysNothingForAnotherRecording() {
        assertNull(AudioMatcher.match(prints(music(90, seed = 4)), prints(music(150, seed = 5))))
    }

    private fun assertNotNullMap(map: SongVideoMap?): SongVideoMap {
        assertNotNull(map)
        return map!!
    }
}
