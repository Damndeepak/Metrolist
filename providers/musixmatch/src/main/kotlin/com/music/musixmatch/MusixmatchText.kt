package com.music.musixmatch

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLDecoder
import java.util.Locale

// small text and json helpers the app api and the song page both read with

internal fun normalize(text: String): String = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

// whole words only, so "sia" never matches "asia" and "love" never matches "lovely"
internal fun sameWords(found: String, wanted: String): Boolean =
    found.isNotEmpty() && wanted.isNotEmpty() && (" $found ".contains(" $wanted ") || " $wanted ".contains(" $found "))

internal fun decodeUrl(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.intOrNull?.let { n -> n == 1 } }

// a title written two ways on youtube ("フィナーレ。 - Finale.", "フィナーレ。(Finale.)") is searched whole, then each way alone: musixmatch only knows one of them
internal fun titleVariants(title: String): List<String> {
    val variants = mutableListOf(title)
    Regex("""^(.+?)\s*[(（\[](.+?)[)）\]]\s*$""").find(title)?.let { variants += it.groupValues[1]; variants += it.groupValues[2] }
    title.split(Regex("""\s+[-–—/|]\s+|\s*[／｜]\s*""")).takeIf { it.size == 2 }?.let { variants += it }
    return variants.map { it.trim() }.filter { normalize(it).isNotEmpty() }.distinct()
}
