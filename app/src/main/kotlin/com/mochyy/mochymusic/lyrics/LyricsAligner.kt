// LyricsAligner.kt

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.lyrics.VocalActivity.Companion.FrameMs
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lines timed lyrics up with where the song is really sung ([VocalActivity]), and works out the
 * offset that puts each line on its singing.
 *
 * 1. What the lyrics expect: a voice during every word (or every line, for lyrics timed by line)
 *    and a fresh sound where each word starts. How long people sing counts as much as when they
 *    start: a held note, a quick run of words or a long gap between verses all leave a shape.
 * 2. One shift for the whole song: the one where what's expected and what's heard agree most,
 *    tried every 40 ms across the offset range, then every 10 ms around the best.
 * 3. Line by line: each line may sit up to 400 ms either side of that, all chosen together so
 *    neighbouring lines can't jump about. The answer is the middle of what the lines that clearly
 *    heard their singing say, so a few badly timed lines can't drag it off. The lines also show
 *    whether the timing drifts across the song (lyrics timed to a slightly faster or slower
 *    version), which no single offset can fix.
 */
internal object LyricsAligner {

    class Alignment(
        /** Added to the playback position, the way the song's lyrics offset is. */
        val offsetMs: Int,
        /** 0..1: how sure the match is. */
        val confidence: Float,
        /** How far the timing drifts between the first line and the last, ms; null when it holds steady. */
        val driftMs: Int?,
        val strongLines: Int,
        val totalLines: Int,
    )

    /** Null when there's too little to line up (a handful of lines) or nothing agrees anywhere. */
    fun align(lines: List<LyricsEntry>, vocals: VocalActivity, maxOffsetMs: Int): Alignment? {
        val expected = expectedSinging(lines) ?: return null
        if (expected.spans.size < MinLines) return null

        val heardVoice = zscore(vocals.presence)
        val heardOnsets = zscore(vocals.onsets)
        val wantedVoice = zscore(expected.presence)
        val wantedOnsets = zscore(expected.onsets)
        val onsetWeight = if (expected.byWord) 0.6f else 0.35f

        // 2. the whole song, coarse then fine. Shift k pairs heard frame f with lyric frame f + k.
        val maxShift = maxOffsetMs / FrameMs
        val coarseVoice = downsample(heardVoice); val coarseOnsets = downsample(heardOnsets)
        val coarseWantedVoice = downsample(wantedVoice); val coarseWantedOnsets = downsample(wantedOnsets)
        val coarseMax = maxShift / Coarse
        val coarse = FloatArray(coarseMax * 2 + 1) { i ->
            val shift = i - coarseMax
            correlate(coarseVoice, coarseWantedVoice, shift) + onsetWeight * correlate(coarseOnsets, coarseWantedOnsets, shift)
        }
        val coarseBest = coarse.indices.maxBy { coarse[it] }
        val best = coarse[coarseBest]
        // at the edge of the range: the lyrics are further off than the offset can go, or another version's
        if (best <= 0f || abs(coarseBest - coarseMax) >= coarseMax - 1) return null
        // How far the best stands above the best rival outside its own hill. Lyrics timed by line
        // make a broad hill (each line is one long block), so its edges reach further out. A match
        // that barely beats a rival is chance, not the singing: better no answer than a wrong one.
        val hill = if (expected.byWord) WordHillCoarse else LineHillCoarse
        val runnerUp = coarse.indices
            .filter { abs(it - coarseBest) > hill }
            .maxOfOrNull { coarse[it] } ?: 0f
        val sharpness = ((best - runnerUp) / (abs(best) + 1e-3f)).coerceIn(0f, 1f)
        if (sharpness < MinSharpness) return null

        val center = (coarseBest - coarseMax) * Coarse
        val fine = (center - Coarse - 2..center + Coarse + 2).associateWith { shift ->
            correlate(heardVoice, wantedVoice, shift) + onsetWeight * correlate(heardOnsets, wantedOnsets, shift)
        }
        val shift = fine.maxBy { it.value }.key
        val globalOffset = shift * FrameMs + subFrame(fine, shift) - vocals.originMs
        val voiceAgreement = correlate(heardVoice, wantedVoice, shift)

        // 3. line by line
        // Timed by line, all a line really pins down is where it starts (its end is a guess), so
        // there the start counts for more, and a line stands out less clearly on its own.
        val lineOnsetWeight = if (expected.byWord) onsetWeight else 1f
        val strongLine = if (expected.byWord) StrongLine else StrongLineByLine
        val lineOffsets = refineLines(expected.spans, shift, heardVoice, heardOnsets, wantedVoice, wantedOnsets, lineOnsetWeight)
            .map { (span, frames, strength) -> LineOffset(span, (shift + frames) * FrameMs - vocals.originMs, strength) }
        val strong = lineOffsets.filter { it.strength >= strongLine }

        val offset = if (strong.size >= MinStrongLines) weightedMedian(strong) else globalOffset
        val agrees = abs(offset - globalOffset) <= 150f
        val drift = if (strong.size >= MinStrongLines) drift(strong) else null

        val confidence = (
            0.55f * (sharpness / 0.6f).coerceIn(0f, 1f) +
                0.3f * (voiceAgreement / 0.45f).coerceIn(0f, 1f) +
                0.15f * strong.size.toFloat() / lineOffsets.size
            ) * (if (agrees) 1f else 0.75f)

        return Alignment(
            offsetMs = ((offset / 10f).roundToInt() * 10).coerceIn(-maxOffsetMs, maxOffsetMs),
            confidence = confidence.coerceIn(0f, 1f),
            driftMs = drift,
            strongLines = strong.size,
            totalLines = lineOffsets.size,
        )
    }

