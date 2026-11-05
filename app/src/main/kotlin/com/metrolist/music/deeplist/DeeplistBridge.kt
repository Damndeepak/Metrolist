package com.metrolist.music.deeplist

import android.content.Context
import androidx.media3.common.Player
import com.metrolist.music.constants.DeeplistSyncEnabledKey
import com.metrolist.music.constants.DeeplistTokenKey
import com.metrolist.music.constants.DeeplistWebsiteKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeeplistBridgeStatus {
    enum class State { OFF, SENDING, LIVE, ERROR }
    val state = MutableStateFlow(State.OFF)
}

internal fun startDeeplistBridge(context: Context, scope: CoroutineScope, database: MusicDatabase,
    playerProvider: () -> Player, metadataProvider: () -> MediaMetadata?) {
    scope.launch {
        context.dataStore.data.map { Triple(it[DeeplistSyncEnabledKey] ?: false,
            it[DeeplistWebsiteKey].orEmpty(), it[DeeplistTokenKey].orEmpty()) }
            .distinctUntilChanged().collectLatest { (enabled, website, token) ->
                DeeplistBridgeStatus.state.value = if (enabled) DeeplistBridgeStatus.State.SENDING else DeeplistBridgeStatus.State.OFF
                if (!enabled) return@collectLatest
                val uri = runCatching { java.net.URI(website) }.getOrNull()
                if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || !token.matches(Regex("[a-f0-9]{64}"))) {
                    DeeplistBridgeStatus.state.value = DeeplistBridgeStatus.State.ERROR
                    return@collectLatest
                }
                val endpoint = "https://${uri.rawAuthority}/api/deeplist/state"
                val client = OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
                var lastId: String? = null
                var uploadedLyrics: String? = null
                var failures = 0
                var acknowledged: String? = null
                coroutineScope {
                    val changes = Channel<Unit>(Channel.CONFLATED)
                    val player = playerProvider()
                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION,
                                    Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                                    Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                                changes.trySend(Unit)
                            }
                        }
                    }
                    player.addListener(listener)
                    try {
                        // Remote-control requests must not hold up now-playing uploads.
                        launch {
                            while (isActive) {
                                val command = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val query = acknowledged?.let { "?ack=$it" }.orEmpty()
                                        val request = Request.Builder().url(endpoint.replace("/state", "/control") + query)
                                            .header("Authorization", "Bearer $token").build()
                                        client.newCall(request).execute().use { response ->
                                            if (response.isSuccessful) JSONObject(response.body.string()).optJSONObject("command") else null
                                        }
                                    }.getOrNull()
                                }
                                if (command != null && command.optString("id") != acknowledged) {
                                    val room = (context as? com.metrolist.music.playback.MusicService)?.listenTogetherManager
                                    if (command.optString("trackId") == player.currentMediaItem?.mediaId && (room?.isInRoom != true || room.isHost)) {
                                        when (command.optString("action")) {
                                            "play" -> player.play()
                                            "pause" -> player.pause()
                                            "seek" -> player.seekTo(command.optLong("position").coerceIn(0, player.duration.coerceAtLeast(0)))
                                        }
                                    }
                                    acknowledged = command.optString("id")
                                }
                                delay(1000L)
                            }
                        }
                        while (isActive) {
                            val metadata = metadataProvider()
                            val player = playerProvider()
                            if (metadata == null || player.currentMediaItem?.mediaId != metadata.id || !metadata.id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                                withTimeoutOrNull(500L) { changes.receive() }
                                continue
                            }
                            if (lastId != metadata.id) { lastId = metadata.id; uploadedLyrics = null }
                            val position = player.currentPosition.coerceAtLeast(0L)
                            val playing = player.isPlaying
                            val duration = player.duration.takeIf { it > 0 } ?: (metadata.duration * 1000L).coerceAtLeast(0L)
                            val payload = JSONObject().put("id", metadata.id).put("title", metadata.title.take(300))
                                .put("artist", metadata.artists.joinToString { it.name }.take(500))
                                .put("position", position).put("duration", duration).put("playing", playing)
                            var sentLyrics: String? = null
                            val success = withContext(Dispatchers.IO) {
                                runCatching {
                                    val cachedLyrics = database.lyrics(metadata.id).first()?.lyrics
                                    val offset = database.song(metadata.id).first()?.song?.lyricsOffset ?: 0
                                    payload.put("offset", offset)
                                    if (cachedLyrics != null && cachedLyrics != uploadedLyrics) {
                                        val entries = (if (lyricsTextLooksSynced(cachedLyrics)) LyricsUtils.parseLyrics(cachedLyrics!!) else emptyList()).filter { !it.isBackground && it.text.isNotBlank() }.sortedBy { it.time }.take(800)
                                        val lines = JSONArray()
                                        var chars = 0
                                        val timeline = com.metrolist.music.lyrics.buildVerciWords(entries)
                                        entries.forEachIndexed { index, line ->
                                            chars += line.text.length
                                            if (chars <= 60000 && line.text.length <= 1200) {
                                                val item = JSONObject().put("time", line.time).put("text", line.text)
                                                val words = JSONArray()
                                                val next = entries.getOrNull(index + 1)?.time ?: Long.MAX_VALUE
                                                timeline.asSequence().filter { it.start >= line.time && it.start < next }.take(200).forEach { word ->
                                                    words.put(JSONObject().put("text", word.text.take(200)).put("start", word.start).put("end", word.end))
                                                }
                                                item.put("words", words); lines.put(item)
                                            }
                                        }
                                        payload.put("lines", lines); sentLyrics = cachedLyrics
                                    }
                                    val body = payload.toString()
                                    if (body.toByteArray().size > 180000) payload.put("lines", JSONArray())
                                    val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $token")
                                        .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                                    client.newCall(request).execute().use { it.isSuccessful }
                                }.getOrDefault(false)
                            }
                            if (success) { uploadedLyrics = sentLyrics ?: uploadedLyrics; failures = 0 }
                            else failures = (failures + 1).coerceAtMost(4)
                            DeeplistBridgeStatus.state.value = if (success) DeeplistBridgeStatus.State.LIVE else DeeplistBridgeStatus.State.ERROR
                            withTimeoutOrNull(if (success) 2000L else (500L shl failures).coerceAtMost(8000L)) { changes.receive() }
                        }
                    } finally {
                        player.removeListener(listener)
                        changes.close()
                    }
                }
            }
    }
}
