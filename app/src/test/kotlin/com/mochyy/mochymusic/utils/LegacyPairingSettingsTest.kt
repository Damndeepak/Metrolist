package com.mochyy.mochymusic.utils

import androidx.datastore.preferences.core.*
import com.mochyy.mochymusic.deeplist.DeeplistSyncEnabledKey
import com.mochyy.mochymusic.deeplist.DeeplistWebsiteKey
import com.mochyy.mochymusic.deeplist.DeeplistTokenKey
import org.junit.Assert.*
import org.junit.Test

class LegacyPairingSettingsTest {
    private fun legacy() = mutablePreferencesOf(
        DeeplistSyncEnabledKey to true,
        DeeplistWebsiteKey to "https://world.damndeepak.workers.dev/",
        DeeplistTokenKey to "a".repeat(64),
        stringPreferencesKey("playerStyle") to "OLD_PLAYER",
        stringPreferencesKey("verciStyle") to "APPLE",
        floatPreferencesKey("deeplistAppleSize") to 96f,
        stringPreferencesKey("innerTubeCookie") to "legacy-session",
    )

    @Test fun `native Mochy settings retain only website pairing from old settings`() {
        val copied = copyLegacyDeeplistPairing(emptyPreferences(), legacy())
        assertEquals(true, copied[DeeplistSyncEnabledKey])
        assertEquals("https://world.damndeepak.workers.dev", copied[DeeplistWebsiteKey])
        assertEquals("a".repeat(64), copied[DeeplistTokenKey])
        assertEquals(setOf(DeeplistSyncEnabledKey, DeeplistWebsiteKey, DeeplistTokenKey, LegacyPairingMigratedKey), copied.asMap().keys)
        assertEquals("APPLE", legacy()[stringPreferencesKey("verciStyle")])
    }

    @Test fun `migration runs only once for schema38 and never replaces a new pairing`() {
        assertFalse(needsLegacyPairingMigration(emptyPreferences(), null))
        assertFalse(needsLegacyPairingMigration(emptyPreferences(), 42))
        assertTrue(needsLegacyPairingMigration(emptyPreferences(), 38))
        val native = mutablePreferencesOf(DeeplistTokenKey to "b".repeat(64), stringPreferencesKey("playerStyle") to "VIDEO")
        val copied = copyLegacyDeeplistPairing(native, legacy())
        assertEquals("b".repeat(64), copied[DeeplistTokenKey])
        assertEquals("VIDEO", copied[stringPreferencesKey("playerStyle")])
        assertFalse(needsLegacyPairingMigration(copied, 38))
        assertEquals(copied, copyLegacyDeeplistPairing(copied, legacy().apply { this[DeeplistTokenKey] = "c".repeat(64) }))
    }

    @Test fun `invalid website or token is not migrated and disabled sync stays disabled`() {
        listOf(
            legacy().apply { this[DeeplistWebsiteKey] = "https://example.com" },
            legacy().apply { this[DeeplistTokenKey] = "short" },
        ).forEach { old ->
            val copied = copyLegacyDeeplistPairing(emptyPreferences(), old)
            assertNull(copied[DeeplistTokenKey])
            assertEquals(true, copied[LegacyPairingMigratedKey])
        }
        val disabled = copyLegacyDeeplistPairing(emptyPreferences(), legacy().apply { this[DeeplistSyncEnabledKey] = false })
        assertEquals(false, disabled[DeeplistSyncEnabledKey])
    }
}
