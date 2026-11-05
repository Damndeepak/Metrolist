package com.music.youlyplus.models

import kotlinx.serialization.Serializable

@Serializable
data class LyricsResponse(

    val id: Int? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,

    val lyrics: List<LyricsItem>? = null,
    val type: String? = null,

    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
)

@Serializable
data class LyricsItem(
    val text: String? = null,
    val time: Long? = null,
    val duration: Long? = null,
    val syllabus: List<Syllable>? = null,
    /** which voice sings this line, e.g. {"singer":"v1"}, the api always returned it but the model never declared it so duet lyrics rendered flush left */
    val element: LyricsElement? = null,
)

@Serializable
data class LyricsElement(
    val singer: String? = null,
)

@Serializable
data class Syllable(
    val text: String? = null,
    val time: Long? = null,
    val duration: Long? = null,
    val isBackground: Boolean? = null,
)
