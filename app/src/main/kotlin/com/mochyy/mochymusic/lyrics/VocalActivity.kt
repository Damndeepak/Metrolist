// VocalActivity.kt

package com.mochyy.mochymusic.lyrics

import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Where a song is being sung, every [FrameMs] ms, read from its audio. What lyrics auto offset
 * lines the lyrics up against.
 */
internal class VocalActivity(
    /** 0..1 per frame: how much a voice is singing there. */
    val presence: FloatArray,
    /** 0..1 per frame: how sharply a word or a note starts there. */
    val onsets: FloatArray,
    /** Where frame 0 sits in the song, ms: frame i is at [originMs] + i × [FrameMs]. */
    val originMs: Float,
) {
    val frames: Int get() = presence.size

    companion object {
        const val FrameMs = 10
    }
}

/**
 * Builds [VocalActivity] from PCM as it's decoded, without keeping the audio.
 *
 * A lead vocal nearly always sits in the middle of the mix, carries a pitch, and lives between
 * about 250 Hz and 4 kHz. Most of what shares that band with it is spread to the sides (guitars,
 * pads, reverb) or is noise (cymbals, breath). So each frame keeps the part of the band that's the
 * same in both channels - the middle's power minus the sides' - and how tonal that part is. A
 * mono song has no sides, and then it's simply the band.
 */
internal class VocalActivityAnalyzer {
    // the last FrameSize samples of the middle (L+R) and the sides (L-R), written round and round
    private val midRing = FloatArray(FrameSize)
    private val sideRing = FloatArray(FrameSize)
    private var writeIndex = 0
    private var totalSamples = 0L

    private val real = FloatArray(FrameSize)
    private val imaginary = FloatArray(FrameSize)
    private val previousLog = FloatArray(Bins)
    private val center = FloatArray(Bins)

    private val energyDb = Growing()
    private val share = Growing()
    private val tonality = Growing()
    private val flux = Growing()

    private var resampler: StereoResampler? = null
    private var originUs: Long? = null

    /** [pcm] is interleaved 16-bit audio starting [presentationUs] into the song. */
    fun feed(pcm: ShortBuffer, channels: Int, inputRate: Int, presentationUs: Long) {
        if (originUs == null) originUs = presentationUs
        val current = resampler?.takeIf { it.inputRate == inputRate }
            ?: StereoResampler(inputRate).also { resampler = it }
        current.push(pcm, channels) { mid, side -> addSample(mid, side) }
    }

    /** What was heard, or null when too little audio came through to tell anything. */
    fun finish(): VocalActivity? {
        val frames = energyDb.size
        if (frames < MinFrames) return null
        val energy = normalised(energyDb.toArray())
        val shareN = normalised(share.toArray())
        val tonalN = normalised(tonality.toArray())
        val raw = FloatArray(frames) { 0.6f * energy[it] + 0.2f * shareN[it] + 0.2f * tonalN[it] }
        // a sung syllable lasts longer than a frame: smooth over 70 ms so drum hits don't flicker it
        val presence = movingAverage(raw, radius = 3)
        val onsets = onsetEnvelope(flux.toArray())
        val origin = (originUs ?: 0L) / 1_000f + FrameSize / 2f * 1_000f / SampleRate
        return VocalActivity(presence = presence, onsets = onsets, originMs = origin)
    }

    private fun addSample(mid: Float, side: Float) {
        midRing[writeIndex] = mid
        sideRing[writeIndex] = side
        writeIndex = (writeIndex + 1) and (FrameSize - 1)
        totalSamples++
        if (totalSamples >= FrameSize && (totalSamples - FrameSize) % HopSize == 0L) analyzeFrame()
    }

