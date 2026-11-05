package com.mochyy.mochymusic.lyrics

import com.music.innertube.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CaptionAlignerTest {

    private val englishLines = listOf(
        "I walked along the empty road tonight",
        "the city lights were fading out of sight",
        "and every step I took was calling you",
        "I never knew the morning could be blue",
        "hold me close until the sun goes down",
        "never let me fall onto the ground",
        "we were dancing in the summer rain",
        "nothing in the world could feel the same",
        "the radio was playing our old song",
        "I wonder where the years have all gone",
        "hold me close until the sun goes down",
        "never let me fall onto the ground",
        "we were dancing in the summer rain",
        "nothing in the world could feel the same",
    )

    private fun <T> sure(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    private fun lyrics(texts: List<String>, startMs: Long = 10_000L, gapMs: Long = 4_000L) =
        texts.mapIndexed { i, text -> LyricsEntry(startMs + i * gapMs, text) }

    // auto captions the way youtube sends them: a word at a time, a few words missed or misheard
    private fun autoCaptions(lines: List<LyricsEntry>, deltaAt: (Long) -> Long, random: Random): List<CaptionCue> {
        val cues = ArrayList<CaptionCue>()
        for (line in lines) {
            val words = line.text.split(" ")
            var t = line.time + deltaAt(line.time) + random.nextLong(-150, 150)
            for (word in words) {
                val heard = when (random.nextInt(10)) {
                    0 -> null
                    1 -> word.reversed()
                    else -> word
                }
                if (heard != null) cues += CaptionCue(t, t + 280, heard)
                t += 300
            }
        }
        return cues
    }

    @Test
    fun findsAnIntroShift() {
        val lines = lyrics(englishLines)
        val cues = autoCaptions(lines, { 12_000L }, Random(1))
        val alignment = sure(CaptionAligner.align(lines, cues))
        assertEquals(1, alignment.map.runs.size)
        assertTrue("shift ${alignment.map.runs[0].deltaMs}", abs(alignment.map.runs[0].deltaMs - 12_000L) <= 400)
        assertTrue(alignment.map.isShifted)
        assertTrue("${alignment.matchedLines} lines", alignment.matchedLines >= 10)
    }

    @Test
    fun noShiftWhenAlreadyInStep() {
        val lines = lyrics(englishLines)
        val alignment = sure(CaptionAligner.align(lines, autoCaptions(lines, { 0L }, Random(2))))
        assertTrue(!alignment.map.isShifted)
    }

    @Test
    fun splitsAroundABreakInTheVideo() {
        val lines = lyrics(englishLines)
        // the video adds a 20 s skit after the first chorus
        val deltaAt = { t: Long -> if (t < lines[8].time) 5_000L else 25_000L }
        val alignment = sure(CaptionAligner.align(lines, autoCaptions(lines, deltaAt, Random(3))))
        assertEquals(2, alignment.map.runs.size)
        assertTrue(abs(alignment.map.deltaAt(lines[2].time) - 5_000L) <= 400)
        assertTrue(abs(alignment.map.deltaAt(lines[12].time) - 25_000L) <= 400)
    }

    @Test
    fun followsAVideoThatRunsSlowerThanTheAlbum() {
        val lines = lyrics(englishLines + englishLines.take(10), gapMs = 5_000L)
        // the video plays 3% slower and starts 4 s later: by the last line it's over 3.5 s further behind than at the first
        val deltaAt = { t: Long -> 4_000L + (t * 0.03).toLong() }
        val alignment = sure(CaptionAligner.align(lines, autoCaptions(lines, deltaAt, Random(7))))
        lines.forEach { line ->
            val off = abs(alignment.map.deltaAt(line.time) - deltaAt(line.time))
            assertTrue("line at ${line.time} is $off ms off in ${alignment.map}", off <= 250)
        }
    }

    @Test
    fun keepsACutAJumpWhileFollowingDrift() {
        val lines = lyrics(englishLines + englishLines.take(10), gapMs = 5_000L)
        // a slow drift, then a 15 s skit inserted half way
        val cut = lines[12].time
        val deltaAt = { t: Long -> 2_000L + (t * 0.02).toLong() + if (t >= cut) 15_000L else 0L }
        val alignment = sure(CaptionAligner.align(lines, autoCaptions(lines, deltaAt, Random(8))))
        assertTrue("${alignment.map}", alignment.map.runs.size >= 2)
        // a line that couldn't be found right at the cut could be either side of it; every line in a stretch must be right
        lines.filter { line -> alignment.map.runs.any { line.time in it.fromSongMs..it.toSongMs } }.forEach { line ->
            val off = abs(alignment.map.deltaAt(line.time) - deltaAt(line.time))
            assertTrue("line at ${line.time} is $off ms off in ${alignment.map}", off <= 250)
        }
    }

    @Test
    fun readsJapaneseWithoutSpaces() {
        val japanese = listOf(
            "沈むように溶けてゆくように", "二人だけの空が広がる夜に", "さよならだけだった", "その一言で全てが分かった",
            "日が沈み出した空と君の姿", "フェンス越しに重なっていた", "初めて会った日から", "僕の心の全てを奪った",
            "どこか儚い空気を纏う君は", "寂しい目をしてたんだ",
        )
        val lines = lyrics(japanese)
        // captions written in hiragana where the lyrics use katakana, which should still match
        val cues = lines.flatMap { line ->
            val start = line.time - 3_000L
            line.text.replace("フェンス", "ふぇんす").chunked(4).mapIndexed { k, chunk -> CaptionCue(start + k * 400, start + k * 400 + 380, chunk) }
        }
        val alignment = sure(CaptionAligner.align(lines, cues))
        assertTrue(abs(alignment.map.runs[0].deltaMs + 3_000L) <= 400)
    }

    @Test
    fun givesUpOnSomethingElse() {
        val lines = lyrics(englishLines)
        val other = lyrics(List(14) { "completely different words about nothing $it" }, startMs = 0)
        assertNull(CaptionAligner.align(lines, autoCaptions(other, { 0L }, Random(4))))
    }

    @Test
    fun handsEachLineItsTranslation() {
        val lines = lyrics(englishLines)
        val alignment = sure(CaptionAligner.align(lines, autoCaptions(lines, { 8_000L }, Random(5))))
        // an uploaded translation track: one cue per sung line, on the video's timing
        val translation = lines.mapIndexed { i, line -> CaptionCue(line.time + 8_000L, line.time + 8_000L + 3_500L, "line $i") }
        val assigned = CaptionAligner.assignCues(lines, alignment.map, translation)
        assigned.forEachIndexed { i, text -> assertEquals("line $i", text) }
    }

    @Test
    fun retimesLinesAndTheirWords() {
        val raw = listOf(
            "[ar:Someone]",
            "[00:10.00]I walked along the empty road tonight",
            "<I:10.0:10.3|walked:10.3:10.6>",
            "[00:14.00]the city lights were fading out of sight",
            "[00:18.00]and every step I took was calling you",
            "[00:22.00]I never knew the morning could be blue",
        ).joinToString("\n")
        val lines = LyricsUtils.parseLyrics(raw)
        val alignment = sure(CaptionAligner.align(lyrics(englishLines), autoCaptions(lyrics(englishLines), { 12_000L }, Random(6))))
        val retimed = sure(VideoLyricsSync.retime(raw, lines, alignment.map))
        val moved = LyricsUtils.parseLyrics(retimed)
        assertEquals(lines.size, moved.size)
        moved.zip(lines).forEach { (after, before) ->
            assertEquals(before.text, after.text)
            assertTrue(abs(after.time - before.time - 12_000L) <= 400)
        }
        val words = moved.first().words!!
        assertTrue(abs(words.first().startTime - 22.0) <= 0.4)
        assertTrue(retimed.startsWith("[ar:Someone]"))
    }

    @Test
    fun retimesRichSync() {
        val raw = "[00:10.00]<00:10.00> I <00:10.50> walked\n[00:14.00]<00:14.00> the <00:14.40> city"
        val lines = LyricsUtils.parseLyrics(raw)
        val alignment = sure(CaptionAligner.align(lyrics(englishLines), autoCaptions(lyrics(englishLines), { 12_000L }, Random(7))))
        val moved = LyricsUtils.parseLyrics(sure(VideoLyricsSync.retime(raw, lines, alignment.map)))
        assertEquals(2, moved.size)
        assertTrue(abs(moved[0].time - 22_000L) <= 400)
        assertTrue(abs(moved[0].words!![1].startTime - 22.5) <= 0.4)
    }
}
