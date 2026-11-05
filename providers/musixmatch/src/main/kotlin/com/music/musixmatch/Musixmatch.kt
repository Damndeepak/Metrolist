package com.music.musixmatch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import timber.log.Timber
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// musixmatch's app api finds the song and its facts in a second; the meaning is only on its page, behind a bot check (docs/notes/audio-output.md)
object Musixmatch {
    private const val Site = "https://www.musixmatch.com"
    private const val WebSearch = "https://html.duckduckgo.com/html/?q="
    private const val UserAgent =
        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val Tag = "Musixmatch"
    // a title match (80 or 40) plus the artist (40): anything less is a different song
    private const val GoodMatch = 60
    // each copy is a page load through the bot check, so only a few are tried
    private const val MaxDuplicates = 3

    @Volatile
    private var appContext: Context? = null

    // null until a song page has been asked for over plain http; the bot check turns that away on most networks
    @Volatile
    private var plainPagesWork: Boolean? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .dns(searchDns)
            .build()
    }

    // indonesian isps send duckduckgo to a block page; cloudflare's dns over https still knows where it really is
    private val searchDns by lazy {
        val overHttps = DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build())
            .url("https://1.1.1.1/dns-query".toHttpUrl())
            .includeIPv6(false)
            .build()
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (!hostname.endsWith("duckduckgo.com")) Dns.SYSTEM.lookup(hostname)
                else runCatching { overHttps.lookup(hostname) }.getOrElse { Dns.SYSTEM.lookup(hostname) }
        }
    }

    /** how one lookup ended: only [Found] and [Missing] are real answers worth keeping */
    private sealed interface Lookup {
        data class Found(val song: MusixmatchSong) : Lookup
        /** musixmatch has no page for the song */
        data object Missing : Lookup
        /** couldn't ask: no connection, or the bot check held every road */
        data object Failed : Lookup
    }

    // a song asked for twice in one run (sheet closed and opened again) isn't looked up twice
    private val songs = ConcurrentHashMap<String, Lookup>()

    // pages already read, by address, so the meaning is there at once the next time
    private val pages = ConcurrentHashMap<String, MusixmatchSong>()

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    // the song's facts, quick; [readPage] adds what it's about. null when musixmatch doesn't have it (or couldn't be asked)
    suspend fun song(title: String, artist: String, durationSeconds: Int? = null): MusixmatchSong? = withContext(Dispatchers.IO) {
        val key = "${title.lowercase()}|${artist.lowercase()}"
        songs[key]?.let { cached ->
            val found = (cached as? Lookup.Found)?.song
            return@withContext found?.pageUrl?.let(pages::get) ?: found
        }
        val lookup = runCatching { lookUp(cleanTitle(title), artist.substringBefore(",").substringBefore("&").trim(), durationSeconds) }
            .onFailure { Timber.tag(Tag).w(it, "couldn't read $title by $artist") }
            .getOrDefault(Lookup.Failed)
        // a blocked or offline try is asked again the next time the sheet opens
        if (lookup !is Lookup.Failed) songs[key] = lookup
        // info, not debug: release builds keep it, so a lookup that comes up empty can be read over adb; spelled out because r8 renames the classes
        val outcome = when (lookup) {
            is Lookup.Found -> "found at ${lookup.song.pageUrl}" + lookup.song.alternatePages.takeIf { it.isNotEmpty() }?.let { ", ${it.size} more copies" }.orEmpty()
            Lookup.Missing -> "missing"
            Lookup.Failed -> "couldn't ask"
        }
        android.util.Log.i(Tag, "$title by $artist: $outcome")
        (lookup as? Lookup.Found)?.song
    }

    // the song with what its page says it's about, or null when no page could be read this time
    suspend fun readPage(song: MusixmatchSong): MusixmatchSong? = withContext(Dispatchers.IO) {
        if (song.pageRead) return@withContext song
        val url = song.pageUrl ?: return@withContext null
        pages[url]?.let { return@withContext it }
        val read = readOne(song, url)
        // this copy says nothing about the song, or wouldn't open: musixmatch keeps duplicates, and another one often has it
        val better = if (read?.meaning != null) null else song.alternatePages.take(MaxDuplicates).firstNotNullOfOrNull { other ->
            readOne(song, other)?.takeIf { it.meaning != null }
        }
        val result = better ?: read
        // the next look at this song lands straight on the copy that had something
        result?.also { pages[url] = it }
    }

    private suspend fun readOne(song: MusixmatchSong, url: String): MusixmatchSong? {
        pages[url]?.let { return it }
        val track = runCatching { page(url.removePrefix(Site)) }
            .onFailure { Timber.tag(Tag).w(it, "couldn't read $url") }
            .getOrNull()
            ?.let(::trackOf)
        val read = track?.let { merge(song, it, url) }
        android.util.Log.i(Tag, "page $url: ${if (read == null) "couldn't read" else if (read.meaning != null) "read, has a meaning" else "read, nothing about the song"}")
        return read?.also { pages[url] = it }
    }

    private suspend fun lookUp(title: String, artist: String, durationSeconds: Int?): Lookup {
        // the page will need the hidden browser: it starts and gets past the bot check while the api searches
        if (plainPagesWork != true) appContext?.let(MusixmatchBrowser::warmUp)
        when (val search = MusixmatchApi.search(client, appContext, title, artist, durationSeconds)) {
            is MusixmatchApi.Search.Hit -> return Lookup.Found(MusixmatchApi.songOf(search.track, title, artist, search.duplicates))
            MusixmatchApi.Search.Missing -> return Lookup.Missing
            MusixmatchApi.Search.Failed -> android.util.Log.i(Tag, "the app api turned us away, searching the web for $title by $artist")
        }
        return webLookUp(title, artist)
    }

    // ---- finding the page without the app api ------------------------------------------------

    private suspend fun webLookUp(title: String, artist: String): Lookup {
        val found = searchPaths(title, artist)
        android.util.Log.i(Tag, "web search for $title by $artist: ${found ?: "couldn't search"}")
        // the web search's best pages first, then a guess at the address for each way the title is written
        val paths = (found.orEmpty() + titleVariants(title).drop(1).ifEmpty { listOf(title) }.map { guessPath(it, artist) } + guessPath(title, artist)).distinct()
        var anyAnswer = found != null
        for (path in paths.take(3)) {
            val page = page(path)
            if (page != null) anyAnswer = true
            val track = page?.let(::trackOf) ?: continue
            // a web search or a guess can land on a namesake: the page itself has to name this song
            if (!sameSong(track, title, artist)) continue
            val song = merge(MusixmatchSong(title = title, artist = artist), track, "$Site$path")
            pages["$Site$path"] = song
            return Lookup.Found(song)
        }
        return if (anyAnswer) Lookup.Missing else Lookup.Failed
    }

    /** song page paths a web search turns up, best first; null when the search itself couldn't be done */
    private suspend fun searchPaths(title: String, artist: String): List<String>? {
        val url = WebSearch + URLEncoder.encode("musixmatch $artist $title lyrics", "UTF-8")
        // duckduckgo checks for unusual traffic now and then: the same search from the hidden browser usually gets through
        val html = runCatching { getText(url, "text/html") }.getOrNull()
            ?: appContext?.let { MusixmatchBrowser.html(it, url) }
            ?: return null
        // the traffic check's page has no results on it: that's "couldn't search", not "musixmatch hasn't got it"
        if (!html.contains("result__") && !html.contains("result-link") && !html.contains("no-results")) return null
        return SongLink.findAll(decodeUrl(html))
            .map { decodeUrl(it.groupValues[1]) }
            .distinct()
            .map { it to pathScore(it, title, artist) }
            .filter { it.second >= GoodMatch }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }

    // musixmatch's addresses are the names with dashes ("/lyrics/sia/chandelier"); namesake artists get a number a guess can't know
    private fun guessPath(title: String, artist: String): String {
        fun slug(text: String) = text.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
        return "/lyrics/${slug(artist)}/${slug(title)}"
    }

    // a link names the artist and song ("/lyrics/eill-3/finale"); the number only tells artists of one name apart
    private fun pathScore(path: String, title: String, artist: String): Int {
        val parts = path.removePrefix("/lyrics/").split('/')
        if (parts.size < 2) return 0
        val linkArtist = normalize(parts[0].replace(Regex("-\\d+$"), "").replace('-', ' '))
        val linkTitle = normalize(parts[1].replace('-', ' '))
        val wantedTitle = normalize(title)
        // another artist's song of the same name isn't worth a page load
        if (!sameWords(linkArtist, normalize(artist))) return 0
        return 40 + when {
            linkTitle == wantedTitle -> 80
            sameWords(linkTitle, wantedTitle) -> 40
            else -> 0
        }
    }

    private fun sameSong(track: JsonObject, title: String, artist: String): Boolean {
        val name = normalize(track.string("name").orEmpty())
        val wanted = normalize(title)
        val titleMatches = name == wanted || sameWords(name, wanted)
        val artists = listOf(track.string("artistName").orEmpty()) +
            (track["artists"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("name") }
        val wantedArtist = normalize(artist)
        return titleMatches && artists.any { sameWords(normalize(it), wantedArtist) }
    }

    // ---- the song page -----------------------------------------------------------------------

    // plain http first, until the bot check turns it away once; from then on the page goes straight to the hidden browser
    private suspend fun page(path: String): JsonObject? {
        val html = if (plainPagesWork == false) null
        else runCatching { getText("$Site$path", "text/html,application/xhtml+xml") }.getOrNull()
        val plainData = html?.let { NextData.find(it)?.groupValues?.get(1) }
        if (plainPagesWork == null) plainPagesWork = plainData != null
        val data = plainData
            ?: appContext?.let { MusixmatchBrowser.nextData(it, path) }
            ?: return null
        return parse(data)
    }

    private fun trackOf(nextData: JsonObject): JsonObject? =
        nextData.obj("props")?.obj("pageProps")?.obj("data")?.obj("trackInfo")?.obj("data")?.obj("track")

    private fun merge(base: MusixmatchSong, page: JsonObject, pageUrl: String): MusixmatchSong {
        val lens = page.obj("lens")
        val artists = (page["artists"] as? JsonArray).orEmpty().mapNotNull { element ->
            val artist = element as? JsonObject ?: return@mapNotNull null
            val name = artist.string("name") ?: return@mapNotNull null
            MusixmatchArtist(name = name, imageUrl = artist.string("image")?.takeIf { it.startsWith("http") })
        }
        val pageGenres = (page["artists"] as? JsonArray).orEmpty()
            .flatMap { (it as? JsonObject)?.get("genres") as? JsonArray ?: JsonArray(emptyList()) }
            .mapNotNull { (it as? JsonObject)?.string("name") }
        return base.copy(
            title = page.string("name") ?: base.title,
            artist = page.string("artistName") ?: base.artist,
            album = page.string("albumName") ?: base.album,
            releaseDate = page.string("releaseDate")?.take(10) ?: base.releaseDate,
            genres = (base.genres + pageGenres).distinct(),
            meaning = lens?.obj("meaning")?.string("explanation")?.trim()?.takeIf { it.isNotEmpty() },
            moods = (lens?.obj("moods")?.get("main_moods") as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            themes = (lens?.obj("themes")?.get("main_themes") as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.string("theme") },
            artists = artists.ifEmpty { base.artists },
            coverUrl = page.string("coverImageHD") ?: page.string("coverImage") ?: base.coverUrl,
            spotifyId = page.string("spotifyId")
                ?: (page["commontrackSpotifyIds"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: base.spotifyId,
            appleMusicId = (page["commontrackItunesIds"] as? JsonArray)
                ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: base.appleMusicId,
            isExplicit = page.bool("isExplicit") ?: base.isExplicit,
            pageUrl = pageUrl,
            pageRead = true,
        )
    }

    // ---- http --------------------------------------------------------------------------------

    /** the body of a successful answer; null for anything else, the bot check's 202 included */
    private fun getText(url: String, accept: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        return client.newCall(request).execute().use { response ->
            val challenged = response.header("x-amzn-waf-action") != null
            if (!response.isSuccessful || response.code == 202 || challenged) {
                Timber.tag(Tag).d("${response.code} for ${url.substringBefore('?')}")
                null
            } else {
                response.body.string()
            }
        }
    }

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    // "(remastered 2011)" and "- live" keep a song from matching its plain self
    private fun cleanTitle(title: String): String = title
        .replace(Regex("""\s*[(\[][^)\]]*(remaster|live|version|edit|mix|feat|ft\.|from|official|audio|video)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+.*(remaster|live|version|edit|mix).*$""", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifEmpty { title }

    // a song page's address, without its translation or other sub-pages; "/ja/lyrics/..." is the same page in japanese
    private val SongLink = Regex("""https?://www\.musixmatch\.com(?:/[a-z]{2}(?:-[a-z]{2,4})?)?(/lyrics/[^/"&\s<>?#]+/[^/"&\s<>?#]+)""")
    private val NextData = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
}
