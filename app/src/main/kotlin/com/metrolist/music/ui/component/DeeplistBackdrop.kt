package com.metrolist.music.ui.component

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.metrolist.music.constants.*
import com.metrolist.music.lyrics.canvas.DeeplistCanvasResolver
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.sin

private val backdropClient = OkHttpClient.Builder().callTimeout(7, TimeUnit.SECONDS).build()

// A configured provider returns {"videoUrl":"https://..."}; no music account credentials leave the app.
internal suspend fun deeplistResolveVideo(provider: String, title: String, artist: String): String? = withContext(Dispatchers.IO) {
    if (!provider.startsWith("https://") || provider.length > 2000) return@withContext null
    runCatching {
        val url = provider + (if ('?' in provider) "&" else "?") + "title=" + URLEncoder.encode(title, "UTF-8") + "&artist=" + URLEncoder.encode(artist, "UTF-8")
        backdropClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.byteStream()?.use { com.metrolist.music.deeplist.deeplistReadLimited(it, 32_000) } ?: return@use null
            JSONObject(String(bytes)).optString("videoUrl").takeIf { it.startsWith("https://") }
        }
    }.getOrNull()
}

internal suspend fun deeplistCanvasVideo(spotifyId: String): String? = withContext(Dispatchers.IO) {
    if (!spotifyId.matches(Regex("[A-Za-z0-9]{22}"))) return@withContext null
    runCatching {
        backdropClient.newCall(Request.Builder().url("https://spotify-canva.vercel.app/api/canvas?trackId=$spotifyId").build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.byteStream()?.use { com.metrolist.music.deeplist.deeplistReadLimited(it, 64_000) } ?: return@use null
            val root = JSONObject(String(bytes))
            val list = root.optJSONArray("canvasesList") ?: root.optJSONObject("data")?.optJSONArray("canvasesList")
            list?.optJSONObject(0)?.optString("canvasUrl")?.takeIf { it.startsWith("https://canvaz.scdn.co/") }
        }
    }.getOrNull()
}

@Composable
internal fun DeeplistBackdrop(track: String?, art: String?, title: String, artist: String, playing: Boolean, appleStyle: Boolean = false) {
    val appleAmbient by rememberPreference(DeeplistAppleAmbientKey, true)
    val enabled by rememberPreference(DeeplistArtworkBackgroundKey, true)
    val mode by rememberPreference(DeeplistBackdropModeKey, 2)
    val dim by rememberPreference(DeeplistBackdropDimKey, .48f)
    val blur by rememberPreference(DeeplistBackdropBlurKey, .55f)
    val reduced by rememberPreference(DeeplistPerformanceKey, false)
    val overrides by rememberPreference(DeeplistVideoOverridesKey, "{}")
    val songModes by rememberPreference(DeeplistSongBackdropModesKey, "{}")
    val songMode = remember(track, songModes, overrides) { deeplistSongBackdropMode(songModes, overrides, track.orEmpty()) }
    val ambient = appleStyle && appleAmbient && mode == 2 && songMode == "auto"
    val spotifyTracks by rememberPreference(DeeplistSpotifyTracksKey, "{}")
    val spotifyId = remember(track, spotifyTracks) { runCatching { JSONObject(spotifyTracks).optString(track.orEmpty()) }.getOrDefault("") }
    val provider by rememberPreference(DeeplistVideoProviderKey, "")
    val clock = rememberDeeplistClock(playing && !reduced && mode != 0)
    var resolved by remember(track, overrides, provider, spotifyId, songMode, mode, enabled, reduced, title, artist) { mutableStateOf<String?>(null) }
    var fallback by remember(track, overrides, provider, spotifyId, songMode, mode, enabled, reduced, title, artist) { mutableStateOf<String?>(null) }
    var failed by remember(track, resolved) { mutableStateOf(false) }
    LaunchedEffect(track, title, artist, overrides, provider, mode, enabled, spotifyId, songMode, reduced, ambient) {
        resolved = null
        fallback = null
        if (enabled && mode == 2 && !reduced && songMode != "cover" && !ambient) {
            if (songMode == "custom") {
                resolved = deeplistSongVideo(overrides, track.orEmpty())
            } else {
                resolved = (if (provider.isNotBlank()) deeplistResolveVideo(provider, title, artist) else null)
                    ?: deeplistCanvasVideo(spotifyId)
                if (resolved == null) {
                    val artwork = DeeplistCanvasResolver.resolve(title, artist)
                    resolved = artwork?.tallUrl ?: artwork?.url
                    fallback = listOfNotNull(artwork?.url, artwork?.fallbackUrl).firstOrNull { it != resolved }
                }
            }
        }
    }
    if (!enabled || mode == 0) return
    if (ambient) {
        DeeplistAppleAmbient(playing && !reduced, blur, dim)
        return
    }
    val layer = Modifier.fillMaxSize().graphicsLayer {
        val phase = clock.longValue / 18000f
        scaleX = 1.18f + sin(phase * .6f) * .035f; scaleY = scaleX
        translationX = sin(phase) * 18.dp.toPx(); translationY = sin(phase * .73f) * 14.dp.toPx()
    }.blur((blur.coerceIn(0f, 1f) * 35f).dp)
    Box(Modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = layer)
        if (mode == 2 && resolved != null && !failed && !reduced) {
            key(resolved) {
                DeeplistVideo(resolved!!, playing, layer) {
                    if (fallback != null) { resolved = fallback; fallback = null } else failed = true
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(.15f, .85f))))
    }
}

@Composable
private fun DeeplistVideo(url: String, playing: Boolean, modifier: Modifier, onFailure: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember(url) { ExoPlayer.Builder(context).build().apply {
        volume = 0f; repeatMode = Player.REPEAT_MODE_ONE
        setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
        setMediaItem(MediaItem.fromUri(Uri.parse(url))); prepare()
    } }
    val cropMatrix = remember(player) { android.graphics.Matrix() }
    val latestFailure by rememberUpdatedState(onFailure)
    var firstFrame by remember(url) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { latestFailure() }
            override fun onRenderedFirstFrame() { firstFrame = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, playing, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try { player.playWhenReady = playing; kotlinx.coroutines.awaitCancellation() } finally { player.playWhenReady = false }
        }
    }
    AndroidView(factory = { TextureView(it).apply { isOpaque = false; alpha = 0f; player.setVideoTextureView(this) } }, modifier = modifier,
        update = { texture ->
            texture.alpha = if (firstFrame) 1f else 0f
            val video = player.videoSize
            if (texture.width > 0 && texture.height > 0 && video.width > 0 && video.height > 0) {
                val viewRatio = texture.width.toFloat() / texture.height
                val ratio = video.width.toFloat() / video.height
                texture.setTransform(cropMatrix.apply {
                    setScale(if (ratio > viewRatio) ratio / viewRatio else 1f, if (ratio < viewRatio) viewRatio / ratio else 1f, texture.width / 2f, texture.height / 2f)
                })
            }
        })
}
