package com.mochyy.mochymusic.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object MonochromeApiCanvas {
    private val INSTANCES = listOf(
        "https://eu-central.monochrome.tf/",
        "https://us-west.monochrome.tf/",
        "https://arran.monochrome.tf/",
        "https://api.monochrome.tf/"
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            expectSuccess = false
        }
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long
    )

    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24

    /** the animated cover of the song by the artist, or null, a result more than a few seconds off the song's length is another recording */
    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        durationSec: Int? = null,
    ): CanvasArtwork? {
        val key = cacheKey("search", song, artist, album ?: "", durationSec?.toString() ?: "")
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }

        val result = searchForVideoCover(song, artist, album, durationSec)
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return result
    }

    private suspend fun searchForVideoCover(
        song: String,
        artist: String,
        album: String?,
        durationSec: Int?,
    ): CanvasArtwork? {

        val query = if (!album.isNullOrBlank()) "$artist - $song - $album" else "$artist - $song"

        for (baseUrl in INSTANCES) {
            try {
                val response = client.get("${baseUrl}search/") {
                    parameter("s", query)
                }
                if (response.status != HttpStatusCode.OK) continue

                val root = response.body<JsonObject>()
                val tracksSection = findSearchSection(root, "tracks") ?: continue
                val items = tracksSection.jsonObject["items"]?.jsonArray ?: continue

                // only the song itself counts, a loose "title contains" match once fell through to another song by the same artist and showed its canvas
                for (item in items) {
                    val track = item.jsonObject

                    val trackTitle = track["title"]?.jsonPrimitive?.contentOrNull ?: continue
                    if (!CanvasMatch.sameSong(trackTitle, song)) continue

                    val resultArtists = track["artists"]?.jsonArray.orEmpty()
                        .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                    if (resultArtists.none { CanvasMatch.sameArtist(it, artist) }) continue

                    val trackSeconds = track["duration"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    if (!CanvasMatch.closeDuration(trackSeconds, durationSec)) continue

                    val albumObj = track["album"]?.jsonObject ?: continue
                    val videoCover = albumObj["videoCover"]?.jsonPrimitive?.contentOrNull

                    if (!videoCover.isNullOrBlank()) {
                        val videoUrl = formatVideoUrl(videoCover)
                        if (videoUrl != null) {
                            return CanvasArtwork(
                                name = trackTitle,
                                artist = resultArtists.first(),
                                videoUrl = videoUrl
                            )
                        }
                    }
                }
            } catch (e: Exception) {

                continue
            }
        }
        return null
    }

    private fun findSearchSection(source: JsonElement, key: String): JsonElement? {
        if (source is JsonObject) {
            if (source.containsKey("items") && source["items"] is JsonArray) return source

            if (source.containsKey(key)) {
                val found = findSearchSection(source[key]!!, key)
                if (found != null) return found
            }

            for (value in source.values) {
                val found = findSearchSection(value, key)
                if (found != null) return found
            }
        } else if (source is JsonArray) {
            for (element in source) {
                val found = findSearchSection(element, key)
                if (found != null) return found
            }
        }
        return null
    }

    internal fun formatVideoUrl(id: String): String? {
        val parts = id.split("-")
        if (parts.size != 5) return null

        return "https://resources.tidal.com/videos/${parts[0]}/${parts[1]}/${parts[2]}/${parts[3]}/${parts[4]}/1280x1280.mp4"
    }

    private fun cacheKey(prefix: String, vararg parts: String): String {
        return "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
    }
}
