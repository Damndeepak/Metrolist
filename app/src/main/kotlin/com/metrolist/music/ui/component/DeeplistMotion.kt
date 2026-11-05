package com.metrolist.music.ui.component

import com.metrolist.music.lyrics.LyricsEntry
import java.text.BreakIterator

internal data class DeeplistMotionWord(val startChar: Int, val endChar: Int, val start: Long, val end: Long)

internal fun deeplistMotionWords(entry: LyricsEntry, nextTime: Long?): List<DeeplistMotionWord> {
    val visible = Regex("\\S+").findAll(entry.text).toList()
    if (visible.isEmpty()) return emptyList()
    var cursor = 0
    val native = entry.words.orEmpty().mapNotNull { word ->
        val index = entry.text.indexOf(word.text, cursor)
        if (index < 0 || word.text.isBlank()) null else {
            cursor = index + word.text.length
            DeeplistMotionWord(index, cursor, (word.startTime * 1000).toLong(), (word.endTime * 1000).toLong())
        }
    }
    val duration = ((nextTime ?: (entry.time + 4000L)) - entry.time).coerceIn(1L, 12000L)
    val total = visible.sumOf { it.value.length }.coerceAtLeast(1)
    var consumed = 0
    return visible.map { match ->
        val startChar = match.range.first
        val endChar = match.range.last + 1
        val matching = native.filter { it.startChar < endChar && it.endChar > startChar }
        // Line-only lyrics have no real word timestamps; distribute their line duration.
        val start = matching.minOfOrNull { it.start } ?: (entry.time + duration * consumed / total)
        consumed += match.value.length
        val end = matching.maxOfOrNull { it.end } ?: (entry.time + duration * consumed / total)
        DeeplistMotionWord(startChar, endChar, start, end.coerceAtLeast(start + 1L))
    }
}

internal fun deeplistArrival(elapsed: Long, index: Int, wordstorm: Boolean): Float {
    val stagger = if (wordstorm) 8L else 7L
    val duration = 430L
    val p = ((elapsed - index * stagger).toFloat() / duration).coerceIn(0f, 1f)
    return 1f - (1f - p) * (1f - p) * (1f - p)
}


internal fun deeplistLetterRanges(text: String): List<IntRange> {
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    val ranges = mutableListOf<IntRange>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        ranges.add(start until end)
        start = end
        end = iterator.next()
    }
    return ranges
}

internal fun deeplistCueArrival(now: Long, start: Long, end: Long, fraction: Float, speed: Float, animate: Boolean): Float {
    if (now < start) return 0f
    if (!animate || now >= end) return 1f
    val span = (end - start).coerceAtLeast(1L).toFloat()
    val stagger = fraction * minOf(80f, span * 0.2f)
    val duration = minOf(430f / speed.coerceIn(0.7f, 1.7f), span * 0.7f).coerceAtLeast(1f)
    val p = ((now - start - stagger) / duration).coerceIn(0f, 1f)
    return 1f - (1f - p) * (1f - p) * (1f - p)
}

internal fun deeplistSlowFrames(count: Int, delta: Long): Int =
    if (delta > 48L) (count + 1).coerceAtMost(8) else (count - 1).coerceAtLeast(0)

internal fun deeplistStyleValues(value: String): List<Float>? {
    val values = value.split('|').map { it.toFloatOrNull() ?: return null }
    return values.takeIf { it.size == 18 && it.all { number -> number.isFinite() } }
}