    // ---- 1. what the lyrics expect ----

    private class LineSpan(val startMs: Long, val endMs: Long)

    private class Expected(
        val presence: FloatArray,
        val onsets: FloatArray,
        val spans: List<LineSpan>,
        val byWord: Boolean,
    )

    private fun expectedSinging(lines: List<LyricsEntry>): Expected? {
        val sung = lines.withIndex().filter { it.value.text.isNotBlank() }
        if (sung.isEmpty()) return null
        val byWord = sung.count { !it.value.words.isNullOrEmpty() } * 2 >= sung.size

        val spans = ArrayList<LineSpan>()
        val words = ArrayList<Triple<Long, Long, Float>>()
        val starts = ArrayList<Pair<Long, Float>>()
        for ((index, line) in sung) {
            val weight = if (line.isBackground) 0.5f else 1f
            val timed = line.words?.filter { it.text.isNotBlank() && it.endTime > it.startTime }
            if (!timed.isNullOrEmpty()) {
                var previousEnd = -1L
                for (word in timed) {
                    val start = (word.startTime * 1_000).toLong()
                    val end = (word.endTime * 1_000).toLong()
                    // a quick breath between words is still one stretch of voice to the ear
                    val from = if (previousEnd >= 0 && start - previousEnd in 1..LegatoGapMs) previousEnd else start
                    words += Triple(from, end, weight)
                    starts += start to weight
                    previousEnd = end
                }
                spans += LineSpan((timed.first().startTime * 1_000).toLong(), (timed.last().endTime * 1_000).toLong())
            } else {
                // timed by line: sung from its start for about as long as its syllables take, and
                // never into the next line
                val start = line.time
                val next = sung.firstOrNull { it.index > index }?.value?.time ?: (start + MaxLineMs)
                val guess = (syllables(line.text) * MsPerSyllable).coerceIn(MinLineMs, MaxLineMs)
                val end = min(next - 50, start + guess).coerceAtLeast(start + 300)
                words += Triple(start, end, weight)
                starts += start to weight * 1.5f
                spans += LineSpan(start, end)
            }
        }

        val endMs = (words.maxOfOrNull { it.second } ?: 0L) + 2_000L
        val size = (endMs / FrameMs).toInt() + 1
        val presence = FloatArray(size)
        for ((from, to, weight) in words) {
            val a = (from / FrameMs).toInt().coerceIn(0, size)
            val b = (to / FrameMs).toInt().coerceIn(0, size)
            // a line's end is only a guess when timed by line: let it trail off rather than stop dead
            val fade = if (byWord) 0 else FadeFrames
            for (g in a until b) {
                val tail = if (fade > 0) ((b - g).toFloat() / fade).coerceAtMost(1f) else 1f
                presence[g] = max(presence[g], weight * tail)
            }
        }
        val impulses = FloatArray(size)
        for ((at, weight) in starts) {
            val g = (at / FrameMs).toInt()
            if (g in 0 until size) impulses[g] = max(impulses[g], weight)
        }
        // a word start found by ear is a blur, not a click; a line start more so
        val onsets = gaussianBlur(impulses, sigma = if (byWord) 3f else 8f)
        return Expected(presence, onsets, spans, byWord)
    }

