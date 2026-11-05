package com.mochyy.mochymusic.module.applemusic

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.LicenseServer
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SignInSpec
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamDrm
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.TrackMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays the user's own Apple Music subscription inside Mochy.
 *
 * Signing in happens on music.apple.com itself; the module keeps the web player's developer token
 * and the account's music-user token. A song is found in the catalog by title, artist and length,
 * its web-playback stream is opened, and the device's Widevine CDM decrypts it inside the
 * platform, licensed by Apple's web license server exactly as the web player in Chrome is.
 * Mochy's own player does the playing, so every effect applies.
 *
 * What it can't do: lossless, Atmos and Apple's Spatial mix (only in streams encoded for FairPlay,
 * Apple's own DRM) and downloads (no offline licenses; the audio is never decrypted outside the
 * CDM).
 */
class AppleMusicModule : SourceModule {

    override val info = ModuleInfo(
        id = Id,
        name = "Apple Music",
        version = "1.0",
        description = "Plays your Apple Music subscription in Mochy, in AAC 256, with every Audio Lab effect",
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }
    private val matches = ConcurrentHashMap<String, CatalogTrack>()
    private val misses = ConcurrentHashMap<String, Long>()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override val signIn = SignInSpec(
        startUrl = "https://music.apple.com/us/library/playlists",
        origins = listOf("https://music.apple.com", "https://idmsa.apple.com", "https://authorize.music.apple.com", "https://www.apple.com"),
        sessionOrigins = setOf("https://music.apple.com"),
        desktopSite = false,
        title = "Sign in to Apple Music",
        subtitle = "Your subscription plays in Mochy",
    )

    override val choices = listOf(
        ModuleChoice(
            id = ChoiceQuality,
            label = "Stream",
            options = listOf(
                StreamOption(OptionAac256, "AAC 256", "Apple Music's standard stereo stream"),
                StreamOption(OptionHe64, "HE-AAC 64", "A quarter of the data, for slow or metered networks"),
            ),
        ),
        ModuleChoice(
            id = ChoiceRegion,
            label = "Region",
            options = listOf(StreamOption(RegionAuto, "Your account's", "Searches the catalog of your Apple account's country")) +
                Regions.map { (code, name) -> StreamOption(code, name, "Searches the $name catalog. Songs your account's country doesn't license may still be refused") },
        ),
    )

    // ---- account ------------------------------------------------------------------------------

    private var userToken: String?
        get() = host.storage.get("user_token")
        set(value) = host.storage.set("user_token", value)

    private var capturedDeveloperToken: String?
        get() = host.storage.get("developer_token")
        set(value) = host.storage.set("developer_token", value)

    private var storefront: String?
        get() = host.storage.get("storefront")
        set(value) = host.storage.set("storefront", value)

    override fun accountState(): AccountState = when {
        userToken == null -> AccountState.SignedOut
        host.storage.get("expired") != null -> AccountState.Expired(host.storage.get("expired")!!)
        else -> AccountState.SignedIn(host.storage.get("account_label") ?: "Signed in")
    }

    override fun onSignInHeader(name: String, value: String, url: String): Boolean {
        val trimmed = value.trim()
        when (name.trim().lowercase()) {
            "authorization" -> {
                val token = trimmed.removePrefix("Bearer ").removePrefix("bearer ").trim()
                if (token.startsWith("eyJ") && jwtExpiryMs(token) > System.currentTimeMillis()) capturedDeveloperToken = token
            }
            "media-user-token" -> if (trimmed.length >= 20) {
                userToken = trimmed
                host.storage.set("expired", null)
            }
        }
        return userToken != null && capturedDeveloperToken != null
    }

    override suspend fun verifyAccount(): AccountState = verifyAccount(retried = false)

    private fun verifyAccount(retried: Boolean): AccountState {
        if (userToken == null) return AccountState.SignedOut
        val response = api("/v1/me/storefront")
        if (response.code == 401 || response.code == 403) {
            // the developer token may just have rotated; try the current one, once, before giving up
            if (!retried && refreshDeveloperToken()) return verifyAccount(retried = true)
            host.storage.set("expired", "Apple signed this session out")
            return AccountState.Expired("Apple signed this session out")
        }
        if (!response.ok) return AccountState.Expired("Apple Music didn't answer (${response.code})")
        val front = parse(response).array("data")?.firstOrNull()?.jsonObject
        val id = front?.string("id")
        val country = front?.get("attributes")?.jsonObject?.string("name")
        if (id != null) storefront = id
        val label = "Signed in${country?.let { " · $it" }.orEmpty()}"
        host.storage.set("account_label", label)
        host.storage.set("expired", null)
        return AccountState.SignedIn(label)
    }

