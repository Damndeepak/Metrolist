package com.metrolist.music.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.lyrics.*
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DeeplistSetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var exportJson by remember { mutableStateOf("") }
    var includeCorrections by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<Int?>(null) }
    var preview by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    val errorText = stringResource(R.string.deeplist_setup_invalid)
    val readFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val data = ByteArray(240_001)
                    var count = 0
                    while (count < data.size) { val n = stream.read(data, count, data.size - count); if (n < 0) break; count += n }
                    require(count <= 240_000); String(data, 0, count, Charsets.UTF_8)
                } ?: error("No input")
            } }.onSuccess { text = it; preview = null; status = null }
                .onFailure { status = R.string.deeplist_setup_invalid }
        }
    }
    val writeFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(exportJson) } ?: error("No output")
            } }.onSuccess { status = R.string.deeplist_setup_exported }.onFailure { status = R.string.deeplist_setup_invalid }
        }
    }
    fun applyImport(replace: Boolean) {
        val values = preview?.filterKeys { it in selected } ?: return
        if (values.isEmpty()) return
        scope.launch {
            busy = true
            runCatching { context.dataStore.edit { prefs ->
                val undo = DeeplistSetup.snapshot(prefs)
                DeeplistSetup.apply(prefs, values, replace)
                prefs[DeeplistImportUndoKey] = undo
            } }.onSuccess { preview = null; status = R.string.deeplist_setup_applied }
                .onFailure { status = R.string.deeplist_setup_invalid }
            busy = false
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.deeplist_setup_share)) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.deeplist_setup_notice))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.deeplist_include_corrections), Modifier.weight(1f))
                Switch(includeCorrections, { includeCorrections = it })
            }
            TextButton(onClick = { scope.launch {
                val json = DeeplistSetup.snapshot(context.dataStore.data.first(), includeCorrections)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Deeplist", DeeplistSetup.shareCode(json)))
                status = R.string.deeplist_setup_copied
            } }) { Text(stringResource(R.string.deeplist_copy_setup)) }
            TextButton(onClick = { scope.launch {
                exportJson = DeeplistSetup.snapshot(context.dataStore.data.first(), includeCorrections)
                writeFile.launch("Deeplist-Setup.json")
            } }) { Text(stringResource(R.string.deeplist_export_setup)) }
            OutlinedTextField(text, { text = it.take(240_000); preview = null; status = null }, label = { Text(stringResource(R.string.deeplist_paste_setup)) }, maxLines = 4)
            TextButton(onClick = { readFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.deeplist_import_file)) }
            TextButton(onClick = {
                runCatching { DeeplistSetup.decode(text) }.onSuccess { preview = it; selected = it.keys; status = null }.onFailure { preview = null; status = R.string.deeplist_setup_invalid }
            }, enabled = text.isNotBlank() && !busy) { Text(stringResource(R.string.deeplist_preview_import)) }
            preview?.let { values ->
                Text(stringResource(R.string.deeplist_import_count, selected.size))
                values.forEach { (key, value) ->
                    val label = deeplistSetupLabel(key)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(key in selected, { checked -> selected = if (checked) selected + key else selected - key })
                        Text(if (key == DeeplistEmojiCorrectionsKey.name) stringResource(R.string.deeplist_correction_count, decodeEmojiCorrections(value as String).size) else "$label: $value", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { applyImport(false) }, enabled = !busy && selected.isNotEmpty()) { Text(stringResource(R.string.deeplist_merge_setup)) }
                TextButton(onClick = { applyImport(true) }, enabled = !busy && selected.isNotEmpty()) { Text(stringResource(R.string.deeplist_replace_setup)) }
            }
            TextButton(onClick = { scope.launch {
                runCatching { context.dataStore.edit { prefs ->
                    val undo = prefs[DeeplistImportUndoKey] ?: error("No undo")
                    DeeplistSetup.apply(prefs, DeeplistSetup.decode(undo), true)
                    prefs.remove(DeeplistImportUndoKey)
                } }.onSuccess { status = R.string.deeplist_setup_undone }
                    .onFailure { Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show() }
            } }) { Text(stringResource(R.string.deeplist_undo_import)) }
            status?.let { Text(stringResource(it)) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wordstorm_done)) } })
}