    /** Roughly how many syllables a line sings: vowel groups in Latin script, one per character in CJK. */
    private fun syllables(text: String): Int {
        var count = 0
        var inVowel = false
        for (ch in text) {
            when {
                Character.UnicodeScript.of(ch.code).let { it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA ||
                    it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL } -> {
                    count++
                    inVowel = false
                }
                ch.lowercaseChar() in Vowels -> {
                    if (!inVowel) count++
                    inVowel = true
                }
                else -> inVowel = false
            }
        }
        return count.coerceAtLeast(1)
    }

    // ---- 3. line by line ----

    private data class LineFit(val span: LineSpan, val frames: Int, val strength: Float)
    private class LineOffset(val span: LineSpan, val offsetMs: Float, val strength: Float)

    /**
     * Where each line fits best within ±[LineSlackFrames] of [shift], chosen together: moving a
     * line away from its neighbour costs a little, so one line can only go its own way when its
     * singing clearly says so. [LineFit.strength] is how far its chosen spot stands out from the
     * rest it could have had.
     */
    private fun refineLines(
        spans: List<LineSpan>,
        shift: Int,
        heardVoice: FloatArray,
        heardOnsets: FloatArray,
        wantedVoice: FloatArray,
        wantedOnsets: FloatArray,
        onsetWeight: Float,
    ): List<LineFit> {
        val states = LineSlackFrames * 2 + 1
        val scores = spans.map { span ->
            val from = (span.startMs / FrameMs).toInt() - LineMarginFrames
            val to = (span.endMs / FrameMs).toInt() + LineMarginFrames
            zscore(FloatArray(states) { s ->
                val lineShift = shift + s - LineSlackFrames
                correlateWindow(heardVoice, wantedVoice, lineShift, from, to) +
                    onsetWeight * correlateWindow(heardOnsets, wantedOnsets, lineShift, from, to)
            })
        }

        // the best path through every line's spots, a little cost for each frame two neighbours differ
        val total = Array(spans.size) { FloatArray(states) }
        val from = Array(spans.size) { IntArray(states) }
        scores[0].copyInto(total[0])
        for (i in 1 until spans.size) {
            for (s in 0 until states) {
                var bestValue = Float.NEGATIVE_INFINITY
                var bestFrom = s
                for (p in 0 until states) {
                    val value = total[i - 1][p] - JumpCost * abs(s - p)
                    if (value > bestValue) { bestValue = value; bestFrom = p }
                }
                total[i][s] = bestValue + scores[i][s]
                from[i][s] = bestFrom
            }
        }
        val chosen = IntArray(spans.size)
        chosen[spans.lastIndex] = total.last().indices.maxBy { total.last()[it] }
        for (i in spans.lastIndex downTo 1) chosen[i - 1] = from[i][chosen[i]]

        return spans.mapIndexed { i, span ->
            LineFit(span, frames = chosen[i] - LineSlackFrames, strength = scores[i][chosen[i]])
        }
    }

    /** The offset half the strong lines are above and half below, each counted by how sure it is. */
    private fun weightedMedian(lines: List<LineOffset>): Float {
        val sorted = lines.sortedBy { it.offsetMs }
        val half = sorted.sumOf { it.strength.toDouble() } / 2
        var running = 0.0
        for (line in sorted) {
            running += line.strength
            if (running >= half) return line.offsetMs
        }
        return sorted.last().offsetMs
    }

    /**
     * How much the strong lines' offsets change from the first line to the last, when they change
     * steadily enough to be drift rather than scatter. A straight line through them, weighted by
     * how sure each is.
     */
    private fun drift(lines: List<LineOffset>): Int? {
        val weights = lines.map { it.strength.toDouble() }
        val xs = lines.map { it.span.startMs / 1_000.0 }
        val ys = lines.map { it.offsetMs.toDouble() }
        val totalWeight = weights.sum().takeIf { it > 0 } ?: return null
        val meanX = xs.indices.sumOf { weights[it] * xs[it] } / totalWeight
        val meanY = ys.indices.sumOf { weights[it] * ys[it] } / totalWeight
        val spreadX = xs.indices.sumOf { weights[it] * (xs[it] - meanX) * (xs[it] - meanX) }
        if (spreadX <= 1e-6) return null
        val slope = xs.indices.sumOf { weights[it] * (xs[it] - meanX) * (ys[it] - meanY) } / spreadX
        val spreadY = ys.indices.sumOf { weights[it] * (ys[it] - meanY) * (ys[it] - meanY) }
        val residual = ys.indices.sumOf {
            val fitted = meanY + slope * (xs[it] - meanX)
            weights[it] * (ys[it] - fitted) * (ys[it] - fitted)
        }
        val explained = if (spreadY > 1e-6) 1.0 - residual / spreadY else 0.0
        val across = slope * ((xs.maxOrNull() ?: 0.0) - (xs.minOrNull() ?: 0.0))
        return if (abs(across) >= MinDriftMs && explained >= 0.5) across.roundToInt() else null
    }

