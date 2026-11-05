package com.mochyy.mochymusic.i18n

import com.mochyy.mochymusic.constants.SYSTEM_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test fun `system default follows the device`() {
        val device = Locale.forLanguageTag("id-ID")
        assertEquals(device, resolveUiLocale(SYSTEM_DEFAULT, device))
        assertEquals(device, resolveUiLocale(null, device))
    }

    @Test fun `explicit override wins over device`() {
        assertEquals(Locale.ENGLISH, resolveUiLocale("en", Locale.forLanguageTag("id-ID")))
        assertEquals(Locale.forLanguageTag("id"), resolveUiLocale("id", Locale.JAPANESE))
        assertEquals(Locale.forLanguageTag("zh-TW"), resolveUiLocale("zh-TW", Locale.ENGLISH))
    }

    @Test fun `empty or malformed selection follows system`() {
        assertEquals(Locale.JAPANESE, resolveUiLocale("", Locale.JAPANESE))
        assertEquals(Locale.JAPANESE, resolveUiLocale("!!!", Locale.JAPANESE))
        assertEquals(Locale.ENGLISH, resolveUiLocale(null, Locale.ROOT))
    }

    @Test fun `missing resources fall back to English before other locales`() {
        assertEquals(listOf(Locale.forLanguageTag("id"), Locale.ENGLISH), uiLocaleFallbacks(Locale.forLanguageTag("id")))
        assertEquals(listOf(Locale.forLanguageTag("fr-CA"), Locale.ENGLISH), uiLocaleFallbacks(Locale.forLanguageTag("fr-CA")))
        assertEquals(listOf(Locale.ENGLISH), uiLocaleFallbacks(Locale.ENGLISH))
    }
}
