package com.mochyy.mochymusic.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class LyricsAutoSyncTest {

    /** A sung word: when it really is in the audio, ms. */
    private class Word(val startMs: Long, val endMs: Long)

    /**
     * Where the singing goes: an 8 s intro, verses of short lines with breaths between words, a
     * 12 s instrumental break in the middle, then more verses.
     */
    private fun songWords(random: Random): List<List<Word>> {
        val lines = ArrayList<List<Word>>()
        var t = 8_000L
        repeat(18) { line ->
            if (line == 9) t += 12_000L
            val words = ArrayList<Word>()
            repeat(3 + random.nextInt(4)) {
                val length = 220L + random.nextInt(380)
                words += Word(t, t + length)
                t += length + 40L + random.nextInt(120)
            }
            lines += words
            t += 900L + random.nextInt(2_600)
        }
        return lines
    }

    /**
     * A stereo mix at 44.1 kHz: kick and bass in the middle (below the voice's band), hats panned
     * hard left and right, a wide detuned pad, a centre lead synth during the break (a voice-like
     * decoy) and the voice itself, in the middle, during every word.
     */
    private fun synthSong(lines: List<List<Word>>, seconds: Int, random: Random): ShortArray {
        val rate = 44_100
        val left = FloatArray(rate * seconds)
        val right = FloatArray(rate * seconds)
        // kick every half second, bass under it
        var beat = 0.0
        while (beat < seconds) {
            val start = (beat * rate).toInt()
            for (i in 0 until (0.15 * rate).toInt()) {
                val n = start + i
                if (n >= left.size) break
                val v = 0.45f * exp(-i / (0.04 * rate)).toFloat() * sin(2 * PI * 60.0 * i / rate).toFloat()
                left[n] += v; right[n] += v
            }
            val hat = ((beat + 0.25) * rate).toInt()
            val toLeft = random.nextBoolean()
            for (i in 0 until (0.05 * rate).toInt()) {
                val n = hat + i
                if (n >= left.size) break
                val v = 0.12f * exp(-i / (0.01 * rate)).toFloat() * (random.nextFloat() * 2f - 1f)
                if (toLeft) left[n] += v else right[n] += v
            }
            beat += 0.5
        }
        for (n in left.indices) {
            val t = n.toDouble() / rate
            val bass = 0.2f * sin(2 * PI * 55.0 * t).toFloat()
            left[n] += bass + 0.05f * (sin(2 * PI * 196.0 * t) + sin(2 * PI * 247.0 * t + 1.0) + sin(2 * PI * 294.0 * t + 2.0)).toFloat()
            right[n] += bass + 0.05f * (sin(2 * PI * 196.6 * t + 2.0) + sin(2 * PI * 247.7 * t) + sin(2 * PI * 294.9 * t + 1.0)).toFloat()
        }
        // the decoy: a centred, pitched lead in the instrumental break
        val breakStart = lines[8].last().endMs + 1_500
        harmonic(left, right, rate, breakStart, breakStart + 8_000, f0 = 392.0, level = 0.16f)
        // the voice
        for (line in lines) for (word in line) {
            harmonic(left, right, rate, word.startMs, word.endMs, f0 = 200.0 + random.nextInt(200), level = 0.22f)
        }
        return ShortArray(left.size * 2) { i ->
            val v = if (i % 2 == 0) left[i / 2] else right[i / 2]
            (v.coerceIn(-1f, 1f) * 32_000).toInt().toShort()
        }
    }

    /** A sung note: a pitch with its harmonics, a little vibrato, soft in and out, the same in both channels. */
    private fun harmonic(left: FloatArray, right: FloatArray, rate: Int, fromMs: Long, toMs: Long, f0: Double, level: Float) {
        val from = (fromMs * rate / 1_000).toInt()
        val to = minOf(left.size, (toMs * rate / 1_000).toInt())
        var phase = 0.0
        for (n in from until to) {
            val i = n - from
            val attack = minOf(1f, i / (0.02f * rate))
            val release = minOf(1f, (to - n) / (0.04f * rate))
            val f = f0 * (1.0 + 0.015 * sin(2 * PI * 5.5 * i / rate))
            phase += 2 * PI * f / rate
            var v = 0.0
            for (h in 1..8) v += sin(phase * h) / h
            val s = (level * attack * release * v).toFloat()
            left[n] += s; right[n] += s
        }
    }

    private fun listen(pcm: ShortArray): VocalActivity {
        val analyzer = VocalActivityAnalyzer()
        var at = 0
        val chunk = 4_096 * 2
        while (at < pcm.size) {
            val count = minOf(chunk, pcm.size - at)
            analyzer.feed(ShortBuffer.wrap(pcm, at, count), 2, 44_100, presentationUs = at / 2 * 1_000_000L / 44_100)
            at += count
        }
        val heard = analyzer.finish()
        assertNotNull("too little audio", heard)
        return heard!!
    }

    /**
     * The lyrics as a provider would send them, [lateMs] behind the audio (a positive offset
     * fixes that) and, with [stretch], timed to a slightly slower version.
     */
    private fun lyrics(lines: List<List<Word>>, lateMs: Long, byWord: Boolean, stretch: Double = 1.0): List<LyricsEntry> =
        lines.map { words ->
            fun at(ms: Long) = (ms * stretch).toLong() + lateMs
            LyricsEntry(
                time = at(words.first().startMs),
                text = words.joinToString(" ") { "la" },
                words = if (byWord) words.map { WordTimestamp("la", at(it.startMs) / 1_000.0, at(it.endMs) / 1_000.0) } else null,
            )
        }

    // one song for every test: synthesising and listening to 80 s of audio takes a moment
    private val lines get() = song.first
    private val vocals get() = song.second

    private companion object {
        val song: Pair<List<List<Word>>, VocalActivity> by lazy {
            val random = Random(11)
            val test = LyricsAutoSyncTest()
            val lines = test.songWords(random)
            lines to test.listen(test.synthSong(lines, seconds = 80, random = random))
        }
    }

    @Test
    fun `finds a late word-synced offset to within 30 ms`() {
        val alignment = LyricsAligner.align(lyrics(lines, lateMs = 700, byWord = true), vocals, 10_000)
        assertNotNull(alignment)
        assertEquals(700.0, alignment!!.offsetMs.toDouble(), 30.0)
        assertTrue("confidence ${alignment.confidence}", alignment.confidence >= 0.55f)
    }

    @Test
    fun `finds an early word-synced offset`() {
        val alignment = LyricsAligner.align(lyrics(lines, lateMs = -1_350, byWord = true), vocals, 10_000)
        assertEquals(-1_350.0, alignment!!.offsetMs.toDouble(), 30.0)
    }

    @Test
    fun `leaves lyrics that are already in time alone`() {
        val alignment = LyricsAligner.align(lyrics(lines, lateMs = 0, byWord = true), vocals, 10_000)
        assertEquals(0.0, alignment!!.offsetMs.toDouble(), 30.0)
    }

    @Test
    fun `finds a line-synced offset to within 60 ms`() {
        for (late in listOf(2_400L, -600L, 0L)) {
            val alignment = LyricsAligner.align(lyrics(lines, lateMs = late, byWord = false), vocals, 10_000)
            assertNotNull("late $late", alignment)
            assertEquals("late $late", late.toDouble(), alignment!!.offsetMs.toDouble(), 60.0)
            assertTrue("late $late: confidence ${alignment.confidence}", alignment.confidence >= 0.55f)
        }
    }

    @Test
    fun `notices lyrics timed to a slower version`() {
        // 0.8% slower: about half a second adrift by the last line
        val alignment = LyricsAligner.align(lyrics(lines, lateMs = 300, byWord = true, stretch = 1.008), vocals, 10_000)
        assertNotNull(alignment)
        assertNotNull("drift", alignment!!.driftMs)
        assertTrue("drift ${alignment.driftMs}", alignment.driftMs!! > 250)
    }

    @Test
    fun `refuses lyrics further off than the offset can go`() {
        // nothing in range is right, so whatever scores best is chance: no answer beats a wrong one
        for (late in listOf(14_000L, -13_000L)) for (byWord in listOf(true, false)) {
            val alignment = LyricsAligner.align(lyrics(lines, lateMs = late, byWord = byWord), vocals, 10_000)
            assertTrue("late $late by word $byWord gave ${alignment?.offsetMs}", alignment == null || alignment.confidence < 0.3f)
        }
    }

    @Test
    fun `needs more than a few lines`() {
        assertNull(LyricsAligner.align(lyrics(lines.take(3), lateMs = 0, byWord = true), vocals, 10_000))
    }
}
