// DeezerLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import com.music.directlyrics.Deezer
import com.mochyy.mochymusic.constants.EnableDeezerKey
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableDeezerKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
