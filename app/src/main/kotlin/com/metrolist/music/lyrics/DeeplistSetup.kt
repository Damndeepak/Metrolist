package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.*
import com.metrolist.music.constants.*
import java.util.Base64
import org.json.JSONObject

internal data class SetupField(val key: String, val default: Any, val min: Double = 0.0, val max: Double = 1.0, val choices: Set<String> = emptySet())

internal object DeeplistSetup {
    private const val MAX_LENGTH = 240_000
    val fields = listOf(
        SetupField(DeeplistAppleNeonKey.name, false), SetupField(DeeplistAppleSizeKey.name, 32f, 24.0, 96.0),
        SetupField(DeeplistBackdropModeKey.name, 2, 0.0, 2.0), SetupField(DeeplistBackdropBlurKey.name, .55f), SetupField(DeeplistBackdropDimKey.name, .48f, .15, .85),
        SetupField(DeeplistWordZoomKey.name, .18f, 0.0, .35), SetupField(DeeplistWordSpacingKey.name, 1f, .5, 1.4),
        SetupField(DeeplistWordGlowKey.name, true), SetupField(DeeplistAppleBeatKey.name, false), SetupField(DeeplistStackNeonKey.name, true),
        SetupField(DeeplistTapSeekKey.name, false), SetupField(DeeplistDuetEnabledKey.name, false), SetupField(DeeplistInstrumentalKey.name, true),
        *VerciStyle.entries.filter { it != VerciStyle.APPLE }.map { SetupField(deeplistSizeKey(it).name, 52f, 24.0, 200.0) }.toTypedArray(),
        SetupField(WordstormEnabledKey.name, true), SetupField(VerciLyricsKey.name, false),
        SetupField(VerciStyleKey.name, VerciStyle.APPLE.name, choices = VerciStyle.entries.map { it.name }.toSet()),
        SetupField(VerciFontKey.name, VerciFont.BOLD.name, choices = VerciFont.entries.map { it.name }.toSet()),
        SetupField(VerciTextSizeKey.name, 52f, 28.0, 200.0), SetupField(VerciSpacingKey.name, 1f, .45, 1.35),
        SetupField(VerciBrightnessKey.name, .8f), SetupField(VerciColorKey.name, 0, 0.0, 4.0),
        SetupField(VerciNeonKey.name, true), SetupField(VerciEmojiKey.name, true), SetupField(VerciAudioReactiveKey.name, true),
        SetupField(VerciAdvancedEmojiKey.name, false), SetupField(VerciMegaEmojiKey.name, true),
        SetupField(VerciCameraRingKey.name, false), SetupField(VerciParticlesKey.name, false), SetupField(VerciClockKey.name, false),
        SetupField(VerciBeatLiftKey.name, true), SetupField(VerciDoubleTapKey.name, true), SetupField(VerciSwipeKey.name, true),
        SetupField(DeeplistEmojiDensityKey.name, 1, 0.0, 2.0), SetupField(DeeplistEmojiPackKey.name, 0, 0.0, 2.0),
        SetupField(DeeplistSoftBlurKey.name, .35f), SetupField(DeeplistAnimationKey.name, 1f),
        SetupField(DeeplistArtworkBackgroundKey.name, true), SetupField(DeeplistAutoPerformanceKey.name, true),
        SetupField(DeeplistPerformanceKey.name, false), SetupField(WordstormPresetKey.name, 1, 0.0, 2.0),
        SetupField(WordstormFontSizeKey.name, 44f, 30.0, 64.0), SetupField(WordstormMotionKey.name, true),
        SetupField(WordstormCoverKey.name, false), SetupField(WordstormAccentKey.name, 0, 0.0, 2.0),
        SetupField(DeeplistNeonKey.name, .65f), SetupField(DeeplistSpeedKey.name, 1f, .7, 1.7),
        SetupField(DeeplistContextKey.name, false), SetupField(DeeplistBackgroundKey.name, 1, 0.0, 2.0),
        SetupField(DeeplistDensityKey.name, 28, 6.0, 36.0), SetupField(DeeplistRainSpeedKey.name, 1f, .2, 2.0),
        SetupField(DeeplistWordBrightnessKey.name, .23f, .07, .32), SetupField(DeeplistRingsKey.name, true),
        SetupField(DeeplistGlowKey.name, .4f), SetupField(DeeplistSparksKey.name, .65f),
        SetupField(DeeplistLiftKey.name, 7f, 0.0, 14.0), SetupField(DeeplistFlightKey.name, .43f, .2, .65),
        SetupField(DeeplistGesturesKey.name, true), SetupField(DeeplistChorusBoostKey.name, true),
    )
    fun snapshot(preferences: Preferences, corrections: Boolean = true): String {
        val values = JSONObject()
        fields.forEach { f ->
            val value: Any = when (f.default) {
                is Boolean -> preferences[booleanPreferencesKey(f.key)] ?: f.default
                is Int -> preferences[intPreferencesKey(f.key)] ?: f.default
                is Float -> preferences[floatPreferencesKey(f.key)] ?: f.default
                else -> preferences[stringPreferencesKey(f.key)] ?: f.default
            }
            values.put(f.key, value)
        }
        if (corrections) values.put(DeeplistEmojiCorrectionsKey.name, preferences[DeeplistEmojiCorrectionsKey] ?: "[]")
        return JSONObject().put("format", "deeplist-setup").put("version", 1).put("settings", values).toString()
    }
    fun shareCode(json: String): String = "DPL1." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))

    fun decode(input: String): Map<String, Any> {
        require(input.length <= MAX_LENGTH)
        val value = input.trim()
        val json = if (value.startsWith("DPL1.")) String(Base64.getUrlDecoder().decode(value.substring(5)), Charsets.UTF_8) else value
        require(json.length <= MAX_LENGTH)
        val root = JSONObject(json)
        require(root.getString("format") == "deeplist-setup" && root.get("version") == 1)
        val settings = root.getJSONObject("settings")
        require(settings.length() <= fields.size + 1)
        val valid = fields.associateBy { it.key }
        return settings.keys().asSequence().associateWith { key ->
            val value = settings.get(key)
            if (key == DeeplistEmojiCorrectionsKey.name) {
                require(value is String); decodeEmojiCorrections(value); value
            } else {
                val field = requireNotNull(valid[key])
                when (field.default) {
                    is Boolean -> { require(value is Boolean); value }
                    is Int -> {
                        require(value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 && value.toDouble() in field.min..field.max)
                        value.toInt()
                    }
                    is Float -> {
                        require(value is Number && value.toDouble().isFinite() && value.toFloat() in field.min.toFloat()..field.max.toFloat())
                        value.toFloat()
                    }
                    else -> { require(value is String && value in field.choices); value }
                }
            }
        }
    }
    fun apply(preferences: MutablePreferences, values: Map<String, Any>, replace: Boolean) {
        if (replace) {
            fields.forEach { f -> when (f.default) {
                is Boolean -> preferences.remove(booleanPreferencesKey(f.key))
                is Int -> preferences.remove(intPreferencesKey(f.key))
                is Float -> preferences.remove(floatPreferencesKey(f.key))
                else -> preferences.remove(stringPreferencesKey(f.key))
            } }
            preferences.remove(DeeplistEmojiCorrectionsKey)
        }
        values.forEach { (key, value) -> when (value) {
            is Boolean -> preferences[booleanPreferencesKey(key)] = value
            is Int -> preferences[intPreferencesKey(key)] = value
            is Float -> preferences[floatPreferencesKey(key)] = value
            is String -> {
                preferences[stringPreferencesKey(key)] = if (key == DeeplistEmojiCorrectionsKey.name && !replace) {
                    val incoming = decodeEmojiCorrections(value)
                    val current = decodeEmojiCorrections(preferences[DeeplistEmojiCorrectionsKey] ?: "[]")
                    encodeEmojiCorrections((incoming + current).distinctBy { it.song to it.phrase })
                } else value
            }
        } }
    }
}