    // ---- maths ----

    /** The mean of heard[f] × wanted[f + shift] wherever both exist. */
    private fun correlate(heard: FloatArray, wanted: FloatArray, shift: Int): Float {
        val start = max(0, -shift)
        val end = min(heard.size, wanted.size - shift)
        if (end <= start) return 0f
        var sum = 0.0
        for (f in start until end) sum += heard[f] * wanted[f + shift]
        return (sum / (end - start)).toFloat()
    }

    /** [correlate] over lyric frames [from] until [to] only: one line and its edges. */
    private fun correlateWindow(heard: FloatArray, wanted: FloatArray, shift: Int, from: Int, to: Int): Float {
        val start = maxOf(from, 0, shift)
        val end = minOf(to, wanted.size, heard.size + shift)
        if (end <= start) return 0f
        var sum = 0.0
        for (g in start until end) sum += heard[g - shift] * wanted[g]
        return (sum / (end - start)).toFloat()
    }

    /** Where between frames the true peak sits, ms: a parabola through the best score and its neighbours. */
    private fun subFrame(scores: Map<Int, Float>, peak: Int): Float {
        val left = scores[peak - 1] ?: return 0f
        val middle = scores[peak] ?: return 0f
        val right = scores[peak + 1] ?: return 0f
        val curve = left - 2f * middle + right
        if (curve >= 0f) return 0f
        return (0.5f * (left - right) / curve).coerceIn(-0.5f, 0.5f) * FrameMs
    }

    private fun zscore(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        val mean = values.average().toFloat()
        var variance = 0.0
        for (v in values) variance += (v - mean) * (v - mean)
        val deviation = sqrt(variance / values.size).toFloat().takeIf { it > 1e-6f } ?: return FloatArray(values.size)
        return FloatArray(values.size) { (values[it] - mean) / deviation }
    }

    private fun downsample(values: FloatArray): FloatArray = FloatArray(values.size / Coarse) { i ->
        var sum = 0f
        for (j in 0 until Coarse) sum += values[i * Coarse + j]
        sum / Coarse
    }

    private fun gaussianBlur(values: FloatArray, sigma: Float): FloatArray {
        val radius = (sigma * 3).toInt()
        val kernel = FloatArray(radius * 2 + 1) { exp(-((it - radius) * (it - radius)) / (2f * sigma * sigma)) }
        val out = FloatArray(values.size)
        for (i in values.indices) {
            if (values[i] == 0f) continue
            for (k in kernel.indices) {
                val at = i + k - radius
                if (at in out.indices) out[at] = max(out[at], values[i] * kernel[k])
            }
        }
        return out
    }

    /** Fewer lines than this give too little shape to line up with any certainty. */
    private const val MinLines = 4
    private const val MinStrongLines = 3

    /** The coarse search steps 4 frames (40 ms) at a time. */
    private const val Coarse = 4
    /** How wide the best match's hill is, either side, in coarse steps: 400 ms timed by word, 1.6 s by line. */
    private const val WordHillCoarse = 10
    private const val LineHillCoarse = 40
    /** Below this the best match hardly beats its rivals, and is chance rather than the singing. */
    private const val MinSharpness = 0.25f

    /** How far one line may sit from the song-wide shift: 400 ms either way. */
    private const val LineSlackFrames = 40
    /** How much of the music either side of a line counts toward placing it: 300 ms. */
    private const val LineMarginFrames = 30
    /** The cost of a line sitting a frame away from its neighbour, in the scores' own units. */
    private const val JumpCost = 0.04f
    /** A line whose best spot stands this many deviations above its others clearly heard its singing. */
    private const val StrongLine = 2.0f
    private const val StrongLineByLine = 1.6f

    /** Less drift than this from first line to last is too small to hear. */
    private const val MinDriftMs = 250

    private const val LegatoGapMs = 120L
    private const val MsPerSyllable = 260L
    private const val MinLineMs = 700L
    private const val MaxLineMs = 7_000L
    /** How many frames a line timed by line trails off over at its guessed end: 200 ms. */
    private const val FadeFrames = 20

    private val Vowels = "aeiouyàáâãäåæèéêëìíîïòóôõöøùúûüýÿąęėīįōőūűų"
}
