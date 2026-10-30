package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.MutablePreferences
import com.metrolist.music.constants.*
import org.json.JSONObject

internal fun initializeDeeplistLook(preferences: MutablePreferences) {
    if (preferences[DeeplistGlobalLookInitializedKey] == true) return
    preferences[VerciStyleKey] = VerciStyle.SHIP.name
    preferences[VerciLyricsKey] = true
    preferences[WordstormEnabledKey] = true
    preferences[DeeplistPerSongLookKey] = false
    preferences[DeeplistGlobalLookInitializedKey] = true
}

internal fun applyDeeplistSongLook(preferences: MutablePreferences, track: String) {
    if (preferences[DeeplistPerSongLookKey] != true) return
    val json = runCatching { JSONObject(preferences[DeeplistSongStylesKey] ?: "{}").optString(track) }.getOrDefault("")
    if (json.isBlank()) return
    runCatching { DeeplistSetup.decode(json) }.onSuccess { DeeplistSetup.apply(preferences, it, false) }
}
