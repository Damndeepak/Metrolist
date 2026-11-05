package com.mochyy.mochymusic.canvas

import java.text.Normalizer
import kotlin.math.abs

/** whether a search result is the song being played, strict on purpose: a canvas from another song by the same artist is a bug while no canvas is fine */
object CanvasMatch {
    /** further than this from the song's length, a result is another recording */
    private const val DURATION_TOLERANCE_SEC = 4.0

    // a different take of the song: only a match when the song asked for is that take too
    private val OtherTake = Regex(
        "\\b(live|remix|instrumental|acoustic|karaoke|sped up|slowed|nightcore|8d|demo|inst)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val Bracketed = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val DashTail = Regex("\\s+[-–—]\\s+.*$")
    private val Marks = Regex("\\p{M}+")
    private val NonWord = Regex("[^\\p{L}\\p{N}]+")

    /** same title once extras like (feat. x), (2011 remaster) or - radio edit are stripped, and not a live, remix or other take the wanted title doesn't name */
    fun sameSong(candidate: String, wanted: String): Boolean {
        val key = titleKey(candidate)
        if (key.isEmpty() || key != titleKey(wanted)) return false
        return !OtherTake.containsMatchIn(candidate) || OtherTake.containsMatchIn(wanted)
    }

    fun sameArtist(candidate: String, wanted: String): Boolean {
        val a = fold(candidate)
        val b = fold(wanted)
        return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
    }

    /** true when either length is unknown, there's nothing to tell them apart by */
    fun closeDuration(candidateSec: Double?, wantedSec: Int?): Boolean {
        if (candidateSec == null || candidateSec <= 0 || wantedSec == null || wantedSec <= 0) return true
        return abs(candidateSec - wantedSec) <= DURATION_TOLERANCE_SEC
    }

    private fun titleKey(title: String): String =
        fold(title.replace(Bracketed, "").replace(DashTail, ""))

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).replace(Marks, "").replace(NonWord, "")
}
