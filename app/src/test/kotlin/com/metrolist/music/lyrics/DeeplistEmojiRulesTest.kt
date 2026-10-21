package com.metrolist.music.lyrics

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeeplistEmojiRulesTest {
    private fun words(vararg words: String) = words.mapIndexed { i, text -> VerciWord(text, i * 300L, (i + 1) * 300L, "") }
    @Test fun `phrases win over individual words and negation avoids happy icon`() {
        val result = deeplistEmojiTimeline(words("cold", "heart", "not", "happy"), "song", emptyList(), true, false, true, 2)
        assertEquals(listOf("🧊🖤"), result[0]); assertTrue(result[1].isEmpty())
        assertEquals(listOf("😔"), result[2]); assertTrue(result[3].isEmpty())
    }
    @Test fun `song corrections override global and blank corrections disable matching`() {
        val rules = listOf(EmojiCorrection("heart", "❤️"), EmojiCorrection("heart", "🖤", "song"), EmojiCorrection("rain", "", "song"))
        val result = deeplistEmojiTimeline(words("heart", "rain"), "song", rules, true, true, true, 2)
        assertEquals(listOf("🖤"), result[0]); assertTrue(result[1].isEmpty())
        assertEquals(listOf("❤️"), deeplistEmojiTimeline(words("heart"), "other", rules, true, true, false, 2)[0])
    }
    @Test fun `normalization preserves Hindi marks and never matches substrings`() {
        assertEquals("प्यार", emojiToken("प्यार!"))
        assertEquals("", verciEmoji("nice", true))
        for (word in listOf("pyar", "pyaar", "प्यार", "ख्वाबों", "dhadke", "aankhon", "mohobbat")) assertTrue("missing $word", verciEmoji(word, true, false).isNotEmpty())
        assertTrue(deeplistVocabulary.size > 1000)
        assertEquals("", verciEmoji("unmatchedword", true))
    }
    @Test fun `phrases do not cross instrumental gaps and all emoji switches are respected`() {
        val words = listOf(VerciWord("cold", 0, 300, ""), VerciWord("heart", 5000, 5300, ""))
        val result = deeplistEmojiTimeline(words, "song", emptyList(), true, true, true, 2)
        assertNotEquals(listOf("🧊🖤"), result[0])
        assertTrue(deeplistEmojiTimeline(words, "song", emptyList(), false, false, true, 2).all { it.isEmpty() })
    }
    @Test fun `corrections round trip and malformed rules are rejected`() {
        val value = saveEmojiCorrection("[]", EmojiCorrection(" Cold   Heart! ", "💙", "song"))
        assertEquals(listOf(EmojiCorrection("cold heart", "💙", "song")), decodeEmojiCorrections(value))
        assertTrue(runCatching { saveEmojiCorrection("[]", EmojiCorrection("!!!", "💙")) }.isFailure)
    }
    @Test fun `repeated words vary without changing custom corrections`() {
        val result = deeplistEmojiTimeline(words("love", "love", "love"), "song", emptyList(), true, true, true, 2)
        assertNotEquals(result[0], result[1])
        val custom = deeplistEmojiTimeline(words("love", "love"), "song", listOf(EmojiCorrection("love", "🖤")), true, true, true, 2)
        assertEquals(custom[0], custom[1])
    }
    @Test fun `chorus emphasis uses repeated lines or explicit marks`() {
        val lines = listOf(LyricsEntry(0, "Hello world"), LyricsEntry(1000, "One verse"), LyricsEntry(2000, "Hello world"), LyricsEntry(3000, "Solo line"))
        assertEquals(setOf(0L, 2000L), deeplistChorusLines(lines, emptySet()))
        assertEquals(setOf(0L, 2000L, 3000L), deeplistChorusLines(lines, setOf("solo line")))
    }
    @Test fun `mood rules are conservative and bilingual`() {
        assertEquals(-1, deeplistLyricMood("I feel lonely"))
        assertEquals(1, deeplistLyricMood("नाच झूम"))
        assertEquals(0, deeplistLyricMood("ordinary unmatched words"))
    }

}