@Composable
internal fun DeeplistEmojiEditor(songId: String?, initialPhrase: String = "", onDismiss: () -> Unit) {
    var raw by com.metrolist.music.utils.rememberPreference(DeeplistEmojiCorrectionsKey, "[]")
    val corrections = remember(raw) { runCatching { decodeEmojiCorrections(raw) }.getOrDefault(emptyList()) }
    val targetSong = remember { songId }
    var editing by remember { mutableStateOf<EmojiCorrection?>(null) }
    var phrase by remember { mutableStateOf(initialPhrase.take(100)) }
    var icon by remember { mutableStateOf("") }
    var onlySong by remember { mutableStateOf(songId != null) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.deeplist_emoji_corrections)) }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.deeplist_correction_hint))
            OutlinedTextField(phrase, { phrase = it.take(100); error = false }, label = { Text(stringResource(R.string.deeplist_word_phrase)) })
            OutlinedTextField(icon, { icon = it.take(32); error = false }, label = { Text(stringResource(R.string.deeplist_replacement_emoji)) })
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.deeplist_only_song), Modifier.weight(1f))
                Switch(onlySong, { onlySong = it }, enabled = songId != null)
            }
            TextButton(onClick = {
                runCatching { saveEmojiCorrection(if (editing != null) encodeEmojiCorrections(corrections - editing!!) else raw, EmojiCorrection(phrase, icon, if (onlySong) targetSong.orEmpty() else "")) }
                    .onSuccess { raw = it; phrase = ""; icon = ""; editing = null }.onFailure { error = true }
            }, enabled = phrase.isNotBlank()) { Text(stringResource(R.string.deeplist_save_correction)) }
            if (error) Text(stringResource(R.string.deeplist_setup_invalid))
            corrections.filter { it.song.isEmpty() || it.song == targetSong }.forEach { rule ->
                Row {
                    TextButton(onClick = { editing = rule; phrase = rule.phrase; icon = rule.emoji; onlySong = rule.song.isNotEmpty() }, Modifier.weight(1f)) {
                        Text("${rule.phrase} → ${rule.emoji.ifEmpty { "∅" }} · " + stringResource(if (rule.song.isEmpty()) R.string.deeplist_all_songs else R.string.deeplist_only_song))
                    }
                    TextButton(onClick = { raw = encodeEmojiCorrections(corrections - rule) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wordstorm_done)) } })
}

@Composable
private fun deeplistSetupLabel(key: String): String {
    val label = when (key) {
        DeeplistAppleNeonKey.name, DeeplistStackNeonKey.name -> R.string.verci_neon
        DeeplistWordZoomKey.name -> R.string.deeplist_word_zoom_label
        DeeplistWordSpacingKey.name -> R.string.deeplist_word_spacing_label
        DeeplistWordGlowKey.name -> R.string.deeplist_word_glow
        DeeplistAppleBeatKey.name -> R.string.deeplist_apple_beat
        DeeplistAppleSizeKey.name -> R.string.verci_text_size
        DeeplistBackdropModeKey.name -> R.string.deeplist_background
        DeeplistBackdropBlurKey.name -> R.string.deeplist_soft_blur
        DeeplistBackdropDimKey.name -> R.string.deeplist_background_brightness
        DeeplistTapSeekKey.name -> R.string.deeplist_tap_seek
        DeeplistDuetEnabledKey.name -> R.string.deeplist_duet
        DeeplistInstrumentalKey.name -> R.string.deeplist_instrumental
        WordstormEnabledKey.name -> R.string.wordstorm_settings
        VerciLyricsKey.name -> R.string.verci_lyrics
        VerciStyleKey.name -> R.string.verci_style
        VerciFontKey.name -> R.string.verci_font
        VerciTextSizeKey.name -> R.string.verci_text_size
        VerciSpacingKey.name -> R.string.verci_spacing
        VerciBrightnessKey.name -> R.string.verci_brightness
        VerciColorKey.name -> R.string.verci_colors
        VerciNeonKey.name -> R.string.verci_neon
        VerciEmojiKey.name -> R.string.verci_emojis
        VerciAudioReactiveKey.name -> R.string.verci_audio_reactive
        VerciAdvancedEmojiKey.name -> R.string.verci_advanced_emojis
        VerciMegaEmojiKey.name -> R.string.verci_mega_emojis
        VerciCameraRingKey.name -> R.string.verci_camera_ring
        VerciParticlesKey.name -> R.string.verci_particles
        VerciClockKey.name -> R.string.verci_clock
        VerciBeatLiftKey.name -> R.string.verci_beat_lift
        VerciDoubleTapKey.name -> R.string.verci_double_tap
        VerciSwipeKey.name -> R.string.verci_swipe
        DeeplistEmojiDensityKey.name -> R.string.deeplist_emoji_density
        DeeplistEmojiPackKey.name -> R.string.deeplist_emoji_pack
        DeeplistSoftBlurKey.name -> R.string.deeplist_soft_blur
        DeeplistAnimationKey.name -> R.string.deeplist_animation_intensity
        DeeplistArtworkBackgroundKey.name -> R.string.deeplist_artwork_background
        DeeplistAutoPerformanceKey.name -> R.string.deeplist_auto_performance
        DeeplistPerformanceKey.name -> R.string.deeplist_reduce_effects
        WordstormFontSizeKey.name -> R.string.wordstorm_size
        WordstormMotionKey.name -> R.string.wordstorm_motion_on
        WordstormCoverKey.name -> R.string.wordstorm_cover
        WordstormAccentKey.name -> R.string.verci_colors
        DeeplistNeonKey.name -> R.string.deeplist_neon
        DeeplistSpeedKey.name -> R.string.deeplist_speed
        DeeplistContextKey.name -> R.string.deeplist_context_on
        DeeplistDensityKey.name -> R.string.deeplist_density
        DeeplistRainSpeedKey.name -> R.string.deeplist_rain_speed
        DeeplistWordBrightnessKey.name -> R.string.deeplist_word_brightness
        DeeplistRingsKey.name -> R.string.deeplist_rings
        DeeplistGlowKey.name -> R.string.deeplist_glow
        DeeplistSparksKey.name -> R.string.deeplist_sparks
        DeeplistLiftKey.name -> R.string.deeplist_lift
        DeeplistFlightKey.name -> R.string.deeplist_flight
        DeeplistGesturesKey.name -> R.string.deeplist_gestures
        DeeplistChorusBoostKey.name -> R.string.deeplist_chorus_boost
        else -> R.string.verci_style
    }
    return stringResource(label)
}
