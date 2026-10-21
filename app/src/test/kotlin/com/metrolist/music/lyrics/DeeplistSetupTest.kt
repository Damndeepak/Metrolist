package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeeplistSetupTest {
    @Test fun `setup round trip preserves appearance but excludes credentials and history`() {
        val prefs = mutablePreferencesOf(VerciTextSizeKey to 160f, VerciStyleKey to "APPLE", DeeplistTokenKey to "private-token", DeeplistWebsiteKey to "https://private.example", DeeplistSavedLyricsKey to "private history")
        val json = DeeplistSetup.snapshot(prefs)
        assertFalse(json.contains("private"))
        val imported = DeeplistSetup.decode(DeeplistSetup.shareCode(json))
        val destination = mutablePreferencesOf(DeeplistTokenKey to "keep-token")
        DeeplistSetup.apply(destination, imported, true)
        assertEquals(160f, destination[VerciTextSizeKey])
        assertEquals("APPLE", destination[VerciStyleKey])
        assertEquals("keep-token", destination[DeeplistTokenKey])
        assertTrue(DeeplistSetup.decode(DeeplistSetup.snapshot(mutablePreferencesOf())).isNotEmpty())
    }
    @Test fun `invalid versions credentials booleans and extreme values are rejected before writes`() {
        val valid = JSONObject(DeeplistSetup.snapshot(mutablePreferencesOf()))
        val invalid = listOf(
            JSONObject(valid.toString()).put("version", 2),
            JSONObject(valid.toString()).apply { getJSONObject("settings").put(DeeplistTokenKey.name, "steal") },
            JSONObject(valid.toString()).apply { getJSONObject("settings").put(VerciTextSizeKey.name, 100000) },
            JSONObject(valid.toString()).apply { getJSONObject("settings").put(VerciStyleKey.name, "unknown") },
            JSONObject(valid.toString()).apply { getJSONObject("settings").put(VerciEmojiKey.name, "true") },
        )
        invalid.forEach { assertTrue(runCatching { DeeplistSetup.decode(it.toString()) }.isFailure) }
        assertTrue(runCatching { DeeplistSetup.decode("DPL1.bad!") }.isFailure)
        assertTrue(runCatching { DeeplistSetup.decode("x".repeat(240001)) }.isFailure)
    }
    @Test fun `merge retains unrelated settings and corrections while replace resets appearance only`() {
        val original = encodeEmojiCorrections(listOf(EmojiCorrection("rain", "🌧️")))
        val incoming = encodeEmojiCorrections(listOf(EmojiCorrection("heart", "🖤")))
        val prefs = mutablePreferencesOf(VerciTextSizeKey to 80f, DeeplistEmojiCorrectionsKey to original, DeeplistTokenKey to "token")
        DeeplistSetup.apply(prefs, mapOf(VerciColorKey.name to 3, DeeplistEmojiCorrectionsKey.name to incoming), false)
        assertEquals(80f, prefs[VerciTextSizeKey])
        assertEquals(2, decodeEmojiCorrections(prefs[DeeplistEmojiCorrectionsKey]!!).size)
        DeeplistSetup.apply(prefs, mapOf(VerciColorKey.name to 1), true)
        assertNull(prefs[VerciTextSizeKey]); assertNull(prefs[DeeplistEmojiCorrectionsKey])
        assertEquals("token", prefs[DeeplistTokenKey])
    }
}
