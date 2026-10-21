package com.metrolist.music.lyrics

import android.os.Build
import java.text.Normalizer

internal fun deeplistRomanHindi(text: String): String {
    if (Build.VERSION.SDK_INT < 29 || text.none { it in '\u0900'..'\u097f' }) return text
    return runCatching {
        val roman = android.icu.text.Transliterator.getInstance("Devanagari-Latin").transliterate(text)
        Normalizer.normalize(roman, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }.getOrDefault(text)
}