    private fun analyzeFrame() {
        // both channels in one FFT: the middle as the real part, the sides as the imaginary part
        for (i in 0 until FrameSize) {
            val at = (writeIndex + i) and (FrameSize - 1)
            real[i] = midRing[at] * Window[i]
            imaginary[i] = sideRing[at] * Window[i]
        }
        fft.transform(real, imaginary)

        var bandEnergy = 0f
        var allEnergy = 0f
        var rise = 0f
        var logSum = 0f
        var flatnessEnergy = 0f
        var flatnessBins = 0
        for (bin in 1 until Bins) {
            val mirror = FrameSize - bin
            val a = real[bin]; val b = imaginary[bin]
            val c = real[mirror]; val d = imaginary[mirror]
            // pull the two spectra apart again: X = (Z[k] + conj Z[N-k]) / 2, Y = (Z[k] - conj Z[N-k]) / 2i
            val midRe = (a + c) * 0.5f; val midIm = (b - d) * 0.5f
            val sideRe = (b + d) * 0.5f; val sideIm = (c - a) * 0.5f
            val midPower = midRe * midRe + midIm * midIm
            val sidePower = sideRe * sideRe + sideIm * sideIm
            allEnergy += midPower
            // a source panned with gains l and r gives l·r of its power here: full in the middle,
            // nothing when hard-panned, about nothing for wide, uncorrelated sound like reverb
            val centered = max(0f, midPower - sidePower)
            center[bin] = centered
            if (bin in BandFrom..BandTo) {
                bandEnergy += centered * BandWeight[bin]
                val compressed = ln(1f + 1_000f * sqrt(centered))
                val up = compressed - previousLog[bin]
                if (up > 0f) rise += up
                previousLog[bin] = compressed
            }
            if (bin in TonalFrom..TonalTo) {
                logSum += ln(centered + 1e-10f)
                flatnessEnergy += centered
                flatnessBins++
            }
        }
        energyDb.add((10.0 * log10(bandEnergy + 1e-10)).toFloat())
        share.add(if (allEnergy > 0f) bandEnergy / allEnergy else 0f)
        // spectral flatness: near 1 for noise (cymbals, breath), low for a sung note's harmonics
        val flatness = if (flatnessBins > 0 && flatnessEnergy > 0f) {
            (exp(logSum / flatnessBins) / (flatnessEnergy / flatnessBins)).coerceIn(0f, 1f)
        } else {
            1f
        }
        tonality.add(1f - flatness)
        flux.add(rise / (BandTo - BandFrom + 1))
    }

    /** The flux with its local average taken away, so only real jumps remain, scaled to 0..1. */
    private fun onsetEnvelope(raw: FloatArray): FloatArray {
        val half = 25
        val prefix = DoubleArray(raw.size + 1)
        for (i in raw.indices) prefix[i + 1] = prefix[i] + raw[i]
        val out = FloatArray(raw.size)
        for (i in raw.indices) {
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half + 1).coerceAtMost(raw.size)
            val mean = ((prefix[to] - prefix[from]) / (to - from)).toFloat()
            out[i] = max(0f, raw[i] - mean)
        }
        val top = percentile(out, 0.98f).takeIf { it > 1e-6f } ?: return out
        for (i in out.indices) out[i] = min(1f, out[i] / top)
        return out
    }

    /** Each value placed between this song's 15th and 97th percentile: 0 its low, 1 its high. */
    private fun normalised(values: FloatArray): FloatArray {
        val low = percentile(values, 0.15f)
        val high = percentile(values, 0.97f)
        val span = (high - low).takeIf { it > 1e-6f } ?: return FloatArray(values.size)
        return FloatArray(values.size) { ((values[it] - low) / span).coerceIn(0f, 1f) }
    }

    private fun percentile(values: FloatArray, fraction: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sortedArray()
        return sorted[(sorted.size * fraction).toInt().coerceIn(0, sorted.size - 1)]
    }

    private fun movingAverage(values: FloatArray, radius: Int): FloatArray {
        val prefix = DoubleArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        return FloatArray(values.size) { i ->
            val from = (i - radius).coerceAtLeast(0)
            val to = (i + radius + 1).coerceAtMost(values.size)
            ((prefix[to] - prefix[from]) / (to - from)).toFloat()
        }
    }

    private class Growing {
        private var values = FloatArray(8_192)
        var size = 0
            private set

        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun toArray(): FloatArray = values.copyOf(size)
    }

    companion object {
        const val SampleRate = 16_000
        // 32 ms windows every 10 ms: short enough to place a syllable, long enough to hear its pitch
        private const val FrameSize = 512
        private const val HopSize = 160
        private const val Bins = FrameSize / 2 + 1
        private const val HzPerBin = SampleRate.toFloat() / FrameSize

        /** Less than 15 s of audio says too little about a song to line its lyrics up. */
        private const val MinFrames = 1_500

        private val BandFrom = (250f / HzPerBin).toInt()
        private val BandTo = (4_000f / HzPerBin).toInt()
        private val TonalFrom = (300f / HzPerBin).toInt()
        private val TonalTo = (3_000f / HzPerBin).toInt()

        // where a voice carries most: its formants, 300 Hz to 3.4 kHz, count fully; the edges half
        private val BandWeight = FloatArray(Bins) { bin ->
            val hz = bin * HzPerBin
            if (hz in 300f..3_400f) 1f else 0.5f
        }

        private val Window = FloatArray(FrameSize) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FrameSize - 1))).toFloat() }
        private val fft = Fft(FrameSize)
    }
}

