package com.music.wikipedia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** a song as wikidata and wikipedia know it; whatever they didn't say stays null or empty */
data class WikiSong(
    /** the article's opening, in [language]; null when the song has facts but no article */
    val about: String?,
    val language: String?,
    val articleUrl: String?,
    val genres: List<String>,
    /** "2014-03-17", or just the year when that's all that's known */
    val releaseDate: String?,
    val writers: List<String>,
    val producers: List<String>,
    val label: String?,
)

// wikidata finds the song and its facts, wikipedia gives its article; both are free and open, no key and no bot check
object Wikipedia {
    private const val Wikidata = "https://www.wikidata.org/w/api.php"
    // wikimedia asks every app to say who it is
    private const val UserAgent = "Mochy (https://github.com/realidkroo/Mochy-Music)"
    private const val Tag = "Wikipedia"

    // song, single, musical work, composition: what a song's item is an instance of
    private val SongKinds = setOf("Q7366", "Q134556", "Q2188189", "Q105543609", "Q55850593")

    private sealed interface Lookup {
        data class Found(val song: WikiSong) : Lookup
        data object Missing : Lookup
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }
    private val songs = ConcurrentHashMap<String, Lookup>()

    /** the song's facts and article, or null when it has no wikidata item (or couldn't be asked) */
    suspend fun song(title: String, artist: String, language: String = Locale.getDefault().language): WikiSong? = withContext(Dispatchers.IO) {
        val key = "${title.lowercase()}|${artist.lowercase()}|$language"
        songs[key]?.let { return@withContext (it as? Lookup.Found)?.song }
        val lookup = runCatching { lookUp(title, artist, language) }
            .onFailure { Timber.tag(Tag).w(it, "couldn't read $title by $artist") }
            .getOrNull()
        if (lookup != null) songs[key] = lookup
        (lookup as? Lookup.Found)?.song
    }

    private fun lookUp(title: String, artist: String, language: String): Lookup? {
        val leadArtist = artist.substringBefore(",").substringBefore("&").trim()
        val languages = languagesFor(title, language)
        val id = findItem(title, leadArtist, languages) ?: return Lookup.Missing
        val entity = get("$Wikidata?action=wbgetentities&ids=$id&props=claims|sitelinks&format=json")
            ?.obj("entities")?.obj(id) ?: return null
        val claims = entity.obj("claims") ?: JsonObject(emptyMap())

        val genreIds = ids(claims, "P136")
        val writerIds = (ids(claims, "P86") + ids(claims, "P676")).distinct()
        val producerIds = ids(claims, "P162")
        val labelIds = ids(claims, "P264")
        val labels = labelsOf((genreIds + writerIds + producerIds + labelIds).distinct(), languages)

        val (article, articleLanguage) = articleFor(entity.obj("sitelinks"), languages)
        val about = article?.let { extract(it, articleLanguage!!) }
        return Lookup.Found(
            WikiSong(
                about = about,
                language = articleLanguage.takeIf { about != null },
                articleUrl = article?.let { "https://$articleLanguage.wikipedia.org/wiki/${URLEncoder.encode(it.replace(' ', '_'), "UTF-8")}" },
                genres = genreIds.mapNotNull(labels::get),
                releaseDate = releaseOf(claims),
                writers = writerIds.mapNotNull(labels::get),
                producers = producerIds.mapNotNull(labels::get),
                label = labelIds.firstNotNullOfOrNull(labels::get),
            ),
        )
    }

    // the phone's language first, english, then japanese, korean or chinese when the title is written in one
    private fun languagesFor(title: String, language: String): List<String> {
        val script = when {
            title.any { it in '぀'..'ヿ' } -> "ja"
            title.any { it in '가'..'힯' } -> "ko"
            title.any { it in '一'..'鿿' } -> "zh"
            else -> null
        }
        return listOfNotNull(language.lowercase(), "en", script).distinct()
    }