    override fun signOut() {
        listOf("user_token", "developer_token", "storefront", "account_label", "expired").forEach { host.storage.set(it, null) }
        matches.clear()
        misses.clear()
    }

    // ---- finding and streaming a song ---------------------------------------------------------

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        val token = userToken ?: return ResolveResult.Unavailable("Sign in to Apple Music first").also {
            report(DiagnosticStep("Account", false, "Not signed in"))
        }
        if (storefront == null) {
            report(DiagnosticStep("Account", null, "Checking your account…"))
            val state = verifyAccount()
            if (state !is AccountState.SignedIn) {
                report(DiagnosticStep("Account", false, (state as? AccountState.Expired)?.reason ?: "Signed out"))
                return ResolveResult.Unavailable("Apple Music account isn't signed in")
            }
        }
        report(DiagnosticStep("Account", true, host.storage.get("account_label") ?: "Signed in"))

        val region = picks[ChoiceRegion]?.takeIf { it != RegionAuto } ?: storefront ?: "us"
        val quality = picks[ChoiceQuality] ?: OptionAac256
        val key = "$region|${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on Apple Music"))
            return ResolveResult.NotFound("Not on Apple Music")
        }
        report(DiagnosticStep("Find the song", null, "Searching Apple Music ($region)…"))
        val ranked = matches[key]?.let { listOf(TrackMatcher.Scored(it, 1f)) } ?: match(track, region)
        if (ranked.isEmpty()) {
            misses[key] = System.currentTimeMillis()
            report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}” in $region"))
            return ResolveResult.NotFound("No matching recording on Apple Music")
        }
        val best = ranked.first()
        report(DiagnosticStep("Find the song", true, "${best.track.title} · ${best.track.artist}" +
            if (ranked.size > 1) " (${ranked.size} copies)" else ""))

        // Apple's web-playback stream, the one its web player uses in Chrome. (The enhanced HLS
        // streams list Widevine keys too and even license, but their audio is labelled with an
        // all-zero key id that only FairPlay ignores, so Widevine can't decrypt a sample of it.)
        // The same recording's copies are tried in turn, best match first.
        report(DiagnosticStep("Stream", null, "Opening the stream…"))
        val nearEqual = ranked.filter { it.score >= best.score - 0.06f }.take(4)
        var lastProblem = "No stream for this song in $region"
        for (candidate in nearEqual) {
            when (val result = webPlaybackStream(candidate.track, quality, token, track)) {
                is StreamAttempt.Ok -> {
                    matches[key] = candidate.track
                    report(DiagnosticStep("Stream", true, result.detail))
                    return ResolveResult.Found(result.stream)
                }
                is StreamAttempt.Problem -> lastProblem = result.reason
            }
        }
        report(DiagnosticStep("Stream", false, lastProblem))
        return ResolveResult.Unavailable(lastProblem)
    }

    private sealed interface StreamAttempt {
        class Ok(val stream: SourceStream, val detail: String) : StreamAttempt
        class Problem(val reason: String) : StreamAttempt
    }

    /**
     * Apple's web-playback stream: AAC 256 (or HE-AAC 64) for every song, behind one Widevine
     * key whose real id the playlist names.
     */
    private fun webPlaybackStream(song: CatalogTrack, quality: String, token: String, track: TrackQuery): StreamAttempt {
        val body = buildJsonObject {
            put("salableAdamId", song.id)
            put("language", "en-US")
        }.toString().toByteArray()
        val response = host.http(
            HttpRequest(
                WebPlaybackUrl,
                method = "POST",
                headers = webHeaders + mapOf("Authorization" to "Bearer ${developerToken()}", "Media-User-Token" to token),
                body = body,
                contentType = "application/json",
            )
        )
        if (!response.ok) return StreamAttempt.Problem("Web playback answered ${response.code}")
        val answer = parse(response)
        val entry = answer.array("songList")?.firstOrNull()?.jsonObject
            ?: return StreamAttempt.Problem(answer.string("customerMessage") ?: "Web playback has no stream for this song")
        val assets = entry.array("assets").orEmpty().map { it.jsonObject }
        val wantedFlavor = if (quality == OptionHe64) "ctrp64" else "ctrp256"
        val asset = assets.firstOrNull { it.string("flavor")?.endsWith(wantedFlavor) == true }
            ?: assets.firstOrNull { it.string("flavor")?.contains("ctrp") == true }
            ?: return StreamAttempt.Problem("Web playback offers only ${assets.mapNotNull { it.string("flavor") }.joinToString()}")
        val url = asset.string("URL") ?: return StreamAttempt.Problem("Web playback stream has no address")
        val media = host.http(HttpRequest(url, headers = webHeaders))
        if (!media.ok) return StreamAttempt.Problem("Web playback playlist answered ${media.code}")
        val (keyUri, keyId) = AppleHls.legacyKey(media.text)
            ?: return StreamAttempt.Problem("Web playback stream has no Widevine key")
        val pssh = AppleHls.psshFor(keyId)
        val playlist = AppleHls.rewriteLegacy(media.text, url, keyUri, pssh)
        val key = AppleHls.WidevineKey(keyUri, pssh, keyId)
        val songId = song.id
        val flavor = asset.string("flavor").orEmpty()
        val label = when {
            flavor.endsWith("ctrp256") -> "AAC 256"
            flavor.endsWith("ctrp64") -> "HE-AAC 64"
            else -> "AAC"
        }
        return StreamAttempt.Ok(
            stream = SourceStream(
                url = "data:application/vnd.apple.mpegurl;base64," + Base64.getEncoder().encodeToString(playlist.toByteArray()),
                format = StreamFormat.Hls,
                drm = StreamDrm.Widevine(LicenseServer { acquireLicense(it, listOf(key), songId, token) }, listOf(pssh)),
                sourceLabel = "Apple Music",
                qualityLabel = label,
                matched = matched(song, track),
                // "32:ctrp64" is HE-AAC, which its container reads as plain AAC
                codec = if (flavor.endsWith("ctrp64")) "HE-AAC" else "AAC",
            ),
            detail = "$label · ${AppleHls.segmentCount(playlist)} segments",
        )
    }

    private fun matched(song: CatalogTrack, track: TrackQuery) = MatchedTrack(
        id = song.id,
        title = song.title,
        artist = song.artist,
        album = song.album,
        durationMs = song.durationMs,
        confidence = TrackMatcher.score(track, song),
    )

    private fun match(track: TrackQuery, region: String): List<TrackMatcher.Scored> {
        val isrc = track.isrc
        if (isrc != null) {
            val byIsrc = api("/v1/catalog/$region/songs?filter[isrc]=${enc(isrc)}")
            if (byIsrc.ok) {
                val songs = songsIn(parse(byIsrc).array("data"))
                if (songs.isNotEmpty()) return songs.map { TrackMatcher.Scored(it, 1f) }
            }
        }
        val term = "${TrackMatcher.core(track.title)} ${track.artists.firstOrNull().orEmpty()}".trim()
        val search = api("/v1/catalog/$region/search?term=${enc(term)}&types=songs&limit=15")
        if (!search.ok) throw IOException("Apple Music search answered ${search.code}")
        val songs = songsIn(parse(search).jsonObject["results"]?.jsonObject?.get("songs")?.jsonObject?.get("data") as? JsonArray)
        return TrackMatcher.ranked(track, songs)
    }

    private fun songsIn(data: JsonArray?): List<CatalogTrack> = data.orEmpty().mapNotNull { element ->
        val obj = element.jsonObject
        val a = obj["attributes"]?.jsonObject ?: return@mapNotNull null
        CatalogTrack(
            id = obj.string("id") ?: return@mapNotNull null,
            title = a.string("name") ?: return@mapNotNull null,
            artist = a.string("artistName").orEmpty(),
            album = a.string("albumName"),
            durationMs = (a["durationInMillis"] as? JsonPrimitive)?.longOrNull,
            isrc = a.string("isrc"),
        )
    }

    /** trades the CDM's challenge for a license, for whichever of the song's keys it's asking about */
    private fun acquireLicense(challenge: ByteArray, keys: List<AppleHls.WidevineKey>, songId: String, token: String): ByteArray {
        val key = keys.firstOrNull { challenge.indexOf(it.keyId) >= 0 } ?: keys.last()
        val body = buildJsonObject {
            put("challenge", Base64.getEncoder().encodeToString(challenge))
            put("key-system", "com.widevine.alpha")
            put("uri", key.uri)
            put("adamId", songId)
            put("isLibrary", false)
            put("user-initiated", true)
        }.toString().toByteArray()
        val response = host.http(
            HttpRequest(
                LicenseUrl,
                method = "POST",
                headers = webHeaders + mapOf(
                    "Authorization" to "Bearer ${developerToken()}",
                    "Media-User-Token" to token,
                ),
                body = body,
                contentType = "application/json",
            )
        )
        if (!response.ok) throw IOException("Apple's license server answered ${response.code}")
        val answer = parse(response)
        val status = (answer["status"] as? JsonPrimitive)?.intOrNull
        val license = answer.string("license")
        if (status != 0 || license == null) {
            val message = answer.string("customerMessage") ?: "status $status"
            if (status == -1002) host.storage.set("expired", message)
            throw IOException("Apple refused the license: $message")
        }
        return Base64.getDecoder().decode(license)
    }

    // ---- HTTP ----------------------------------------------------------------------------------

    private fun api(path: String): HttpResponse = host.http(
        HttpRequest(
            "https://amp-api.music.apple.com$path",
            headers = webHeaders + buildMap {
                put("Authorization", "Bearer ${developerToken()}")
                userToken?.let { put("Media-User-Token", it) }
            },
        )
    )

    /** the web player's developer token: the one seen at sign-in while it lasts, then the page's current one */
    private fun developerToken(): String {
        capturedDeveloperToken?.takeIf { jwtExpiryMs(it) > System.currentTimeMillis() + 60_000 }?.let { return it }
        refreshDeveloperToken()
        return capturedDeveloperToken ?: ""
    }

    /** reads the token the web player ships in its main script */
    private fun refreshDeveloperToken(): Boolean {
        val page = runCatching { host.http(HttpRequest("https://music.apple.com/us/browse", headers = webHeaders)) }.getOrNull()
            ?.takeIf { it.ok } ?: return false
        val scripts = Regex("""/assets/index(?:-legacy)?[~-][a-zA-Z0-9_-]+\.js""").findAll(page.text).map { it.value }.distinct()
        for (path in scripts) {
            val script = runCatching { host.http(HttpRequest("https://music.apple.com$path", headers = webHeaders)) }.getOrNull()
                ?.takeIf { it.ok } ?: continue
            val token = Regex("""eyJ[a-zA-Z0-9_-]+\.eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+""").findAll(script.text)
                .map { it.value }
                .filter { jwtExpiryMs(it) > System.currentTimeMillis() }
                .sortedByDescending { jwtPayload(it)?.contains("AMPWebPlay") == true }
                .firstOrNull()
            if (token != null) {
                capturedDeveloperToken = token
                return true
            }
        }
        return false
    }

    private fun parse(response: HttpResponse): JsonObject =
        runCatching { json.parseToJsonElement(response.text).jsonObject }.getOrElse { JsonObject(emptyMap()) }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
    /** the object, or an empty one: Apple leaves fields out freely */
    private val JsonElement.jsonObject: JsonObject get() = this as? JsonObject ?: JsonObject(emptyMap())

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    companion object {
        const val Id = "applemusic"
        const val ChoiceQuality = "quality"
        const val ChoiceRegion = "region"
        const val RegionAuto = "auto"
        const val OptionAac256 = "aac256"
        const val OptionHe64 = "he64"

        private const val LicenseUrl = "https://play.itunes.apple.com/WebObjects/MZPlay.woa/wa/acquireWebPlaybackLicense"
        private const val WebPlaybackUrl = "https://play.itunes.apple.com/WebObjects/MZPlay.woa/wa/webPlayback"

        private val Regions = listOf(
            "us" to "United States", "gb" to "United Kingdom", "jp" to "Japan", "kr" to "South Korea",
            "id" to "Indonesia", "sg" to "Singapore", "my" to "Malaysia", "ph" to "Philippines", "th" to "Thailand",
            "in" to "India", "au" to "Australia", "ca" to "Canada", "de" to "Germany", "fr" to "France",
            "br" to "Brazil", "mx" to "Mexico",
        )
        private const val MissMemoryMs = 30L * 60 * 1000

        /** Apple turns non-browser clients away; the web player's own headers */
        private val webHeaders = mapOf(
            "Origin" to "https://music.apple.com",
            "Referer" to "https://music.apple.com/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        )

        internal fun jwtPayload(token: String): String? = runCatching {
            String(Base64.getUrlDecoder().decode(token.split(".")[1]), Charsets.UTF_8)
        }.getOrNull()

        internal fun jwtExpiryMs(token: String): Long =
            jwtPayload(token)?.let { Regex(""""exp":(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }?.times(1000) ?: 0L
    }
}
