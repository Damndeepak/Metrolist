package com.music.musixmatch

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import kotlin.math.abs

// musixmatch's android app api: plain http with a free token, no bot check, and its search knows every song page's address
internal object MusixmatchApi {
    private const val Base = "https://apic.musixmatch.com/ws/1.1/"
    private const val AppId = "android-player-v1.0"
    private const val Prefs = "musixmatch"
    private const val TokenKey = "user_token"
    private const val Tag = "Musixmatch"
    // youtube's upload and musixmatch's recording of one song rarely differ by more than a couple of seconds
    private const val LengthSlackSeconds = 3

    sealed interface Search {
        /** the best page, and the other copies musixmatch keeps of the same song, best first */
        data class Hit(val track: JsonObject, val duplicates: List<JsonObject>) : Search
        data object Missing : Search
        /** no connection, or musixmatch wants a captcha for every token we can get */
        data object Failed : Search
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var token: String? = null

    fun search(client: OkHttpClient, context: Context?, title: String, artist: String, durationSeconds: Int?): Search {
        val titles = titleVariants(title)
        val found = mutableListOf<JsonObject>()
        for (variant in titles) {
            found += tracks(client, context, "q_track" to variant, "q_artist" to artist) ?: return Search.Failed
            // the same title with its analysis is as good as it gets, so the other ways of writing it aren't asked for
            val best = rank(found, titles, artist, durationSeconds).firstOrNull()
            if (best != null && best.exactTitle && best.analysed) break
        }
        var ranked = rank(found, titles, artist, durationSeconds)
        // a title written another way can still only turn up in the loose search, where only its length gives it away
        for (variant in titles) {
            if (ranked.isNotEmpty()) break
            val loose = tracks(client, context, "q" to "$artist $variant") ?: return Search.Failed
            ranked = rank(loose, titles, artist, durationSeconds)
        }
        val best = ranked.firstOrNull() ?: return Search.Missing
        // copies with the very same title by the same artist are kept in case the best one's page has nothing on the song
        val duplicates = ranked.drop(1).filter { it.exactTitle }.map { it.track }
        return Search.Hit(best.track, duplicates)
    }

