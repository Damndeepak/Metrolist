package com.metrolist.music.lyrics

internal fun deeplistLyricMood(text: String): Int {
    val tokens = emojiPhrase(text).split(' ').toSet()
    val sad = setOf("sad", "cry", "crying", "tears", "lonely", "alone", "heartbreak", "broken", "tanha", "dard", "aansu", "udaas", "तन्हा", "दर्द", "आँसू", "उदास")
    val energetic = setOf("party", "dance", "dancing", "fire", "boom", "naach", "nacho", "jhoom", "नाच", "झूम", "आग")
    return when { tokens.any { it in sad } -> -1; tokens.any { it in energetic } -> 1; else -> 0 }
}

internal fun deeplistChorusLines(lines: List<LyricsEntry>, marked: Set<String>): Set<Long> {
    val counts = lines.filter { it.text.isNotBlank() }.groupingBy { emojiPhrase(it.text) }.eachCount()
    return lines.filter { line ->
        val text = emojiPhrase(line.text)
        text.isNotBlank() && ((counts[text] ?: 0) > 1 || text in marked)
    }.map { it.time }.toSet()
}

internal fun deeplistChorusMarks(raw: String, song: String): Set<String> = runCatching {
    val array = org.json.JSONObject(raw).optJSONArray(song) ?: return@runCatching emptySet()
    (0 until array.length().coerceAtMost(50)).map { emojiPhrase(array.getString(it)) }.toSet()
}.getOrDefault(emptySet())
