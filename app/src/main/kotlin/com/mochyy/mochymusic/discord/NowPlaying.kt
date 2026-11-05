// NowPlaying.kt

package com.mochyy.mochymusic.discord

/** What the player is on, as the Discord status needs it. MusicService fills it in. */
data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val artistArtworkUrl: String?,
    val durationMs: Long,
    /** where the song was at [readAtMs] */
    val positionMs: Long,
    val readAtMs: Long,
    val speed: Float,
    val isPlaying: Boolean,
    /** "OPUS · 160 kbps", or null until the decoder says */
    val quality: String?,
    /** a YouTube video id, so links can open this exact song */
    val isYouTube: Boolean,
) {
    fun positionAt(nowMs: Long): Long {
        if (!isPlaying) return positionMs
        val moved = ((nowMs - readAtMs) * speed).toLong()
        return (positionMs + moved).coerceIn(0, durationMs.coerceAtLeast(0))
    }

    fun progressAt(nowMs: Long): Float =
        if (durationMs <= 0) 0f else positionAt(nowMs).toFloat() / durationMs

    companion object {
        /** for the preview when nothing is playing: a song that started 96 seconds ago */
        fun sample(nowMs: Long) = NowPlaying(
            mediaId = "",
            title = "Fake Plastic Trees",
            artist = "Radiohead",
            album = "The Bends",
            artworkUrl = null,
            artistArtworkUrl = null,
            durationMs = 290_000,
            positionMs = 96_000,
            readAtMs = nowMs,
            speed = 1f,
            isPlaying = true,
            quality = "OPUS · 160 kbps",
            isYouTube = false,
        )
    }
}
