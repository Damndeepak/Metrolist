// YouTubeCaptionsLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import com.music.innertube.YouTube

/**
 * The video's captions: YouTube's auto-generated ones, or a track uploaded in the same language.
 * Speech recognition gets sung words wrong often enough that these are only shown when no other
 * source has the song - see [LyricsHelper]'s tiers.
 */
object YouTubeCaptionsLyricsProvider : LyricsProvider {
    // the registry key, like the other YouTube sources: this is what gets stored with the lyrics
    override val name = "YouTubeCaptions"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.captions(id)
}
