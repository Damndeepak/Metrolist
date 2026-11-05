package com.music.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest
import java.util.Locale

/** a song as a scrobbling service wants it, times are in seconds */
@Serializable
data class ScrobbleTrack(
    val artist: String,
    val track: String,
    val album: String? = null,
    val durationSeconds: Int? = null,
)

/** a signed-in account: the name it goes by and the key every scrobble is sent with */
data class AudioscrobblerSession(val name: String, val key: String)

/** what the service said went wrong, by its own error codes (see the last.fm api docs) */
class AudioscrobblerException(val code: Int, message: String) : Exception(message) {
    /** the account took back mochy's access or the session is gone: sign in again */
    val isSignedOut: Boolean get() = code == INVALID_SESSION

    /** turned down for good, like a song with no title, sending it again won't help */
    val isRejected: Boolean get() = code == INVALID_PARAMETERS || code == INVALID_RESOURCE

    /** worth trying the same request again later */
    val isTemporary: Boolean get() = code in TemporaryCodes

    /** signing in: the username or password is wrong, both services answer this with the same code */
    val isWrongLogin: Boolean get() = code == AUTH_FAILED

    companion object {
        /** not one of the service's codes: the server itself failed or answered with no json */
        const val SERVER_ERROR = -1
        private const val AUTH_FAILED = 4
        private const val INVALID_PARAMETERS = 6
        private const val INVALID_RESOURCE = 7
        private const val INVALID_SESSION = 9
        private val TemporaryCodes = setOf(SERVER_ERROR, 8, 11, 16, 29)
    }
}

/** one audioscrobbler 2.0 server (last.fm or a copy of its api like libre.fm), calls are signed with md5, network failures are IOExceptions and the service's own errors AudioscrobblerException */
class Audioscrobbler(
    private val apiRoot: String,
    private val apiKey: String,
    private val secret: String,
    /** libre.fm only takes the old md5 login token on sign-in, it turns a plain password away */
    private val signsInWithToken: Boolean = false,
) {
    /** false in a build made without this service's api key, which can't sign anyone in */
    val isConfigured: Boolean get() = apiKey.isNotEmpty() && secret.isNotEmpty()

    /** signs in straight from mochy's own form, the password only goes to the service over https and isn't kept */
    suspend fun getMobileSession(username: String, password: String): AudioscrobblerSession {
        val login = if (signsInWithToken) {
            mapOf("authToken" to md5(username.lowercase(Locale.ROOT) + md5(password)))
        } else {
            mapOf("password" to password)
        }
        val session = call("auth.getMobileSession", mapOf("username" to username) + login)["session"] as? JsonObject
            ?: throw unreadable()
        return AudioscrobblerSession(
            name = session.string("name") ?: throw unreadable(),
            key = session.string("key") ?: throw unreadable(),
        )
    }

    suspend fun updateNowPlaying(sessionKey: String, track: ScrobbleTrack) {
        call("track.updateNowPlaying", track.params(index = null), sessionKey)
    }

    /** up to MAX_BATCH plays in one go, each with the time it started playing */
    suspend fun scrobble(sessionKey: String, plays: List<Pair<ScrobbleTrack, Long>>) {
        require(plays.size <= MAX_BATCH) { "At most $MAX_BATCH scrobbles per request" }
        val params = buildMap {
            plays.forEachIndexed { index, (track, startedAt) ->
                putAll(track.params(index))
                put("timestamp[$index]", startedAt.toString())
            }
        }
        call("track.scrobble", params, sessionKey)
    }

    suspend fun love(sessionKey: String, artist: String, track: String, love: Boolean) {
        call(if (love) "track.love" else "track.unlove", mapOf("artist" to artist, "track" to track), sessionKey)
    }

    private suspend fun call(method: String, params: Map<String, String> = emptyMap(), sessionKey: String? = null): JsonObject {
        val signed = buildMap {
            put("method", method)
            put("api_key", apiKey)
            sessionKey?.let { put("sk", it) }
            putAll(params)
        }
        val response = client.post(apiRoot) {
            userAgent(USER_AGENT)
            setBody(
                FormDataContent(
                    Parameters.build {
                        signed.forEach { (name, value) -> append(name, value) }
                        append("api_sig", signature(signed))
                        // left out of the signature, as the API asks
                        append("format", "json")
                    },
                ),
            )
        }
        val body = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        (json?.get("error") as? JsonPrimitive)?.let { error ->
            val code = error.contentOrNull?.toIntOrNull() ?: AudioscrobblerException.SERVER_ERROR
            throw AudioscrobblerException(code, json.string("message") ?: "Error $code")
        }
        if (!response.status.isSuccess() || json == null) {
            throw AudioscrobblerException(AudioscrobblerException.SERVER_ERROR, "The server answered ${response.status.value}")
        }
        return json
    }

    private fun signature(params: Map<String, String>): String =
        md5(params.toSortedMap().entries.joinToString("") { it.key + it.value } + secret)

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun ScrobbleTrack.params(index: Int?): Map<String, String> {
        val suffix = index?.let { "[$it]" }.orEmpty()
        return buildMap {
            put("artist$suffix", artist)
            put("track$suffix", track)
            album?.let { put("album$suffix", it) }
            durationSeconds?.let { put("duration$suffix", it.toString()) }
        }
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun unreadable() = AudioscrobblerException(AudioscrobblerException.SERVER_ERROR, "The answer couldn't be read")

    companion object {
        /** the most plays one track.scrobble request takes */
        const val MAX_BATCH = 50

        private const val USER_AGENT = "Mochy"

        private val client by lazy {
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }
                expectSuccess = false
            }
        }
    }
}
