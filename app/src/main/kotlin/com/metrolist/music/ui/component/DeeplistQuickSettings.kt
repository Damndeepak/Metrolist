package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.lyrics.*
import com.metrolist.music.utils.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun DeeplistQuickSettings(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by rememberEnumPreference(VerciStyleKey, VerciStyle.SHIP)
    val legacy by rememberPreference(VerciTextSizeKey, 52f)
    var size by rememberPreference(deeplistSizeKey(style), if (style == VerciStyle.APPLE) 32f else legacy)
    var background by rememberPreference(DeeplistBackdropModeKey, 2)
    var enabled by rememberPreference(DeeplistArtworkBackgroundKey, true)
    var dim by rememberPreference(DeeplistBackdropDimKey, .48f)
    var blur by rememberPreference(DeeplistBackdropBlurKey, .55f)
    var recording by rememberPreference(VerciRecordingKey, false)
    var neon by rememberPreference(deeplistNeonKey(style), style != VerciStyle.APPLE)
    var zoom by rememberPreference(DeeplistWordZoomKey, .18f)
    var wordSpacing by rememberPreference(DeeplistWordSpacingKey, 1f)
    var lineSpacing by rememberPreference(VerciSpacingKey, 1f)
    var wordGlow by rememberPreference(DeeplistWordGlowKey, true)
    var appleBeat by rememberPreference(DeeplistAppleBeatKey, false)
    var verciEnabled by rememberPreference(VerciLyricsKey, true)
    var tab by remember { mutableIntStateOf(0) }
    var styleDialog by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var journey by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf(false) }
    val advanced = if (tab == 1) verciLyricsSettingsItems() else emptyList()
    if (styleDialog) EnumDialog(onDismiss = { styleDialog = false }, onSelect = { style = it; verciEnabled = true; styleDialog = false }, title = stringResource(R.string.verci_style), current = style, values = VerciStyle.entries, valueText = { deeplistStyleLabel(it) })
    if (tools) DeeplistTools { tools = false }
    if (setup) DeeplistSetupDialog { setup = false }
    if (journey) DeeplistJourneyEditor { journey = false }
    if (playlists) com.metrolist.music.ui.menu.DeeplistPlaylistImportDialog(onDismiss = { playlists = false })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.88f)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.wordstorm_settings), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tab == 0, { tab = 0 }, label = { Text(stringResource(R.string.deeplist_quick)) })
                    FilterChip(tab == 1, { tab = 1 }, label = { Text(stringResource(R.string.deeplist_advanced)) })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tab == 0) {
                        Text(stringResource(R.string.deeplist_global_look_hint), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { scope.launch { context.dataStore.edit { it[VerciLyricsKey] = false }; onDismiss() } }) { Text(stringResource(R.string.wordstorm_enable)) }
                        TextButton(onClick = { styleDialog = true }) { Text(stringResource(R.string.deeplist_style_value, deeplistStyleLabel(style))) }
                        Text(stringResource(R.string.deeplist_mode_size, size.toInt()))
                        Slider(size.coerceIn(if (style == VerciStyle.APPLE) 24f else 28f, if (style == VerciStyle.APPLE) 96f else 200f), { size = it }, valueRange = if (style == VerciStyle.APPLE) 24f..96f else 28f..200f)
                        Text(stringResource(R.string.verci_spacing))
                        Slider(lineSpacing, { lineSpacing = it }, valueRange = .45f..1.35f)
                        Text(stringResource(R.string.deeplist_word_zoom, (zoom * 100).toInt()))
                        Slider(zoom, { zoom = it }, valueRange = 0f..0.35f)
                        if (style == VerciStyle.APPLE) {
                            Text(stringResource(R.string.deeplist_word_spacing, (wordSpacing * 100).toInt()))
                            Slider(wordSpacing, { wordSpacing = it }, valueRange = .5f..1.4f)
                            QuickSwitch(R.string.deeplist_word_glow, wordGlow) { wordGlow = it }
                            QuickSwitch(R.string.deeplist_apple_beat, appleBeat) { appleBeat = it }
                        }
                        Text(stringResource(R.string.deeplist_background))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(R.string.deeplist_off, R.string.deeplist_cover, R.string.deeplist_auto_video).forEachIndexed { i, label ->
                                FilterChip((if (enabled) background else 0) == i, { background = i; enabled = i != 0 }, label = { Text(stringResource(label)) })
                            }
                        }
                        Text(stringResource(R.string.deeplist_background_hint), style = MaterialTheme.typography.bodySmall)
                        DeeplistSongBackground()
                        if (enabled && background > 0) {
                            Text(stringResource(R.string.deeplist_background_brightness)); Slider(1f - dim, { dim = 1f - it }, valueRange = .15f..0.85f)
                            Text(stringResource(R.string.deeplist_soft_blur)); Slider(blur, { blur = it })
                        }
                        QuickSwitch(R.string.verci_recording, recording) { recording = it }
                        QuickSwitch(R.string.verci_neon, neon) { neon = it }
                        Text(stringResource(R.string.deeplist_quick_presets), style = MaterialTheme.typography.titleSmall)
                        Row {
                            listOf(R.string.deeplist_clean, R.string.deeplist_deepglow, R.string.wordstorm_cinematic).forEachIndexed { i, label ->
                                TextButton(onClick = { scope.launch { context.dataStore.edit { p ->
                                    val renderer = if (i == 0) VerciStyle.APPLE else if (i == 1) VerciStyle.SHIP else VerciStyle.VISUAL
                                    p[VerciStyleKey] = renderer.name; p[VerciLyricsKey] = true; p[WordstormEnabledKey] = true
                                    p[VerciNeonKey] = i == 1; p[VerciParticlesKey] = i == 2
                                    p[DeeplistArtworkBackgroundKey] = true; p[DeeplistBackdropModeKey] = 2
                                    p[deeplistSizeKey(renderer)] = if (i == 0) 32f else if (i == 1) 110f else 64f
                                } } }) { Text(stringResource(label)) }
                            }
                        }
                        HorizontalDivider()
                        TextButton(onClick = { journey = true }) { Text(stringResource(R.string.deeplist_song_journey)) }
                        TextButton(onClick = { tools = true }) { Text(stringResource(R.string.deeplist_tools)) }
                        TextButton(onClick = { playlists = true }) { Text(stringResource(R.string.deeplist_playlist_import)) }
                        TextButton(onClick = { setup = true }) { Text(stringResource(R.string.deeplist_transfer_settings)) }
                        TextButton(onClick = { size = if (style == VerciStyle.APPLE) 32f else 52f; dim = .48f; blur = .55f }) { Text(stringResource(R.string.deeplist_reset_mode)) }
                    } else {
                        Material3SettingsGroup(items = advanced)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.wordstorm_done)) }
            }
        }
    }
}

