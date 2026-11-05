// SPDX-License-Identifier: GPL-3.0-only
// Provider interfaces adapted from Clash-Projects/LastWave-Native, 2026 contributors.
package com.metrolist.music.lyrics.canvas

import com.metrolist.music.deeplist.deeplistReadLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

internal enum class CanvasSource { APPLE, TIDAL, COMMUNITY }

internal data class CanvasArtwork(
    val url: String,
    val fallbackUrl: String? = null,
    val tallUrl: String? = null,
    val wideUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: CanvasSource = CanvasSource.COMMUNITY,
) {
    fun matches(wantTitle: String, wantArtist: String): Boolean {
        val name = title?.normalizeForMatch().orEmpty()
        val wanted = splitArtists(wantArtist)
        val credited = splitArtists(artist.orEmpty())
        return name.isNotBlank() && name == wantTitle.normalizeForMatch() &&
            wanted.isNotEmpty() && credited.isNotEmpty() && wanted.all { it in credited }
    }
}

internal fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}\\p{M}\\s]"), " ")
        .replace(Regex("\\s+"), " ").trim()

internal fun splitArtists(raw: String): List<String> =
    raw.split(Regex("\\s*[,;&]\\s*|\\s+[×x]\\s+|\\b(?:feat|ft|featuring|with)\\.?\\s+", RegexOption.IGNORE_CASE))
        .map { it.normalizeForMatch() }.filter { it.isNotBlank() }

internal const val CANVAS_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

internal fun canvasGet(client: OkHttpClient, url: String, headers: Map<String, String> = emptyMap()): String? = runCatching {
    val request = Request.Builder().url(url).header("User-Agent", CANVAS_UA)
    headers.forEach { (key, value) -> request.header(key, value) }
    client.newCall(request.build()).execute().use { response ->
        if (!response.isSuccessful) null else response.body?.byteStream()?.use {
            String(deeplistReadLimited(it, 4_000_000), Charsets.UTF_8)
        }
    }
}.getOrNull()

internal object DeeplistCanvasResolver {
    private val client = OkHttpClient.Builder().callTimeout(7, TimeUnit.SECONDS).build()
    private data class Cached(val result: CanvasArtwork?, val expires: Long)
    private val cache = object : LinkedHashMap<String, Cached>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > 64
    }
    private val mutex = Mutex()

    suspend fun resolve(title: String, artist: String): CanvasArtwork? = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(Regex("""\s*\((?:official|lyric|audio|video)[^)]*\)|\s*\[(?:official|lyric|audio|video)[^]]*\]""", RegexOption.IGNORE_CASE), "").trim()
        val cleanArtist = artist.removeSuffix(" - Topic").trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null
        val key = "$cleanTitle|$cleanArtist"
        mutex.withLock {
            cache[key]?.takeIf { it.expires > System.currentTimeMillis() }?.let { return@withLock it.result }
            val sources = listOf<() -> CanvasArtwork?>(
                { AppleMusicCanvas.search(client, cleanTitle, cleanArtist, null) },
                { TidalCanvas.search(client, cleanTitle, cleanArtist, null) },
                { CommunityCanvas.search(client, cleanTitle, cleanArtist, null) },
            )
            var found: CanvasArtwork? = null
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                val candidate = runCatching { source() }.getOrNull() ?: continue
                if (candidate.matches(cleanTitle, cleanArtist) && validCanvasUrl(candidate.url)) {
                    found = candidate; break
                }
            }
            currentCoroutineContext().ensureActive()
            cache[key] = Cached(found, System.currentTimeMillis() + if (found == null) 300_000 else 21_600_000)
            found
        }
    }
}

internal fun validCanvasUrl(value: String): Boolean = runCatching {
    val url = java.net.URI(value)
    url.scheme == "https" && url.userInfo == null && !url.host.isNullOrBlank()
}.getOrDefault(false)