    // an item whose description names the artist ("2014 single by sia"), or a song-like one whose performer is them
    private fun findItem(title: String, artist: String, languages: List<String>): String? {
        val wantedArtist = normalize(artist)
        val candidates = languages.flatMap { language ->
            val url = "$Wikidata?action=wbsearchentities&type=item&limit=10&format=json" +
                "&language=$language&uselang=$language&search=${URLEncoder.encode(title, "UTF-8")}"
            (get(url)?.get("search") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        }.distinctBy { it.string("id") }
        if (wantedArtist.isEmpty()) return null
        candidates.firstOrNull { hasWords(normalize(it.string("description").orEmpty()), wantedArtist) }
            ?.string("id")?.let { return it }

        val unsure = candidates.mapNotNull { it.string("id") }.take(8)
        if (unsure.isEmpty()) return null
        val entities = get("$Wikidata?action=wbgetentities&ids=${unsure.joinToString("|")}&props=claims&format=json")
            ?.obj("entities") ?: return null
        val songLike = unsure.mapNotNull { id -> entities.obj(id)?.obj("claims")?.let { id to it } }
            .filter { (_, claims) -> ids(claims, "P31").any { it in SongKinds } }
        if (songLike.isEmpty()) return null
        val performers = labelsOf(songLike.flatMap { ids(it.second, "P175") }.distinct(), languages + "en")
        return songLike.firstOrNull { (_, claims) ->
            ids(claims, "P175").any { performer -> hasWords(normalize(performers[performer].orEmpty()), wantedArtist) }
        }?.first
    }

    private fun articleFor(sitelinks: JsonObject?, languages: List<String>): Pair<String?, String?> {
        if (sitelinks == null) return null to null
        for (language in languages) {
            sitelinks.obj("${language}wiki")?.string("title")?.let { return it to language }
        }
        return null to null
    }

    // the article's opening paragraphs as plain text
    private fun extract(title: String, language: String): String? {
        val url = "https://$language.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1" +
            "&redirects=1&format=json&titles=${URLEncoder.encode(title, "UTF-8")}"
        val pages = get(url)?.obj("query")?.obj("pages") ?: return null
        return pages.values.firstNotNullOfOrNull { (it as? JsonObject)?.string("extract") }
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun labelsOf(ids: List<String>, languages: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val entities = ids.chunked(50).mapNotNull { chunk ->
            get("$Wikidata?action=wbgetentities&props=labels&format=json&languages=${languages.joinToString("|")}&ids=${chunk.joinToString("|")}")
                ?.obj("entities")
        }
        return entities.flatMap { it.entries }.mapNotNull { (id, entity) ->
            val labels = (entity as? JsonObject)?.obj("labels") ?: return@mapNotNull null
            val label = languages.firstNotNullOfOrNull { labels.obj(it)?.string("value") } ?: return@mapNotNull null
            id to label
        }.toMap()
    }

    // "+2014-03-17t00:00:00z" at day precision, or only the year when wikidata only knows the year
    private fun releaseOf(claims: JsonObject): String? {
        val value = values(claims, "P577").mapNotNull { it as? JsonObject }.firstOrNull() ?: return null
        val time = value.string("time")?.removePrefix("+") ?: return null
        val precision = (value["precision"] as? JsonPrimitive)?.intOrNull ?: 9
        return when {
            precision >= 11 -> time.take(10)
            precision == 10 -> time.take(7)
            else -> time.take(4)
        }
    }

    private fun values(claims: JsonObject, property: String): List<JsonElement> =
        (claims[property] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject)?.obj("mainsnak")?.obj("datavalue")?.get("value")
        }

    private fun ids(claims: JsonObject, property: String): List<String> =
        values(claims, property).mapNotNull { (it as? JsonObject)?.string("id") }

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

    // whole words only, so "ado" isn't found inside "adonis"
    private fun hasWords(text: String, words: String): Boolean = words.isNotEmpty() && " $text ".contains(" $words ")

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
