package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.*
import org.junit.Assert.*
import org.junit.Test

class DeeplistAppleDefaultTest {
    @Test fun `default switches renderer once while preserving sizes backgrounds and later choices`() {
        val preferences = mutablePreferencesOf(DeeplistAppleSizeKey to 68f, DeeplistBackdropDimKey to .7f, VerciLyricsKey to false)
        applyDeeplistAppleDefault(preferences)
        assertEquals("APPLE", preferences[VerciStyleKey])
        assertEquals(true, preferences[VerciLyricsKey])
        assertEquals(68f, preferences[DeeplistAppleSizeKey])
        assertEquals(.7f, preferences[DeeplistBackdropDimKey])
        preferences[VerciStyleKey] = "SHIP"
        preferences[VerciLyricsKey] = false
        applyDeeplistAppleDefault(preferences)
        assertEquals("SHIP", preferences[VerciStyleKey])
        assertEquals(false, preferences[VerciLyricsKey])
    }
}
