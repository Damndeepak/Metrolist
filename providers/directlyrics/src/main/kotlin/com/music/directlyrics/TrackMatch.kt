package com.music.directlyrics

import kotlin.math.abs

/** a search result cut down to what tells whether it is the song being played */
internal data class TrackCandidate<T>(
    val titles: List<String>,
    val artists: List<String>,
    val durationSec: Int,
    val ref: T,
)

/** how many seconds a result's length may differ from the song's and still be the same recording */
private const val DurationTolerance = 8

/** the result that is this song: about the same length, same title and same artist when one matches, the top hit is often a cover or a live take so search order can't be trusted */
internal fun <T> List<TrackCandidate<T>>.bestMatch(title: String, artist: String, durationSec: Int): T? {
    val wantedTitle = normalize(title)
    val wantedArtist = normalize(artist)
    if (wantedTitle.isEmpty()) return null
    return asSequence()
        .filter { durationSec <= 0 || abs(it.durationSec - durationSec) <= DurationTolerance }
        .map { it to titleScore(it, wantedTitle) }
        .filter { (_, score) -> score > 0 }
        .maxByOrNull { (candidate, score) -> score + artistScore(candidate, wantedArtist) }
        ?.first
        ?.ref
}

private fun titleScore(candidate: TrackCandidate<*>, wantedTitle: String): Int =
    candidate.titles.map(::normalize).filter { it.isNotEmpty() }.maxOfOrNull { title ->
        when {
            title == wantedTitle -> 2
            title.contains(wantedTitle) || wantedTitle.contains(title) -> 1
            else -> 0
        }
    } ?: 0

// the app passes every artist joined into one string, so any one of the result's artists counts
private fun artistScore(candidate: TrackCandidate<*>, wantedArtist: String): Int {
    if (wantedArtist.isEmpty()) return 0
    val matches = candidate.artists.map(::normalize).any { name ->
        name.isNotEmpty() && (wantedArtist.contains(name) || name.contains(wantedArtist))
    }
    return if (matches) 2 else 0
}

// "blinding lights (remastered)", "晴天（深情版）" and "song [live]" all come down to the bare name
private val Decorations = Regex("""\s*[(（\[【「『《].*?[)）\]】」』》]""")

private fun normalize(text: String): String =
    text.replace(Decorations, "").lowercase().filter { it.isLetterOrDigit() }
