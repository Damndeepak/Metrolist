package com.music.directlyrics

import java.util.Locale

/** one timed word or piece of a word, in milliseconds from the start of the song */
internal data class TimedWord(val text: String, val startMs: Long, val endMs: Long)

/** one lyrics line, words is empty when the source only times whole lines */
internal data class TimedLine(val startMs: Long, val text: String, val words: List<TimedWord> = emptyList())

private val LrcLineRegex = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?](.*)$""")

/** standard "[mm:ss.xx]text" lines, tag lines like [ti:] and [ar:] and anything untimed are dropped */
internal fun parseLrc(lrc: String): List<TimedLine> = lrc.lineSequence().mapNotNull { raw ->
    val match = LrcLineRegex.matchEntire(raw.trim()) ?: return@mapNotNull null
    val (minutes, seconds, fraction, text) = match.destructured
    val millis = fraction.padEnd(3, '0').take(3).toLong()
    TimedLine(minutes.toLong() * 60_000 + seconds.toLong() * 1000 + millis, text.trim())
}.toList()

// credit lines like "作词 : 方文山", "lyrics by：max martin" or "制作人 : 周杰伦": a short label, then a colon
private val CreditRegex = Regex("""^[^:：]{1,24}[:：].+$""")
private const val HeadCreditWindow = 15
private const val TailCreditWindow = 6

private fun TimedLine.isCredit(): Boolean {
    if (!CreditRegex.matches(text)) return false
    val label = text.substringBefore(':').substringBefore('：').trim()
    return label.split(' ').size <= 4
}

/** drops the credits chinese services put around the lyrics: near the top everything up to the last credit goes (taking the "title - artist" line before them), near the end the first credit and all after it */
internal fun List<TimedLine>.withoutCredits(): List<TimedLine> {
    val body = drop(take(HeadCreditWindow).indexOfLast { it.isCredit() } + 1)
    val tailStart = (body.size - TailCreditWindow).coerceAtLeast(0)
    val tailCut = body.subList(tailStart, body.size).indexOfFirst { it.isCredit() }
    return if (tailCut == -1) body else body.subList(0, tailStart + tailCut)
}

// "纯音乐，请欣赏" ("instrumental, enjoy") is the one line both chinese services send for a song with no words
private const val InstrumentalMarker = "纯音乐"

/** whether there is anything to sing along to, rather than nothing or an "instrumental" note */
internal fun List<TimedLine>.hasLyrics(): Boolean {
    val sung = filter { it.text.isNotBlank() }
    return sung.isNotEmpty() && sung.none { InstrumentalMarker in it.text }
}

// the word line is split on "|" and each word on ":" and ends at ">" so a word can't carry them
private const val WordSeparators = ":|<>"

/** lines in the app's lyrics format, a word-timed line is followed by its words as "<word:start:end|...>" in seconds like the paxsenix provider writes them, so the highlight lasts as long as the word is sung */
internal fun List<TimedLine>.toLrc(): String = buildString {
    this@toLrc.forEach { line ->
        append(stamp(line.startMs)).append(line.text).append('\n')
        val words = line.words
            .map { it.copy(text = it.text.trim().filterNot { char -> char in WordSeparators }) }
            .filter { it.text.isNotEmpty() }
        if (words.isNotEmpty()) {
            words.joinTo(this, separator = "|", prefix = "<", postfix = ">\n") { word ->
                "${word.text}:${word.startMs / 1000.0}:${word.endMs / 1000.0}"
            }
        }
    }
}.trimEnd()

private fun stamp(ms: Long): String =
    String.format(Locale.US, "[%02d:%02d.%03d]", ms / 60_000, ms / 1000 % 60, ms % 1000)