    // what the app api says about a song: everything but the meaning, which only its page has
    fun songOf(track: JsonObject, title: String, artist: String, duplicates: List<JsonObject> = emptyList()): MusixmatchSong {
        val genres = listOf("primary_genres", "secondary_genres").flatMap { key ->
            (track.obj(key)?.get("music_genre_list") as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.obj("music_genre")?.string("music_genre_name") }
        }
        return MusixmatchSong(
            title = track.string("track_name") ?: title,
            artist = track.string("artist_name") ?: artist,
            album = track.string("album_name"),
            releaseDate = track.string("first_release_date")?.take(10),
            genres = genres.distinct(),
            coverUrl = listOf("album_coverart_800x800", "album_coverart_500x500", "album_coverart_350x350")
                .firstNotNullOfOrNull { key -> track.string(key)?.takeIf { it.startsWith("http") && "nocover" !in it } },
            spotifyId = track.string("track_spotify_id")
                ?: (track["commontrack_spotify_ids"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull },
            appleMusicId = (track["commontrack_itunes_ids"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull },
            isExplicit = track.int("explicit") == 1,
            pageUrl = track.string("track_share_url")?.substringBefore('?')?.let(::decodeUrl)
                ?: track.string("commontrack_vanity_id")?.let { "https://www.musixmatch.com/lyrics/$it" },
            lengthSeconds = track.int("track_length")?.takeIf { it > 0 },
            alternatePages = duplicates.mapNotNull(::pageOf).distinct().filter { it != pageOf(track) },
        )
    }

    private fun pageOf(track: JsonObject): String? = track.string("track_share_url")?.substringBefore('?')?.let(::decodeUrl)
        ?: track.string("commontrack_vanity_id")?.let { "https://www.musixmatch.com/lyrics/$it" }

    internal class Candidate(val track: JsonObject, val score: Int, val exactTitle: Boolean, val analysed: Boolean)

    // musixmatch keeps copies of popular songs under a numbered or tribute artist ("NewJeans-5/DITTO", "Tonight i'm Adele"): the artist's own copy with the song's analysis comes first
    internal fun rank(tracks: List<JsonObject>, titles: List<String>, artist: String, durationSeconds: Int?): List<Candidate> {
        val wantedArtist = normalize(artist)
        val wantedTitles = titles.map(::normalize)
        return tracks
            .distinctBy { it.string("track_share_url") ?: it.int("track_id") }
            .filter { sameWords(normalize(it.string("artist_name").orEmpty()), wantedArtist) }
            .mapNotNull { track ->
                val name = normalize(track.string("track_name").orEmpty())
                val sameLength = durationSeconds != null &&
                    track.int("track_length")?.let { abs(it - durationSeconds) <= LengthSlackSeconds } == true
                val exact = name in wantedTitles
                val titleScore = when {
                    exact -> 3
                    wantedTitles.any { sameWords(name, it) } -> 2
                    sameLength -> 1
                    else -> return@mapNotNull null
                }
                val analysed = track.int("has_lyrics_analysis") == 1
                // "adele" is adele; "tonight i'm adele" only has her name in it, and that matters more than a few seconds of length
                val ownArtist = normalize(track.string("artist_name").orEmpty()) == wantedArtist
                // an address like "/track-1" is a page made before the song had its name, often left empty
                val namedPage = track.string("track_share_url")?.let { url -> wantedTitles.any { sameWords(normalize(slugOf(url)), it) } } == true
                val score = titleScore * 4 + (if (ownArtist) 2 else 0) + (if (analysed) 2 else 0) + (if (sameLength) 1 else 0) + (if (namedPage) 2 else 0)
                Candidate(track, score, exact, analysed)
            }
            // sortedBy is stable, so ties keep musixmatch's own order, which is by rating
            .sortedByDescending { it.score }
    }

    // the song part of a page address, "/lyrics/eill-3/Finale" gives "Finale"
    private fun slugOf(url: String): String = decodeUrl(url.substringBefore('?').substringAfterLast('/')).replace('-', ' ')

    /** the search's tracks, best rated first; null when musixmatch couldn't be asked */
    private fun tracks(client: OkHttpClient, context: Context?, vararg query: Pair<String, String>): List<JsonObject>? {
        val body = call(client, context, "track.search", *query, "page_size" to "10", "s_track_rating" to "desc") ?: return null
        return (body["track_list"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.obj("track") }
    }

    // a token musixmatch has started asking a captcha for is swapped for a fresh one, once
    private fun call(client: OkHttpClient, context: Context?, method: String, vararg query: Pair<String, String>): JsonObject? {
        repeat(2) { attempt ->
            val userToken = token(client, context, fresh = attempt > 0) ?: return null
            val message = get(client, method, *query, "usertoken" to userToken) ?: return null
            val status = message.obj("header")?.int("status_code")
            if (status == 200) return message.obj("body") ?: JsonObject(emptyMap())
            Timber.tag(Tag).d("$method answered $status")
            if (status != 401) return null
        }
        return null
    }

    // kept on disk: asking for a new one costs most of a second on every app start
    private fun token(client: OkHttpClient, context: Context?, fresh: Boolean): String? {
        val prefs = context?.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        if (!fresh) {
            token?.let { return it }
            prefs?.getString(TokenKey, null)?.let { saved -> token = saved; return saved }
        }
        // a token of only zeros is musixmatch saying "captcha first"
        val newToken = get(client, "token.get")?.obj("body")?.string("user_token")?.takeIf { it.any { c -> c != '0' } }
        // a refused refresh keeps the old token: musixmatch's captcha wears off by itself after a while
        if (newToken != null) {
            token = newToken
            prefs?.edit()?.putString(TokenKey, newToken)?.apply()
        }
        return newToken
    }

    /** the reply's message (header and body), or null when there was no answer at all */
    private fun get(client: OkHttpClient, method: String, vararg query: Pair<String, String>): JsonObject? {
        val url = (Base + method).toHttpUrl().newBuilder()
            .addQueryParameter("app_id", AppId)
            .addQueryParameter("format", "json")
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val text = runCatching {
            client.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
        }.onFailure { Timber.tag(Tag).d(it, "$method failed") }.getOrNull() ?: return null
        return runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()?.obj("message")
    }
}