@Composable
internal fun deeplistStyleLabel(style: VerciStyle): String = stringResource(when (style) {
    VerciStyle.APPLE -> R.string.deeplist_apple_style
    VerciStyle.NEON_STACK -> R.string.deeplist_neon_stack
    VerciStyle.SHIP -> R.string.verci_ship
    VerciStyle.VISUAL -> R.string.verci_visual
    VerciStyle.FISHEYE -> R.string.verci_fisheye
    VerciStyle.FISHEYE_VISUAL -> R.string.verci_fisheye_visual
})

@Composable
private fun QuickSwitch(label: Int, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(label), Modifier.weight(1f)); Switch(checked, change) }
}

@Composable
private fun DeeplistJourneyEditor(onDismiss: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val lyrics by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val track = metadata?.id.orEmpty()
    var raw by rememberPreference(DeeplistJourneysKey, "{}")
    var tags by rememberPreference(DeeplistDuetTagsKey, "{}")
    var duet by rememberPreference(DeeplistDuetEnabledKey, false)
    var instrumental by rememberPreference(DeeplistInstrumentalKey, true)
    var chorus by rememberPreference(DeeplistChorusBoostKey, true)
    var provider by rememberPreference(DeeplistVideoProviderKey, "")
    var choose by remember { mutableStateOf(false) }
    var showProvider by remember { mutableStateOf(false) }
    var editingTime by remember(track) { mutableLongStateOf(connection.player.currentPosition) }
    LaunchedEffect(track) { while (true) { editingTime = connection.player.currentPosition + (song?.song?.lyricsOffset ?: 0); kotlinx.coroutines.delay(150) } }
    val cues = remember(raw, track) { deeplistCues(raw, track) }
    val entries = remember(lyrics, track) { LyricsUtils.parseLyrics(lyrics?.takeIf { it.id == track }?.lyrics.orEmpty()).filter { !it.isBackground && it.text.isNotBlank() } }
    if (choose) EnumDialog(onDismiss = { choose = false }, onSelect = { style ->
        raw = deeplistPutCue(raw, track, connection.player.currentPosition, style); choose = false
    }, title = stringResource(R.string.deeplist_journey_add), current = VerciStyle.SHIP, values = VerciStyle.entries, valueText = { deeplistStyleLabel(it) })
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.deeplist_song_journey)) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(metadata?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.deeplist_journey_hint))
            TextButton(onClick = { choose = true }, enabled = track.isNotBlank()) { Text(stringResource(R.string.deeplist_journey_add)) }
            cues.forEach { cue -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${verciTime(cue.time)} · ${deeplistStyleLabel(cue.style)}", Modifier.weight(1f))
                TextButton(onClick = {
                    val root = JSONObject(raw); root.put(track, org.json.JSONArray().apply { cues.filter { it != cue }.forEach { put(JSONObject().put("time", it.time).put("style", it.style.name)) } }); raw = root.toString()
                }) { Text(stringResource(R.string.delete)) }
            } }
            if (cues.isNotEmpty()) TextButton(onClick = { val root = JSONObject(raw); root.remove(track); raw = root.toString() }) { Text(stringResource(R.string.deeplist_clear_journey)) }
            QuickSwitch(R.string.deeplist_chorus_boost, chorus) { chorus = it }
            QuickSwitch(R.string.deeplist_instrumental, instrumental) { instrumental = it }
            QuickSwitch(R.string.deeplist_duet, duet) { duet = it }
            if (duet) {
                Text(stringResource(R.string.deeplist_duet_hint))
                Text(entries.lastOrNull { it.time <= editingTime }?.text.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Row { listOf("A", "B", "A + B").forEachIndexed { i, label -> TextButton(onClick = {
                    val time = connection.player.currentPosition + (song?.song?.lyricsOffset ?: 0)
                    entries.lastOrNull { it.time <= time }?.let { line ->
                        val root = runCatching { JSONObject(tags) }.getOrDefault(JSONObject())
                        val tag = root.optJSONObject(track) ?: JSONObject(); tag.put(line.time.toString(), i); root.put(track, tag); tags = root.toString()
                    }
                }) { Text(label) } } }
            }
            HorizontalDivider()
            DeeplistSongBackground()
            TextButton(onClick = { showProvider = !showProvider }) { Text(stringResource(R.string.deeplist_video_provider)) }
            if (showProvider) {
                Text(stringResource(R.string.deeplist_provider_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(provider, { if (it.isBlank() || it.startsWith("https://")) provider = it.take(2000) }, label = { Text("HTTPS URL") })
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wordstorm_done)) } })
}
