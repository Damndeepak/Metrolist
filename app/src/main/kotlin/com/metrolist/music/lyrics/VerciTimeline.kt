package com.metrolist.music.lyrics

import kotlin.math.min

internal data class VerciWord(val text: String, val start: Long, val end: Long, val emoji: String, val estimated: Boolean = false)

internal fun buildVerciWords(lines: List<LyricsEntry>): List<VerciWord> {
    val main = lines.filter { !it.isBackground }.sortedBy { it.time }
    return buildList {
        main.forEachIndexed { index, line ->
            if (line.text.isBlank() || line.text.all { it.isWhitespace() || it in "♪♫.-–—" }) return@forEachIndexed
            val next = main.getOrNull(index + 1)?.time ?: (line.time + 6000L)
            val timed = line.words.orEmpty()
            if (timed.isNotEmpty()) {
                // Providers may timestamp syllables; join them before animating whole words.
                val text = StringBuilder()
                var start = 0L
                timed.forEachIndexed { i, word ->
                    if (text.isEmpty()) start = (word.startTime * 1000).toLong()
                    text.append(word.text)
                    if (word.hasTrailingSpace || i == timed.lastIndex) {
                        val value = text.toString().trim()
                        if (value.isNotEmpty()) add(VerciWord(value, start, (word.endTime * 1000).toLong().coerceAtLeast(start + 1), verciEmoji(value)))
                        text.clear()
                    }
                }
            } else {
                // shortcut: line-only lyrics use estimated word timing; exact sync needs word timestamps.
                val tokens = line.text.trim().split(Regex("\\s+"))
                val weight = tokens.sumOf { it.length + 1 }
                val span = min((next - line.time).coerceAtLeast(1), 400L + weight * 105L)
                var used = 0
                tokens.forEach { text ->
                    val start = line.time + span * used / weight
                    used += text.length + 1
                    add(VerciWord(text, start, (line.time + span * used / weight).coerceAtLeast(start + 1), verciEmoji(text), estimated = true))
                }
            }
        }
    }.sortedBy { it.start }
}

internal fun verciWordIndex(words: List<VerciWord>, time: Long): Int {
    var low = 0
    var high = words.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (words[mid].start <= time) low = mid + 1 else high = mid
    }
    return low - 1
}

internal fun verciScrollPosition(words: List<VerciWord>, time: Long): Float {
    val index = verciWordIndex(words, time)
    val next = words.getOrNull(index + 1) ?: return index.toFloat()
    val current = words.getOrNull(index) ?: return -1f
    // Hold during instrumental breaks instead of slowly crossing the whole gap.
    if (next.start - current.end > 1200L) {
        val start = (next.start - 300L).coerceAtLeast(current.end)
        val t = ((time - start).toFloat() / (next.start - start).coerceAtLeast(1)).coerceIn(0f, 1f)
        return index + t * t * (3 - 2 * t)
    }
    val span = (next.start - current.start).coerceAtLeast(1).toFloat()
    fun tangent(i: Int): Float {
        val a = words.getOrNull(i - 1) ?: return 1f / span
        val b = words.getOrNull(i + 1) ?: return 1f / span
        val h0 = (words[i].start - a.start).coerceAtLeast(1).toFloat()
        val h1 = (b.start - words[i].start).coerceAtLeast(1).toFloat()
        if (words[i].start - a.end > 1200 || b.start - words[i].end > 1200) return 0f
        return 3f * (h0 + h1) / ((2 * h1 + h0) * h0 + (h1 + 2 * h0) * h1)
    }
    val t = ((time - current.start) / span).coerceIn(0f, 1f)
    val t2 = t * t
    val t3 = t2 * t
    return index + (-2 * t3 + 3 * t2) + (t3 - 2 * t2 + t) * span * tangent(index) + (t3 - t2) * span * tangent(index + 1)
}

internal data class VerciGap(val start: Long, val end: Long, val echo: String)

internal fun buildVerciGaps(lines: List<LyricsEntry>): List<VerciGap> {
    var previous = ""
    val main = lines.filter { !it.isBackground }.sortedBy { it.time }
    return buildList {
        main.forEachIndexed { i, line ->
            if (line.text.isBlank() || line.text.all { it.isWhitespace() || it in "♪♫.-–—" }) {
                if (previous.isNotEmpty()) add(VerciGap(line.time, main.drop(i + 1).firstOrNull { it.text.isNotBlank() }?.time ?: Long.MAX_VALUE, previous))
            } else previous = line.text
        }
    }
}

internal class VerciHeartbeat {
    private var average = 0f
    private var previous = 0f
    private var last = -10000L
    private val beats = LongArray(3) { -10000L }
    fun reset() { average = 0f; previous = 0f; last = -10000; beats.fill(-10000) }
    fun sample(energy: Float, now: Long, dt: Float): Float {
        average += (energy - average) * (1 - kotlin.math.exp(-dt / .6f))
        if (energy > .08f && energy > average * 1.28f && energy - previous > .025f && now - last > 300) {
            beats[2] = beats[1]; beats[1] = beats[0]; beats[0] = now; last = now
        }
        previous = energy
        fun impulse(t: Float, width: Float): Float {
            if (t < 0 || t > width * 9) return 0f
            val x = t / width
            return x * x * kotlin.math.exp(2 - 2 * x)
        }
        return beats.sumOf { beat ->
            val t = (now - beat) / 1000f
            (impulse(t, .055f) + .43f * impulse(t - .13f, .043f) - .10f * impulse(t - .27f, .065f)).toDouble()
        }.toFloat().coerceIn(-.14f, 1.55f)
    }
}
