package com.metrolist.music.ui.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.lyrics.VerciPreset
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun verciStudioItems(): List<Material3SettingsItem> {
    val (brightness, setBrightness) = rememberPreference(VerciBrightnessKey, .8f)
    val (beatLift, setBeatLift) = rememberPreference(VerciBeatLiftKey, true)
    val (mega, setMega) = rememberPreference(VerciMegaEmojiKey, true)
    val (advanced, setAdvanced) = rememberPreference(VerciAdvancedEmojiKey, false)
    val (ring, setRing) = rememberPreference(VerciCameraRingKey, false)
    val (particles, setParticles) = rememberPreference(VerciParticlesKey, false)
    val (clock, setClock) = rememberPreference(VerciClockKey, false)
    val (reactive, setReactive) = rememberPreference(VerciAudioReactiveKey, true)
    val (recording, setRecording) = rememberPreference(VerciRecordingKey, false)
    val (doubleTap, setDoubleTap) = rememberPreference(VerciDoubleTapKey, true)
    val (swipe, setSwipe) = rememberPreference(VerciSwipeKey, true)
    val (font, setFont) = rememberEnumPreference(VerciFontKey, VerciFont.BOLD)
    val (spacing, setSpacing) = rememberPreference(VerciSpacingKey, 1f)
    val (color, setColor) = rememberPreference(VerciColorKey, 0)
    val (size, _) = rememberPreference(VerciTextSizeKey, 52f)
    val (style, _) = rememberEnumPreference(VerciStyleKey, VerciStyle.FISHEYE)
    val (neon, _) = rememberPreference(VerciNeonKey, true)
    val (emojis, _) = rememberPreference(VerciEmojiKey, true)
    val (saved, setSaved) = rememberPreference(VerciPresetsKey, "{}")
    val (floating, setFloating) = rememberPreference(FloatingLyricsKey, false)
    val (website, _) = rememberPreference(DeeplistWebsiteKey, "")
    val (sharing, setSharing) = rememberPreference(DeeplistSyncEnabledKey, false)
    val (_, setPairToken) = rememberPreference(DeeplistTokenKey, "")
    val bridgeState by com.metrolist.music.deeplist.DeeplistBridgeStatus.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val connection = LocalPlayerConnection.current
    val songState = connection?.currentSong?.collectAsStateWithLifecycle(initialValue = null)
    val song = songState?.value
    var dialog by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf(false) }
    val presets = remember(saved) { runCatching { JSONObject(saved) }.getOrDefault(JSONObject()) }
    if (dialog == "setup") DeeplistSetupDialog { dialog = "" }
    if (dialog == "tools") DeeplistTools { dialog = "" }
    val metadata = connection?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    if (dialog == "corrections") DeeplistEmojiEditor(metadata?.id) { dialog = "" }
    if (dialog == "camera") VerciCameraFit { dialog = "" }
    if (dialog == "font") EnumDialog(
        onDismiss = { dialog = "" }, onSelect = { setFont(it); dialog = "" },
        title = stringResource(R.string.verci_font), current = font, values = VerciFont.entries,
        valueText = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
    )
    if (dialog == "brightness" || dialog == "spacing") {
        val isBrightness = dialog == "brightness"
        var value by remember(dialog) { mutableFloatStateOf(if (isBrightness) brightness else spacing) }
        DefaultDialog(onDismiss = { dialog = "" }, buttons = {
            TextButton(onClick = { if (isBrightness) setBrightness(value) else setSpacing(value); dialog = "" }) { Text(stringResource(android.R.string.ok)) }
        }) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(if (isBrightness) R.string.verci_brightness else R.string.verci_spacing))
                Text("${(value * 100).toInt()}%")
                Slider(value = value, onValueChange = { value = it }, valueRange = if (isBrightness) 0f..1f else .75f..1.35f)
            }
        }
    }
    if (dialog == "presets") AlertDialog(
        onDismissRequest = { dialog = "" }, title = { Text(stringResource(R.string.verci_presets)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, label = { Text(stringResource(R.string.verci_preset_name)) }, singleLine = true)
                TextButton(onClick = {
                    val updated = JSONObject(presets.toString())
                    updated.put(name.trim(), VerciPreset(style, font, size, spacing, brightness, color, neon, emojis, reactive, advanced, ring, particles, clock, beatLift, mega).encode())
                    setSaved(updated.toString()); name = ""
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.deeplist_save_style)) }
                presets.keys().asSequence().toList().forEach { label ->
                    Row {
                        TextButton(onClick = {
                            VerciPreset.decode(presets.optString(label))?.let { p -> scope.launch {
                                context.dataStore.edit { v ->
                                    v[VerciStyleKey] = p.style.name; v[VerciFontKey] = p.font.name
                                    v[VerciTextSizeKey] = p.size; v[VerciSpacingKey] = p.spacing
                                    v[VerciBrightnessKey] = p.brightness; v[VerciColorKey] = p.color
                                    v[VerciAdvancedEmojiKey] = p.advanced; v[VerciCameraRingKey] = p.ring
                                    v[VerciBeatLiftKey] = p.beatLift; v[VerciMegaEmojiKey] = p.mega
                                    v[VerciParticlesKey] = p.particles; v[VerciClockKey] = p.clock
                                    v[VerciNeonKey] = p.neon; v[VerciEmojiKey] = p.emojis; v[VerciAudioReactiveKey] = p.reactive
                                }
                            } }
                        }, modifier = Modifier.weight(1f)) { Text(label) }
                        TextButton(onClick = { val updated = JSONObject(presets.toString()); updated.remove(label); setSaved(updated.toString()) }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text(stringResource(R.string.wordstorm_done)) } },
    )
    if (permission) AlertDialog(
        onDismissRequest = { permission = false }, title = { Text(stringResource(R.string.verci_bubble)) },
        text = { Text(stringResource(if (permissionError) R.string.verci_overlay_unavailable else R.string.verci_overlay_permission)) },
        confirmButton = { TextButton(onClick = {
            if (Settings.canDrawOverlays(context)) { setFloating(true); permission = false }
            else permissionError = runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }.isFailure
        }) { Text(stringResource(R.string.verci_allow_overlay)) } },
        dismissButton = { TextButton(onClick = { permission = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
    return buildList {
        add(Material3SettingsItem(title = { Text(stringResource(R.string.deeplist_tools)) }, onClick = { dialog = "tools" }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.deeplist_setup_share)) }, onClick = { dialog = "setup" }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.deeplist_emoji_corrections)) }, onClick = { dialog = "corrections" }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_beat_lift)) }, trailingContent = { Switch(beatLift, setBeatLift) }, onClick = { setBeatLift(!beatLift) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_mega_emojis)) }, trailingContent = { Switch(mega, setMega) }, onClick = { setMega(!mega) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_camera_fit)) }, onClick = { dialog = "camera" }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.deeplist_website_sync)) }, description = { Text(if (website.isBlank()) stringResource(R.string.deeplist_easy_connect_hint) else "$website · ${bridgeState.name.lowercase()}") }, trailingContent = {
            if (sharing) TextButton(onClick = { setSharing(false); setPairToken("") }) { Text(stringResource(R.string.deeplist_stop_sync)) }
            else if (website.isNotBlank()) TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$website/deeplist.html"))) }) { Text(stringResource(R.string.deeplist_open_website)) }
        }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_advanced_emojis)) }, description = { Text(stringResource(R.string.verci_advanced_emojis_hint)) }, trailingContent = { Switch(advanced, setAdvanced) }, onClick = { setAdvanced(!advanced) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_camera_ring)) }, trailingContent = { Switch(ring, setRing) }, onClick = { setRing(!ring) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_particles)) }, trailingContent = { Switch(particles, setParticles) }, onClick = { setParticles(!particles) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_clock)) }, trailingContent = { Switch(clock, setClock) }, onClick = { setClock(!clock) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_brightness)) }, description = { Text("${(brightness * 100).toInt()}%") }, onClick = { dialog = "brightness" }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_audio_reactive)) }, description = { Text(stringResource(R.string.verci_audio_hint)) }, trailingContent = { Switch(reactive, setReactive) }, onClick = { setReactive(!reactive) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_font)) }, description = { Text(font.name.lowercase()) }, onClick = { dialog = "font" }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_spacing)) }, description = { Text("${(spacing * 100).toInt()}%") }, onClick = { dialog = "spacing" }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_colors)) }, trailingContent = {
            TextButton(onClick = { setColor((color + 1) % verciColors.size) }) { Text("● ${color + 1}", color = Color(verciColors[color.coerceIn(0, 4)])) }
        }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_presets)) }, onClick = { dialog = "presets" }))
        if (song != null) add(Material3SettingsItem(
            title = { Text(stringResource(R.string.deeplist_sync_adjust, song.song.lyricsOffset)) },
            trailingContent = {
                Row {
                    listOf(-100, 0, 100).forEach { delta ->
                        TextButton(onClick = {
                            val current = song.song
                            database.query { upsert(current.copy(lyricsOffset = if (delta == 0) 0 else (current.lyricsOffset + delta).coerceIn(-10000, 10000))) }
                        }) { Text(if (delta == 0) "0" else if (delta > 0) "+100" else "−100") }
                    }
                }
            },
        ))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_recording)) }, description = { Text(stringResource(R.string.verci_recording_hint)) }, trailingContent = { Switch(recording, setRecording) }, onClick = { setRecording(!recording) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_double_tap)) }, trailingContent = { Switch(doubleTap, setDoubleTap) }, onClick = { setDoubleTap(!doubleTap) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_swipe)) }, trailingContent = { Switch(swipe, setSwipe) }, onClick = { setSwipe(!swipe) }))
        add(Material3SettingsItem(title = { Text(stringResource(R.string.verci_bubble)) }, description = { Text(stringResource(R.string.verci_bubble_hint)) }, trailingContent = { Switch(floating, { value -> if (!value) setFloating(false) else if (Settings.canDrawOverlays(context)) setFloating(true) else { permissionError = false; permission = true } }) }, onClick = {
            if (floating) setFloating(false) else if (Settings.canDrawOverlays(context)) setFloating(true) else { permissionError = false; permission = true }
        }))
    }
}
