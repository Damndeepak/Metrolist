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
    @Test fun `new lyric controls and neon stack survive setup transfer`() {
        val prefs = mutablePreferencesOf().apply {
            this[VerciStyleKey] = "NEON_STACK"
            this[deeplistSizeKey(VerciStyle.NEON_STACK)] = 190f
            this[DeeplistAppleSizeKey] = 90f
            this[DeeplistWordZoomKey] = .3f
            this[DeeplistWordSpacingKey] = .5f
            this[VerciSpacingKey] = .45f
            this[DeeplistAppleBeatKey] = true
        }
        val destination = mutablePreferencesOf()
        DeeplistSetup.apply(destination, DeeplistSetup.decode(DeeplistSetup.snapshot(prefs)), true)
        assertEquals("NEON_STACK", destination[VerciStyleKey])
        assertEquals(190f, destination[deeplistSizeKey(VerciStyle.NEON_STACK)])
        assertEquals(90f, destination[DeeplistAppleSizeKey])
        assertEquals(.5f, destination[DeeplistWordSpacingKey])
        assertEquals(.3f, destination[DeeplistWordZoomKey])
        assertEquals(true, destination[DeeplistAppleBeatKey])
    }

    @Test fun `setup round trip preserves appearance but excludes credentials and history`() {
        val prefs = mutablePreferencesOf().apply { this[VerciTextSizeKey] = 160f; this[VerciStyleKey] = "APPLE"; this[DeeplistTokenKey] = "private-token"; this[DeeplistWebsiteKey] = "https://private.example"; this[DeeplistSavedLyricsKey] = "private history" }
        val json = DeeplistSetup.snapshot(prefs)
        assertFalse(json.contains("private"))
        val imported = DeeplistSetup.decode(DeeplistSetup.shareCode(json))
        val destination = mutablePreferencesOf().apply { this[DeeplistTokenKey] = "keep-token" }
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
        val prefs = mutablePreferencesOf().apply { this[VerciTextSizeKey] = 80f; this[DeeplistEmojiCorrectionsKey] = original; this[DeeplistTokenKey] = "token" }
        DeeplistSetup.apply(prefs, mapOf(VerciColorKey.name to 3, DeeplistEmojiCorrectionsKey.name to incoming), false)
        assertEquals(80f, prefs[VerciTextSizeKey])
        assertEquals(2, decodeEmojiCorrections(prefs[DeeplistEmojiCorrectionsKey]!!).size)
        DeeplistSetup.apply(prefs, mapOf(VerciColorKey.name to 1), true)
        assertNull(prefs[VerciTextSizeKey]); assertNull(prefs[DeeplistEmojiCorrectionsKey])
        assertEquals("token", prefs[DeeplistTokenKey])
    }
}
