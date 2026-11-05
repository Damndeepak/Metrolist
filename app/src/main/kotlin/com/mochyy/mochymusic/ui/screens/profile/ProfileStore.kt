// ProfileStore.kt
//
// Saving the profile's name and picture. The picture is copied into the app's own files, because
// the one the picker hands over can vanish (a cropped copy in the cache, a deleted gallery photo).

package com.mochyy.mochymusic.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.constants.UsernameKey
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object ProfileStore {

    private const val PhotoPrefix = "profile_pic_"

    /** Copies [uri] into the app's files and returns the new file's path, or null if it couldn't be read. */
    fun copyPhoto(context: Context, uri: Uri): String? = runCatching {
        // a fresh name each time, so image caches never hand back the old picture
        val file = File(context.filesDir, "$PhotoPrefix${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        file.absolutePath
    }.onFailure { Timber.w(it, "Couldn't copy the profile picture") }.getOrNull()

    /**
     * Saves [name], and [photo] if a new one was picked. It finishes even if the screen that asked
     * goes away halfway, so a closed sheet never leaves a picture copied but not saved.
     */
    suspend fun save(context: Context, name: String, photo: Uri?) = withContext(NonCancellable + Dispatchers.IO) {
        val app = context.applicationContext
        val newPath = photo?.let { copyPhoto(app, it) }
        var oldPath: String? = null
        app.dataStore.edit { prefs ->
            prefs[UsernameKey] = name
            if (newPath != null) {
                oldPath = prefs[ProfilePicUriKey]
                prefs[ProfilePicUriKey] = newPath
            }
        }
        oldPath?.let { deleteOldPhoto(app, it) }
    }

    /** Removes a picture this class copied earlier; anything else (a content uri, someone else's file) stays. */
    private fun deleteOldPhoto(context: Context, path: String) {
        val file = File(path)
        if (file.parentFile == context.filesDir && file.name.startsWith(PhotoPrefix)) file.delete()
    }
}
