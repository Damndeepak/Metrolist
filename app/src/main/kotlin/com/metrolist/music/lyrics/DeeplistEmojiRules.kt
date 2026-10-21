package com.metrolist.music.lyrics

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal fun emojiToken(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
    .lowercase(Locale.ROOT).filter { it.isLetter() || it == '\u200d' ||
        Character.getType(it) == Character.NON_SPACING_MARK.toInt() ||
        Character.getType(it) == Character.COMBINING_SPACING_MARK.toInt() }

internal fun emojiPhrase(text: String): String = text.trim().split(Regex("\\s+"))
    .map(::emojiToken).filter(String::isNotEmpty).joinToString(" ")

internal data class EmojiCorrection(val phrase: String, val emoji: String, val song: String = "")

internal fun decodeEmojiCorrections(value: String): List<EmojiCorrection> {
    require(value.length <= 100_000)
    val array = JSONArray(value)
    require(array.length() <= 500)
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val phrase = emojiPhrase(item.getString("phrase"))
        val icon = item.getString("emoji")
        val song = item.optString("song")
        require(phrase.isNotBlank() && phrase.length <= 100 && phrase.split(' ').size <= 8)
        require(icon.length <= 32 && icon.none { it.isISOControl() } && song.length <= 100)
        EmojiCorrection(phrase, icon, song)
    }.distinctBy { it.song to it.phrase }
}

internal fun encodeEmojiCorrections(items: List<EmojiCorrection>): String = JSONArray().apply {
    items.take(500).forEach { put(JSONObject().put("phrase", it.phrase).put("emoji", it.emoji).put("song", it.song)) }
}.toString()

internal fun saveEmojiCorrection(value: String, correction: EmojiCorrection): String {
    val clean = correction.copy(phrase = emojiPhrase(correction.phrase), emoji = correction.emoji.trim())
    val updated = listOf(clean) + decodeEmojiCorrections(value).filter { it.song != clean.song || it.phrase != clean.phrase }
    val encoded = encodeEmojiCorrections(updated)
    decodeEmojiCorrections(encoded)
    return encoded
}

private val phraseEmojis = mapOf(
    "cold heart" to "🧊🖤", "broken heart" to "💔", "broken dreams" to "💔💭",
    "fall in love" to "🥰", "falling in love" to "🥰", "not happy" to "😔", "not afraid" to "💪",
    "no tears" to "🫶", "miss you" to "🥀", "without you" to "🥀", "feel blue" to "😔",
    "feeling blue" to "😔", "blue eyes" to "💙👀", "my baby" to "🫶", "baby girl" to "🫶",
    "meri jaan" to "🥰", "mere dil" to "❤️", "toota dil" to "💔", "dil toota" to "💔",
    "tere bina" to "🥀", "tum bin" to "🥀", "yaad aati" to "💭", "raat ki baarish" to "🌙🌧️",
    "मेरी जान" to "🥰", "टूटा दिल" to "💔", "दिल टूटा" to "💔", "तेरे बिना" to "🥀",
    "याद आती" to "💭", "रात की बारिश" to "🌙🌧️", "प्यार नहीं" to "💔", "खुश नहीं" to "😔",
)

// Resolve once per timeline/settings change, never during a rendered frame.
internal fun deeplistEmojiTimeline(
    words: List<VerciWord>, song: String, corrections: List<EmojiCorrection>,
    advanced: Boolean, basic: Boolean, mega: Boolean, density: Int,
): List<List<String>> {
    if (!advanced && !basic) return List(words.size) { emptyList() }
    val result = MutableList<List<String>>(words.size) { emptyList() }
    val tokens = words.map { emojiToken(it.text) }
    val custom = corrections.filter { it.song.isEmpty() || it.song == song }
        .sortedBy { if (it.song == song) 1 else 0 }.associate { it.phrase to it.emoji }
    var i = 0
    while (i < words.size) {
        var matched = false
        for (count in minOf(8, words.size - i) downTo 1) {
            if ((i until i + count - 1).any { words[it + 1].start - words[it].end > 1200 }) continue
            val phrase = tokens.subList(i, i + count).joinToString(" ")
            val icon = if (custom.containsKey(phrase)) custom[phrase]
                else if (advanced) phraseEmojis[phrase] else null
            if (icon != null) {
                result[i] = if (icon.isEmpty()) emptyList() else listOf(icon)
                i += count; matched = true; break
            }
        }
        if (matched) continue
        if (density != 0 || i % 3 == 0) {
            val bundle = verciEmojiBundle(words[i].text, advanced, basic, mega)
            result[i] = bundle.take(if (density == 2) 2 else 1)
        }
        i++
    }
    return result
}

internal fun emojiMotion(icon: String): Int = when {
    icon.contains("🌧") || icon.contains("💧") || icon.contains("😢") -> 1
    icon.contains("🪽") || icon.contains("🦋") || icon.contains("🕊") -> 2
    icon.contains("💔") -> 3
    else -> 0
}
