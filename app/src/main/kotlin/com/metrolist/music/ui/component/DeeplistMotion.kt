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
    val duration = ((nextTime ?: (entry.time + 4000L)) - entry.time).coerceIn(600L, 12000L)
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
