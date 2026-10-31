package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.MutablePreferences
import com.metrolist.music.constants.*
import org.json.JSONObject

internal fun initializeDeeplistLook(preferences: MutablePreferences) {
    if (preferences[DeeplistAppleDefaultInitializedKey] == true) return
    preferences[VerciStyleKey] = VerciStyle.APPLE.name
    preferences[DeeplistAppleSizeKey] = 34f
    preferences[DeeplistAppleNeonKey] = false
    preferences[VerciParticlesKey] = false
    preferences[VerciEmojiKey] = false
    preferences[DeeplistWordZoomKey] = .08f
    preferences[DeeplistWordSpacingKey] = 1f
    preferences[VerciSpacingKey] = 1f
    preferences[DeeplistWordGlowKey] = false
    preferences[DeeplistAppleBeatKey] = false
    preferences[DeeplistArtworkBackgroundKey] = true
    preferences[DeeplistBackdropModeKey] = 1
    preferences[DeeplistBackdropDimKey] = .60f
    preferences[DeeplistBackdropBlurKey] = .70f
    preferences[VerciLyricsKey] = true
    preferences[WordstormEnabledKey] = true
    preferences[DeeplistPerSongLookKey] = false
    preferences[DeeplistGlobalLookInitializedKey] = true
    preferences[DeeplistAppleDefaultInitializedKey] = true
}

internal fun applyDeeplistSongLook(preferences: MutablePreferences, track: String) {
    if (preferences[DeeplistPerSongLookKey] != true) return
    val json = runCatching { JSONObject(preferences[DeeplistSongStylesKey] ?: "{}").optString(track) }.getOrDefault("")
    if (json.isBlank()) return
    runCatching { DeeplistSetup.decode(json) }.onSuccess { DeeplistSetup.apply(preferences, it, false) }
}
