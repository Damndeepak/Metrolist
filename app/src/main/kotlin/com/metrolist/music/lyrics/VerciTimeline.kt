package com.metrolist.music.lyrics

import java.util.Locale
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
    val lead = min(300L, (next.start - (words.getOrNull(index)?.start ?: 0L)).coerceAtLeast(2) / 2)
    val progress = ((time - next.start + lead).toFloat() / lead).coerceIn(0f, 1f)
    return index + progress * progress * (3 - 2 * progress)
}

private val verciEmojis = buildMap {
    listOf(
        "❤️" to "love loved lover loves dil pyaar pyar ishq mohabbat",
        "💔" to "broken heartbreak hurt pain dard",
        "💋" to "kiss kisses kissing lips",
        "🌙" to "moon moonlight chand raat night midnight tonight",
        "✨" to "stars shine glow sparkle taare magic",
        "🔥" to "fire flame burn burning aag lit",
        "🌧️" to "rain raining baarish tears cry crying",
        "🌊" to "ocean sea waves paani water",
        "🦋" to "butterfly butterflies fly flying freedom",
        "💭" to "dream dreams dreaming think thought khwaab yaad",
        "🥀" to "lonely alone tanha miss missing",
        "☀️" to "sun sunshine morning day subah",
        "🌹" to "rose roses flower flowers gulab",
        "👀" to "eyes eye look looking see aankhen",
        "💫" to "crazy dizzy pagal spinning",
        "💃" to "dance dancing party groove",
        "🎶" to "music song singing melody gaana",
        "🫶" to "care together saath always forever",
        "⚡" to "thunder lightning electric power",
        "🚀" to "sky space rocket high aasman",
        "🧊" to "ice cold frozen freeze",
        "👑" to "king queen crown royal",
        "💀" to "dead death dying die",
        "😈" to "devil demon demons wicked",
        "😇" to "angel heaven paradise",
        "💸" to "money cash rich dollar dollars paisa",
        "🚬" to "smoke smoking cigarette",
        "🍷" to "wine drunk drinking",
        "😊" to "smile smiling happy laugh",
        "🤫" to "secret secrets quiet silence whisper",
        "🏠" to "home ghar house",
        "🌍" to "world duniya earth",
        "⏳" to "time waqt waiting wait",
        "🛣️" to "road roads journey safar",
    ).forEach { (emoji, words) -> words.split(' ').forEach { put(it, emoji) } }
}

internal fun verciEmoji(text: String): String =
    verciEmojis[text.lowercase(Locale.ROOT).filter { it.isLetter() }] ?: ""
