package com.music.directlyrics.models

import kotlinx.serialization.Serializable

@Serializable
internal data class NetEaseSearchResponse(val result: SearchResult? = null) {
    @Serializable
    data class SearchResult(val songs: List<Song> = emptyList())

    @Serializable
    data class Song(
        val id: Long,
        val name: String = "",
        /** milliseconds */
        val duration: Long = 0,
        val artists: List<Artist> = emptyList(),
        /** other names the song goes by, often its english or original title */
        val alias: List<String> = emptyList(),
    )

    @Serializable
    data class Artist(val name: String = "")
}

@Serializable
internal data class NetEaseLyricResponse(
    /** line-timed lrc */
    val lrc: Lyric? = null,
    /** word-timed lyrics in netease's own format, when the song has them */
    val yrc: Lyric? = null,
) {
    @Serializable
    data class Lyric(val lyric: String? = null)
}
