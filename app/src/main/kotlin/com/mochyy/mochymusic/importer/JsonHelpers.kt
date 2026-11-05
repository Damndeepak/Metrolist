// JsonHelpers.kt

package com.mochyy.mochymusic.importer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

// lenient readers for third-party json: a missing or differently-typed field is null, never a crash

internal fun JsonElement.path(vararg keys: String): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) current = (current as? JsonObject)?.get(key) ?: return null
    return current
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

/** a number or a numeric string, as ids sometimes come either way */
internal fun JsonObject.idString(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray).orEmpty()

private val OFFSET_WITHOUT_COLON = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]Z")

/** a timestamp as services send them (iso with a z, or tidal's +0000 offset), in epoch ms */
internal fun dateMs(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return runCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(text, OFFSET_WITHOUT_COLON).toInstant().toEpochMilli() }.getOrNull()
}
