package com.music.musixmatch

/** what musixmatch knows about one song; anything it didn't say stays null or empty */
data class MusixmatchSong(
    val title: String,
    val artist: String,
    val album: String? = null,
    /** "2000-07-10", the first release musixmatch has on record */
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    /** musixmatch's own few lines on what the song is about */
    val meaning: String? = null,
    val moods: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val artists: List<MusixmatchArtist> = emptyList(),
    val coverUrl: String? = null,
    val spotifyId: String? = null,
    val appleMusicId: String? = null,
    val isExplicit: Boolean = false,
    /** the song's page on musixmatch, for the "provided by" link */
    val pageUrl: String? = null,
    val lengthSeconds: Int? = null,
    /** the song page has been read, so the meaning, moods and themes are final (and null means it has none) */
    val pageRead: Boolean = false,
    /** other copies musixmatch keeps of this song, tried in order when this page says nothing about it */
    val alternatePages: List<String> = emptyList(),
)

data class MusixmatchArtist(
    val name: String,
    val imageUrl: String? = null,
)
