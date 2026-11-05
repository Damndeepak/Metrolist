// NotRecommended.kt

package com.mochyy.mochymusic.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** songs you asked us to stop suggesting; autoplay, radios and home's picks all leave them out */
object NotRecommended {
    const val PREFS_NAME = "not_recommended"
    private const val KEY_SONGS = "songs"

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var cached: Set<String>? = null

    // opening the file here starts loading it in the background, so the first check later doesn't wait on disk
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val songs: Set<String>
        get() = cached ?: synchronized(this) {
            cached ?: prefs.getStringSet(KEY_SONGS, null).orEmpty().toSet().also { cached = it }
        }

    operator fun contains(songId: String): Boolean = songId in songs

    @Synchronized
    fun add(songId: String) = save(songs + songId)

    @Synchronized
    fun remove(songId: String) = save(songs - songId)

    private fun save(next: Set<String>) {
        cached = next
        prefs.edit { putStringSet(KEY_SONGS, next) }
    }
}
