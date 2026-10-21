package com.metrolist.music.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch
import org.json.JSONObject

internal fun deeplistSongVideo(raw: String, track: String): String? = runCatching {
    JSONObject(raw).optString(track).takeIf { it.startsWith("content://") || com.metrolist.music.lyrics.canvas.validCanvasUrl(it) }
}.getOrNull()

internal fun deeplistSongBackdropMode(raw: String, videos: String, track: String): String =
    runCatching { JSONObject(raw).optString(track).takeIf { it in listOf("auto", "cover", "custom") } }.getOrNull()
        ?: if (deeplistSongVideo(videos, track) != null) "custom" else "auto"

@Composable
internal fun DeeplistSongBackground() {
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val track = metadata?.id.orEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val raw by rememberPreference(DeeplistSongBackdropModesKey, "{}")
    val videos by rememberPreference(DeeplistVideoOverridesKey, "{}")
    val mode = deeplistSongBackdropMode(raw, videos, track)
    var failed by remember(track) { mutableStateOf(false) }
    fun changeMode(value: String) { scope.launch { context.dataStore.edit { prefs ->
        val modes = runCatching { JSONObject(prefs[DeeplistSongBackdropModesKey] ?: "{}") }.getOrDefault(JSONObject())
        modes.put(track, value); prefs[DeeplistSongBackdropModesKey] = modes.toString()
        prefs[DeeplistArtworkBackgroundKey] = true; prefs[DeeplistBackdropModeKey] = 2
    } } }
    // Bind the result to the song that launched the picker even if playback changes while it is open.
    var pickingTrack by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pickingTrack.isNotBlank()) {
            val destination = pickingTrack
            val permitted = runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
            failed = !permitted
            if (permitted) scope.launch { context.dataStore.edit { prefs ->
                val values = runCatching { JSONObject(prefs[DeeplistVideoOverridesKey] ?: "{}") }.getOrDefault(JSONObject())
                val modes = runCatching { JSONObject(prefs[DeeplistSongBackdropModesKey] ?: "{}") }.getOrDefault(JSONObject())
                values.put(destination, uri.toString()); modes.put(destination, "custom")
                prefs[DeeplistVideoOverridesKey] = values.toString(); prefs[DeeplistSongBackdropModesKey] = modes.toString()
                prefs[DeeplistArtworkBackgroundKey] = true; prefs[DeeplistBackdropModeKey] = 2
            } }
        }
    }
    if (track.isBlank()) return
    Text(stringResource(R.string.deeplist_this_song_background, metadata?.title.orEmpty()), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(mode == "auto", { changeMode("auto") }, label = { Text(stringResource(R.string.deeplist_auto_video)) })
        FilterChip(mode == "cover", { changeMode("cover") }, label = { Text(stringResource(R.string.deeplist_cover)) })
        FilterChip(mode == "custom", { pickingTrack = track; picker.launch(arrayOf("video/*")) }, label = { Text(stringResource(R.string.deeplist_my_video)) })
    }
    if (deeplistSongVideo(videos, track) != null) TextButton(onClick = { scope.launch { context.dataStore.edit { prefs ->
        val values = runCatching { JSONObject(prefs[DeeplistVideoOverridesKey] ?: "{}") }.getOrDefault(JSONObject())
        val modes = runCatching { JSONObject(prefs[DeeplistSongBackdropModesKey] ?: "{}") }.getOrDefault(JSONObject())
        values.remove(track); modes.put(track, "auto")
        prefs[DeeplistVideoOverridesKey] = values.toString(); prefs[DeeplistSongBackdropModesKey] = modes.toString()
    } } }) { Text(stringResource(R.string.deeplist_remove_video)) }
    if (failed) Text(stringResource(R.string.deeplist_video_permission_failed), color = MaterialTheme.colorScheme.error)
}
