// OfflineMode.kt

package com.mochyy.mochymusic.utils

import com.music.innertube.YouTube

/**
 * Offline mode as the rest of the app reads it, without waiting on the data store: the player,
 * the lyrics lookup and the YouTube client all check it at the moment they would go online. App
 * sets it at startup and on every change of the setting.
 */
object OfflineMode {
    @Volatile
    var isOn: Boolean = false
        private set

    fun set(on: Boolean) {
        isOn = on
        YouTube.offline = on
    }
}
