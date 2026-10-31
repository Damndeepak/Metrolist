package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeeplistLookTest {
    @Test fun `Apple becomes default once and later choices survive restart`() {
        val prefs = mutablePreferencesOf().apply { this[VerciStyleKey] = "SHIP"; this[DeeplistGlobalLookInitializedKey] = true; this[DeeplistTokenKey] = "keep"; this[deeplistSizeKey(VerciStyle.SHIP)] = 140f }
        initializeDeeplistLook(prefs)
        assertEquals("APPLE", prefs[VerciStyleKey])
        assertEquals(true, prefs[VerciLyricsKey])
        assertEquals(true, prefs[WordstormEnabledKey])
        assertEquals(false, prefs[DeeplistPerSongLookKey])
        assertEquals("keep", prefs[DeeplistTokenKey])
        assertEquals(34f, prefs[DeeplistAppleSizeKey])
        assertEquals(false, prefs[DeeplistWordGlowKey])
        assertEquals(false, prefs[VerciEmojiKey])
        assertEquals(1, prefs[DeeplistBackdropModeKey])
        assertEquals(140f, prefs[deeplistSizeKey(VerciStyle.SHIP)])
        prefs[VerciStyleKey] = "SHIP"
        prefs[VerciLyricsKey] = false
        initializeDeeplistLook(prefs)
        assertEquals("SHIP", prefs[VerciStyleKey])
        assertEquals(false, prefs[VerciLyricsKey])
    }

    @Test fun `legacy song snapshots cannot overwrite global look after song changes or reopening`() {
        val old = mutablePreferencesOf().apply { this[VerciStyleKey] = "APPLE"; this[VerciTextSizeKey] = 32f }
        val prefs = mutablePreferencesOf().apply { this[DeeplistSongStylesKey] = JSONObject().put("song", DeeplistSetup.snapshot(old, false)).toString() }
        initializeDeeplistLook(prefs)
        prefs[VerciTextSizeKey] = 150f
        listOf("song", "other", "song").forEach { applyDeeplistSongLook(prefs, it) }
        assertEquals("APPLE", prefs[VerciStyleKey])
        assertEquals(150f, prefs[VerciTextSizeKey])
        prefs[DeeplistPerSongLookKey] = true
        applyDeeplistSongLook(prefs, "song")
        assertEquals("APPLE", prefs[VerciStyleKey])
        assertEquals(32f, prefs[VerciTextSizeKey])
    }
}
