package com.metrolist.music.ui.component

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.*
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun DeeplistTools(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection = LocalPlayerConnection.current
    val metadata = connection?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val entity = connection?.currentLyrics?.collectAsStateWithLifecycle(initialValue = null)?.value
    val database = LocalDatabase.current
    val manager = LocalListenTogetherManager.current
    val canControl = manager?.isInRoom != true || manager.isHost
    var saved by rememberPreference(DeeplistSavedLyricsKey, "[]")
    var songStyles by rememberPreference(DeeplistSongStylesKey, "{}")
    var density by rememberPreference(DeeplistEmojiDensityKey, 1)
    var pack by rememberPreference(DeeplistEmojiPackKey, 0)
    var blur by rememberPreference(DeeplistSoftBlurKey, .35f)
    var animation by rememberPreference(DeeplistAnimationKey, 1f)
    var artwork by rememberPreference(DeeplistArtworkBackgroundKey, true)
    var automatic by rememberPreference(DeeplistAutoPerformanceKey, true)
    var reduced by rememberPreference(DeeplistPerformanceKey, false)
    var roman by rememberPreference(DeeplistRomanHindiKey, false)
    var recording by rememberPreference(VerciRecordingKey, false)
    var touchLock by rememberPreference(DeeplistTouchLockKey, false)
    var status by remember { mutableStateOf<Int?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    var draft by remember(metadata?.id) { mutableStateOf(entity?.takeIf { it.id == metadata?.id }?.lyrics.orEmpty()) }
    var pendingTrack by remember { mutableStateOf<String?>(null) }
    val importLyrics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingTrack
        if (uri != null && id != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val buffer = CharArray(200_001)
                    var count = 0
                    while (count < buffer.size) { val n = reader.read(buffer, count, buffer.size - count); if (n < 0) break; count += n }
                    require(count <= 200_000); String(buffer, 0, count)
                } ?: error("No input")
            } }.onSuccess { value ->
                if (value.isBlank()) status = R.string.deeplist_setup_invalid
                else { draft = value; edit = true }
            }.onFailure { status = R.string.deeplist_setup_invalid }
        }
    }
    val trackId = metadata?.id
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.deeplist_tools)) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            listOf(R.string.deeplist_appearance, R.string.deeplist_controls_tab, R.string.deeplist_saved_lyrics).forEachIndexed { i, label ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(stringResource(label)) })
            }
            if (tab == 0) {
                Text(stringResource(R.string.deeplist_quick_presets))
                listOf(R.string.deeplist_clean, R.string.deeplist_deepglow, R.string.wordstorm_cinematic).forEachIndexed { i, label ->
                    TextButton(onClick = { scope.launch { context.dataStore.edit { prefs ->
                        prefs[WordstormEnabledKey] = true; prefs[VerciLyricsKey] = true
                        prefs[VerciStyleKey] = if (i == 0) VerciStyle.APPLE.name else if (i == 1) VerciStyle.FISHEYE_VISUAL.name else VerciStyle.VISUAL.name
                        prefs[VerciNeonKey] = i != 0; prefs[VerciParticlesKey] = i == 2
                        prefs[VerciEmojiKey] = i != 0; prefs[VerciAdvancedEmojiKey] = i == 1
                        prefs[VerciTextSizeKey] = if (i == 0) 46f else 52f
                    } } }) { Text(stringResource(label)) }
                }
                Text(stringResource(R.string.deeplist_emoji_density))
                listOf(R.string.deeplist_light, R.string.deeplist_expressive, R.string.deeplist_chaos).forEachIndexed { i, label ->
                    FilterChip(selected = density == i, onClick = { density = i }, label = { Text(stringResource(label)) })
                }
                Text(stringResource(R.string.deeplist_emoji_pack))
                listOf(R.string.deeplist_classic, R.string.deeplist_monochrome, R.string.deeplist_neon_pack).forEachIndexed { i, label ->
                    FilterChip(selected = pack == i, onClick = { pack = i }, label = { Text(stringResource(label)) })
                }
                Text(stringResource(R.string.deeplist_soft_blur))
                Slider(blur.coerceIn(0f, 1f), { blur = it }, valueRange = 0f..1f)
                Text(stringResource(R.string.deeplist_animation_intensity))
                Slider(animation.coerceIn(0f, 1f), { animation = it }, valueRange = 0f..1f)
                ToolSwitch(R.string.deeplist_artwork_background, artwork) { artwork = it }
                ToolSwitch(R.string.deeplist_auto_performance, automatic) { automatic = it }
                ToolSwitch(R.string.deeplist_reduce_effects, reduced) { reduced = it }
                if (Build.VERSION.SDK_INT >= 29) ToolSwitch(R.string.deeplist_roman_hindi, roman) { roman = it }
                TextButton(onClick = { scope.launch {
                    val id = trackId ?: return@launch
                    val json = DeeplistSetup.snapshot(context.dataStore.data.first(), false)
                    val styles = runCatching { JSONObject(songStyles) }.getOrDefault(JSONObject())
                    require(styles.length() < 300 || styles.has(id))
                    styles.put(id, json); songStyles = styles.toString(); status = R.string.deeplist_style_saved
                } }, enabled = trackId != null) { Text(stringResource(R.string.deeplist_save_song_style)) }
                TextButton(onClick = {
                    val styles = runCatching { JSONObject(songStyles) }.getOrDefault(JSONObject())
                    styles.remove(trackId.orEmpty()); songStyles = styles.toString()
                }, enabled = trackId != null) { Text(stringResource(R.string.deeplist_remove_song_style)) }
            }
            if (tab == 1) {
                ToolSwitch(R.string.deeplist_touch_lock, touchLock) { touchLock = it }
                ToolSwitch(R.string.verci_recording, recording) { recording = it }
                Text(stringResource(R.string.deeplist_reel_hint))
                TextButton(onClick = { pendingTrack = trackId; draft = entity?.takeIf { it.id == trackId }?.lyrics.orEmpty(); edit = true }, enabled = trackId != null) { Text(stringResource(R.string.deeplist_edit_lyrics)) }
                TextButton(onClick = { pendingTrack = trackId; importLyrics.launch(arrayOf("text/*", "application/octet-stream", "application/x-subrip")) }, enabled = trackId != null) { Text(stringResource(R.string.deeplist_import_lrc)) }
                Text(stringResource(R.string.deeplist_local_lyrics_hint))
                val loop = connection?.service?.lyricsLoop
                if (loop != null) {
                    Text(stringResource(R.string.deeplist_loop_range, loop.start?.let(::verciTime) ?: "—", loop.end?.let(::verciTime) ?: "—"))
                    Row {
                        TextButton(onClick = { loop.markStart() }, enabled = canControl) { Text(stringResource(R.string.deeplist_mark_a)) }
                        TextButton(onClick = { if (!loop.markEnd()) status = R.string.deeplist_loop_hint }, enabled = canControl && loop.start != null) { Text(stringResource(R.string.deeplist_mark_b)) }
                        TextButton(onClick = { loop.clear() }) { Text(stringResource(R.string.deeplist_clear_loop)) }
                    }
                }
                Text(stringResource(R.string.deeplist_sleep_timer))
                Row { listOf(15, 30, 60).forEach { minutes ->
                    TextButton(onClick = { connection?.service?.sleepTimer?.start(minutes) }, enabled = connection != null && canControl) { Text(stringResource(R.string.deeplist_minutes, minutes)) }
                } }
            }
            if (tab == 2) {
                val items = deeplistSavedLyrics(saved)
                if (items.isEmpty()) Text(stringResource(R.string.deeplist_no_saved))
                items.forEach { lyric ->
                    Text(lyric.text)
                    Text(lyric.title, style = MaterialTheme.typography.labelSmall)
                    Row {
                        TextButton(onClick = { scope.launch {
                            runCatching { withContext(Dispatchers.IO) { deeplistShareCard(context, lyric) } }
                                .onSuccess { runCatching { context.startActivity(Intent.createChooser(it, null)) }.onFailure { status = R.string.deeplist_card_error } }
                                .onFailure { status = R.string.deeplist_card_error }
                        } }) { Text(stringResource(R.string.deeplist_share_card)) }
                        TextButton(onClick = { saved = org.json.JSONArray().apply {
                            items.filter { it != lyric }.forEach { put(JSONObject().put("text", it.text).put("title", it.title).put("artist", it.artist)) }
                        }.toString() }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
            status?.let { Text(stringResource(it)) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wordstorm_done)) } })
    if (edit) AlertDialog(onDismissRequest = { edit = false }, title = { Text(stringResource(R.string.deeplist_edit_lyrics)) }, text = {
        Column {
            Text(stringResource(R.string.deeplist_review_lyrics))
            OutlinedTextField(draft, { draft = it.take(200_000) }, modifier = Modifier.heightIn(max = 330.dp), label = { Text(stringResource(R.string.deeplist_edit_lyrics)) })
        }
    }, confirmButton = { TextButton(onClick = {
        val id = pendingTrack ?: return@TextButton
        if (draft.isNotBlank()) database.query { upsert(LyricsEntity(id, draft, "Manual")) }
        edit = false
    }, enabled = pendingTrack != null && draft.isNotBlank()) { Text(stringResource(R.string.deeplist_save_correction)) } }, dismissButton = {
        TextButton(onClick = { edit = false }) { Text(stringResource(android.R.string.cancel)) }
    })
}

@Composable
private fun ToolSwitch(label: Int, value: Boolean, changed: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f)); Switch(value, changed)
    }
}