/**
 * Turns interleaved PCM at any rate into the middle and the sides at 16 kHz. Everything above
 * ~6.5 kHz is filtered out first: left in, cymbals at 12-15 kHz would fold down into the voice's
 * band and read as singing.
 */
internal class StereoResampler(
    val inputRate: Int,
    outputRate: Int = VocalActivityAnalyzer.SampleRate,
    cutoffHz: Double = 6_500.0,
) {
    internal val step = inputRate.toDouble() / outputRate
    internal val filtering = inputRate > outputRate
    // a 4th-order Butterworth low-pass: two biquads per channel
    internal val left = arrayOf(Biquad.lowPass(inputRate, cutoffHz, 0.5412), Biquad.lowPass(inputRate, cutoffHz, 1.3066))
    internal val right = arrayOf(Biquad.lowPass(inputRate, cutoffHz, 0.5412), Biquad.lowPass(inputRate, cutoffHz, 1.3066))
    internal var position = 0.0
    internal var previousLeft = 0f
    internal var previousRight = 0f

    inline fun push(pcm: ShortBuffer, channels: Int, emit: (mid: Float, side: Float) -> Unit) {
        val frames = pcm.remaining() / channels
        for (frame in 0 until frames) {
            var l = pcm.get() / 32_768f
            var r = if (channels >= 2) pcm.get() / 32_768f else l
            // a surround stream's other channels aren't part of the stereo picture
            for (c in 2 until channels) pcm.get()
            if (filtering) {
                l = left[1].process(left[0].process(l))
                r = right[1].process(right[0].process(r))
            }
            while (position <= 1.0) {
                val t = position.toFloat()
                val sampleL = previousLeft + (l - previousLeft) * t
                val sampleR = previousRight + (r - previousRight) * t
                emit((sampleL + sampleR) * 0.5f, (sampleL - sampleR) * 0.5f)
                position += step
            }
            position -= 1.0
            previousLeft = l
            previousRight = r
        }
    }

    internal class Biquad(
        private val b0: Float, private val b1: Float, private val b2: Float,
        private val a1: Float, private val a2: Float,
    ) {
        private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }

        companion object {
            fun lowPass(rate: Int, cutoffHz: Double, q: Double): Biquad {
                val k = tan(PI * cutoffHz / rate)
                val norm = 1.0 / (1.0 + k / q + k * k)
                val b0 = k * k * norm
                return Biquad(
                    b0 = b0.toFloat(),
                    b1 = (2.0 * b0).toFloat(),
                    b2 = b0.toFloat(),
                    a1 = (2.0 * (k * k - 1.0) * norm).toFloat(),
                    a2 = ((1.0 - k / q + k * k) * norm).toFloat(),
                )
            }
        }
    }

}
