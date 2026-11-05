package com.music.genius

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** what genius knows about one song; whatever it didn't say stays null or empty */
data class GeniusSong(
    val title: String,
    val artist: String,
    /** the song's "about", written by fans and sometimes the artist; null when nobody has yet */
    val about: String?,
    /** "2014-03-17" */
    val releaseDate: String?,
    val album: String?,
    val writers: List<String>,
    val producers: List<String>,
    val genres: List<String>,
    val pageUrl: String,
    val artistImageUrl: String?,
    val spotifyId: String?,
    val appleMusicId: String?,
)

// genius's own website api, the one its pages call: search and song details answer without a key
object Genius {
    private const val Api = "https://genius.com/api"
    private const val UserAgent = "Mochy (https://github.com/realidkroo/Mochy-Music)"
    private const val Tag = "Genius"
    // a title match (80 or 40) plus the artist (40): anything less is a different song
    private const val GoodMatch = 60

    private sealed interface Lookup {
        data class Found(val song: GeniusSong) : Lookup
        data object Missing : Lookup
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    // only real answers are kept for the run; a failed try is asked again next time
    private val songs = ConcurrentHashMap<String, Lookup>()

    /** the best match for the song and its details, or null when genius doesn't have it (or couldn't be asked) */
    suspend fun song(title: String, artist: String): GeniusSong? = withContext(Dispatchers.IO) {
        val key = "${title.lowercase()}|${artist.lowercase()}"
        songs[key]?.let { return@withContext (it as? Lookup.Found)?.song }
        val lookup = runCatching { lookUp(title, artist) }
            .onFailure { Timber.tag(Tag).w(it, "couldn't read $title by $artist") }
            .getOrNull()
        if (lookup != null) songs[key] = lookup
        (lookup as? Lookup.Found)?.song
    }

    private fun lookUp(title: String, artist: String): Lookup? {
        val leadArtist = artist.substringBefore(",").substringBefore("&").trim()
        val search = get("$Api/search/multi?per_page=5&q=${URLEncoder.encode("$leadArtist $title", "UTF-8")}") ?: return null
        val hits = (search.obj("response")?.get("sections") as? JsonArray).orEmpty()
            .filter { (it as? JsonObject)?.string("type") == "song" }
            .flatMap { ((it as JsonObject)["hits"] as? JsonArray).orEmpty() }
            .mapNotNull { (it as? JsonObject)?.obj("result") }
        val best = hits
            .map { it to score(it, title, leadArtist) }
            .filter { it.second >= GoodMatch }
            .maxByOrNull { it.second }
            ?.first
            ?: return Lookup.Missing
        val id = (best["id"] as? JsonPrimitive)?.longOrNull ?: return Lookup.Missing
        val song = get("$Api/songs/$id?text_format=plain")?.obj("response")?.obj("song") ?: return null
        return Lookup.Found(read(song))
    }

    // titles come as "フィナーレ。(finale.)": either half may be the one the player knows
    private fun score(result: JsonObject, title: String, artist: String): Int {
        val artistName = result.obj("primary_artist")?.string("name").orEmpty()
        // genius files translations and romanizations under its own "genius english translations" and friends
        if (artistName.startsWith("Genius ")) return 0
        val wanted = normalize(title)
        val shown = result.string("title").orEmpty()
        val names = listOf(shown, shown.substringBefore("("), shown.substringAfter("(", "").substringBeforeLast(")"))
            .map(::normalize)
            .filter { it.isNotEmpty() }
        var score = when {
            names.any { it == wanted } -> 80
            names.any { it.contains(wanted) || wanted.contains(it) } -> 40
            else -> 0
        }
        val artists = listOf(artistName, artistName.substringBefore("("), result.string("artist_names").orEmpty()).map(::normalize)
        val wantedArtist = normalize(artist)
        if (wantedArtist.isNotEmpty() && artists.any { it.isNotEmpty() && (it == wantedArtist || it.contains(wantedArtist)) }) score += 40
        return score
    }

    private fun read(song: JsonObject): GeniusSong {
        val about = song.obj("description")?.string("plain")?.trim()?.takeIf { it.isNotEmpty() && it != "?" }
        fun names(key: String) = (song[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("name") }
        val media = (song["media"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return GeniusSong(
            title = song.string("title").orEmpty(),
            artist = song.obj("primary_artist")?.string("name").orEmpty(),
            about = about,
            releaseDate = song.string("release_date"),
            album = song.obj("album")?.string("name"),
            writers = names("writer_artists"),
            producers = names("producer_artists"),
            // the tags genius marks primary are genres; the rest are places and languages
            genres = (song["tags"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .filter { (it["primary"] as? JsonPrimitive)?.booleanOrNull == true }
                .mapNotNull { it.string("name") },
            pageUrl = song.string("url").orEmpty(),
            artistImageUrl = song.obj("primary_artist")?.string("image_url")?.takeIf { !it.contains("default_avatar") },
            spotifyId = song.string("spotify_uuid")
                ?: media.firstOrNull { it.string("provider") == "spotify" }?.string("url")?.substringAfterLast('/'),
            appleMusicId = song.string("apple_music_id"),
        )
    }

    private fun get(url: String): JsonObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent)
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.tag(Tag).d("${response.code} for ${url.substringBefore('?')}")
                return null
            }
            runCatching { json.parseToJsonElement(response.body.string()) as? JsonObject }.getOrNull()
        }
    }

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
