package com.mochyy.mochymusic.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class SincResamplerTest {

    private fun tone(rate: Int, hz: Double, seconds: Double, amplitude: Double = 0.5): FloatArray =
        FloatArray((rate * seconds).toInt()) { (amplitude * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun run(resampler: SincResampler, input: FloatArray, chunk: Int = input.size): FloatArray {
        val out = ArrayList<Float>()
        var at = 0
        while (at < input.size) {
            val n = minOf(chunk, input.size - at)
            val plane = arrayOf(input.copyOfRange(at, at + n))
            val target = arrayOf(FloatArray(resampler.maxOutputFrames(n)))
            val written = resampler.process(plane, n, target)
            for (i in 0 until written) out += target[0][i]
            at += n
        }
        return out.toFloatArray()
    }

    private fun rms(samples: FloatArray, from: Int, to: Int): Double =
        sqrt((from until to).sumOf { samples[it].toDouble() * samples[it] } / (to - from))

    // a 1 khz tone comes out of 44.1 -> 48 at the same level, at the right length
    @Test
    fun keepsAudibleTonesWhole() {
        val resampler = SincResampler.create(44_100, 48_000, 1)!!
        val input = tone(44_100, 1_000.0, 1.0)
        val out = run(resampler, input)
        assertEquals(48_000.0, out.size.toDouble(), 2.0)
        val level = rms(out, 2_000, out.size - 2_000) / rms(input, 2_000, input.size - 2_000)
        assertEquals(1.0, level, 0.001)
    }

    // the top of what anyone hears, 20 khz, comes through at full level
    @Test
    fun flatTo20k() {
        val input = tone(44_100, 20_000.0, 1.0)
        val out = run(SincResampler.create(44_100, 48_000, 1)!!, input)
        val db = 20 * log10(rms(out, 4_000, out.size - 4_000) / rms(input, 4_000, input.size - 4_000))
        assertTrue("20 khz is  dB", abs(db) < 0.01)
    }

    // a 30 khz tone in a 96 khz file can't exist at 48 khz: it must vanish, not fold down to 18 khz
    @Test
    fun rejectsWhatWouldAlias() {
        val resampler = SincResampler.create(96_000, 48_000, 1)!!
        val out = run(resampler, tone(96_000, 30_000.0, 0.5))
        val db = 20 * log10(rms(out, 2_000, out.size - 2_000) / (0.5 / sqrt(2.0)))
        assertTrue("aliasing only ${"%.1f".format(db)} dB down", db < -110)
    }

    // the player hands buffers of any size: cutting the song up must not change a single sample
    @Test
    fun chunksMatchOneBlock() {
        val input = tone(44_100, 3_000.0, 0.3)
        val whole = run(SincResampler.create(44_100, 48_000, 1)!!, input)
        val pieces = run(SincResampler.create(44_100, 48_000, 1)!!, input, chunk = 333)
        assertEquals(whole.size, pieces.size)
        for (i in whole.indices) assertTrue(abs(whole[i] - pieces[i]) < 1e-6f)
    }
}
