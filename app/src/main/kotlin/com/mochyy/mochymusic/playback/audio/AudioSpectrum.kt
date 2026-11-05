package com.mochyy.mochymusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The music's spectrum as it's heard, for the visualizer. The clock sink hands every decoded buffer
 * here with its timestamp; while something is drawing it (it asks every frame) the audio is cut
 * into overlapping windows, each transformed and summed into [Bands] bands spaced by pitch, and
 * stamped with its place in the song. The drawing asks for the bands at the player's position, so
 * the bars move with what's heard rather than with what was decoded half a second earlier.
 *
 * Nothing is worked out while nobody asks: a player with no visualizer on screen pays one clock read
 * per buffer.
 */
@UnstableApi
class AudioSpectrum {

    @Volatile private var lastAskedNs = 0L

    // what the sink is being given; written on the playback thread
    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    @Volatile private var streamOffsetUs = 0L

    // samples waiting for a full window, mixed down to one channel
    private val pending = FloatArray(FftSize)
    private var filled = 0
    /** the song time of pending[0], in µs */
    private var pendingStartUs = Long.MIN_VALUE

    // the transform's scratch
    private val re = FloatArray(FftSize)
    private val im = FloatArray(FftSize)
    private val hann = FloatArray(FftSize) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FftSize - 1))).toFloat() }
    private val cosTable = FloatArray(FftSize / 2) { i -> cos(2.0 * PI * i / FftSize).toFloat() }
    private val sinTable = FloatArray(FftSize / 2) { i -> sin(2.0 * PI * i / FftSize).toFloat() }
    private val reversed = IntArray(FftSize).also { table ->
        val bits = log2(FftSize.toDouble()).toInt()
        for (i in table.indices) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    // each band's centre and edges in bins, for the sample rate in use
    private var bandsFor = 0
    private val bandLo = FloatArray(Bands)
    private val bandHi = FloatArray(Bands)
    private val bandTilt = FloatArray(Bands)

    // what's been worked out: a ring of frames, each a song time and its bands
    private val lock = Any()
    private val times = LongArray(History) { Long.MIN_VALUE }
    private val frames = Array(History) { FloatArray(Bands) }
    private var head = 0

    /** the sink's input format: only plain PCM can be read */
    fun configure(format: Format) {
        synchronized(lock) { clear() }
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) {
            encoding = C.ENCODING_INVALID
            return
        }
        sampleRate = format.sampleRate
        channels = format.channelCount
        encoding = format.pcmEncoding
    }

    fun onStreamOffset(offsetUs: Long) { streamOffsetUs = offsetUs }

    /** a seek or a new stream: what came before no longer lines up */
    fun flush() {
        synchronized(lock) { clear() }
    }

    /** a new buffer for the sink, [presentationTimeUs] its first sample's time; it isn't moved */
    fun onBuffer(buffer: ByteBuffer, presentationTimeUs: Long) {
        if (System.nanoTime() - lastAskedNs > ListenWindowNs) {
            filled = 0
            pendingStartUs = Long.MIN_VALUE
            return
        }
        val rate = sampleRate
        val ch = channels
        val bytes = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        if (rate <= 0 || ch <= 0) return
        if (bandsFor != rate) placeBands(rate)

        val startUs = presentationTimeUs - streamOffsetUs
        val frameCount = buffer.remaining() / (bytes * ch)
        // a jump from where the last buffer left off (a gap, a stream change): start the window over
        val expected = if (pendingStartUs == Long.MIN_VALUE) Long.MIN_VALUE else pendingStartUs + filled * 1_000_000L / rate
        if (expected == Long.MIN_VALUE || kotlin.math.abs(startUs - expected) > 80_000L) {
            filled = 0
            pendingStartUs = startUs
        }
        val base = buffer.position()
        val frameBytes = bytes * ch
        for (f in 0 until frameCount) {
            var sum = 0f
            val at = base + f * frameBytes
            for (c in 0 until ch) {
                val i = at + c * bytes
                sum += when (encoding) {
                    C.ENCODING_PCM_16BIT -> buffer.getShort(i) / 32768f
                    C.ENCODING_PCM_FLOAT -> buffer.getFloat(i)
                    C.ENCODING_PCM_32BIT -> buffer.getInt(i) / 2147483648f
                    else -> ((buffer.get(i).toInt() and 0xFF) or ((buffer.get(i + 1).toInt() and 0xFF) shl 8) or (buffer.get(i + 2).toInt() shl 16)) / 8388608f
                }
            }
            pending[filled++] = sum / ch
            if (filled == FftSize) {
                analyse(pendingStartUs + (FftSize / 2) * 1_000_000L / rate)
                // half a window on: the second half becomes the first
                System.arraycopy(pending, Hop, pending, 0, FftSize - Hop)
                filled = FftSize - Hop
                pendingStartUs += Hop * 1_000_000L / rate
            }
        }
    }

    /**
     * The bands at [positionMs] into the song, 0 (silent) to 1 (loud), spread over [out]'s length,
     * between the two frames either side of it. False, and [out] left at 0, when there's nothing
     * near that time yet (just asked, or just seeked).
     */
    fun read(positionMs: Long, out: FloatArray): Boolean {
        lastAskedNs = System.nanoTime()
        java.util.Arrays.fill(out, 0f)
        synchronized(lock) {
            var before = -1
            var after = -1
            for (i in 0 until History) {
                val t = times[i]
                if (t == Long.MIN_VALUE) continue
                if (t <= positionMs && (before < 0 || t > times[before])) before = i
                if (t >= positionMs && (after < 0 || t < times[after])) after = i
            }
            val a = if (before >= 0) before else after
            val b = if (after >= 0) after else before
            if (a < 0 || b < 0) return false
            if (positionMs - times[a] > StaleMs && times[b] - positionMs > StaleMs) return false
            val span = (times[b] - times[a]).toFloat()
            val t = if (span > 0f) ((positionMs - times[a]) / span).coerceIn(0f, 1f) else 0f
            val fa = frames[a]
            val fb = frames[b]
            // the bands spread over the bars asked for
            for (k in out.indices) {
                val x = if (out.size == 1) 0f else k * (Bands - 1f) / (out.size - 1f)
                val i0 = x.toInt().coerceAtMost(Bands - 1)
                val i1 = (i0 + 1).coerceAtMost(Bands - 1)
                val u = x - i0
                val va = fa[i0] + (fa[i1] - fa[i0]) * u
                val vb = fb[i0] + (fb[i1] - fb[i0]) * u
                out[k] = va + (vb - va) * t
            }
            return true
        }
    }

    private fun clear() {
        java.util.Arrays.fill(times, Long.MIN_VALUE)
        head = 0
        filled = 0
        pendingStartUs = Long.MIN_VALUE
    }

    /** bands spaced by pitch from [LowHz] up to [HighHz] (or what the rate carries), tilted so the highs count */
    private fun placeBands(rate: Int) {
        bandsFor = rate
        val binHz = rate.toFloat() / FftSize
        val high = minOf(HighHz, rate / 2f * 0.95f)
        val ratio = (high / LowHz).toDouble()
        for (b in 0 until Bands) {
            val lo = LowHz * ratio.pow(b.toDouble() / Bands).toFloat()
            val hi = LowHz * ratio.pow((b + 1.0) / Bands).toFloat()
            bandLo[b] = lo / binHz
            bandHi[b] = hi / binHz
            // music falls off toward the top; a few dB an octave keeps the right of the arc alive
            val centre = sqrt(lo * hi)
            bandTilt[b] = 3.5f * (ln(centre / 1000f) / ln(2f))
        }
    }

    private fun analyse(centreUs: Long) {
        for (i in 0 until FftSize) {
            val j = reversed[i]
            re[j] = pending[i] * hann[i]
            im[j] = 0f
        }
        fft()
        // the oldest frame, out of the reader's sight while it's written over
        val frame: FloatArray
        synchronized(lock) {
            times[head] = Long.MIN_VALUE
            frame = frames[head]
        }
        val scale = 4f / FftSize
        for (b in 0 until Bands) {
            val lo = bandLo[b]
            val hi = bandHi[b]
            var peak = 0f
            if (hi - lo < 1f) {
                // narrower than a bin: the level at its centre, between the bins either side
                val x = (lo + hi) / 2f
                val i0 = x.toInt().coerceIn(1, FftSize / 2 - 2)
                val u = x - i0
                peak = magnitude(i0) * (1f - u) + magnitude(i0 + 1) * u
            } else {
                val from = lo.toInt().coerceIn(1, FftSize / 2 - 1)
                val to = hi.toInt().coerceIn(from, FftSize / 2 - 1)
                for (i in from..to) peak = maxOf(peak, magnitude(i))
            }
            val db = 20f * log10((peak * scale).coerceAtLeast(1e-6f)) + bandTilt[b]
            frame[b] = ((db - FloorDb) / (CeilingDb - FloorDb)).coerceIn(0f, 1f)
        }
        synchronized(lock) {
            times[head] = centreUs / 1000L
            head = (head + 1) % History
        }
    }

    private fun magnitude(i: Int): Float = sqrt(re[i] * re[i] + im[i] * im[i])

    /** in place, on re/im already in bit-reversed order */
    private fun fft() {
        var size = 2
        while (size <= FftSize) {
            val half = size / 2
            val step = FftSize / size
            var start = 0
            while (start < FftSize) {
                var k = 0
                for (j in start until start + half) {
                    val c = cosTable[k]
                    val s = -sinTable[k]
                    val l = j + half
                    val tr = re[l] * c - im[l] * s
                    val ti = re[l] * s + im[l] * c
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }

    companion object {
        const val Bands = 40
        private const val FftSize = 2048
        private const val Hop = 1024
        /** about three seconds of frames: the sink runs that far ahead at most */
        private const val History = 160
        private const val ListenWindowNs = 1_500_000_000L
        /** further than this from any frame and there's nothing to show */
        private const val StaleMs = 250L
        private const val LowHz = 45f
        private const val HighHz = 15_000f
        private const val FloorDb = -62f
        private const val CeilingDb = -6f
    }
}
