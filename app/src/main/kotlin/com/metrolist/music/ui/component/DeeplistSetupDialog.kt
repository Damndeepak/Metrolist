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
        val values = preview ?: return
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
                runCatching { DeeplistSetup.decode(text) }.onSuccess { preview = it; status = null }.onFailure { preview = null; status = R.string.deeplist_setup_invalid }
            }, enabled = text.isNotBlank() && !busy) { Text(stringResource(R.string.deeplist_preview_import)) }
            preview?.let { values ->
                Text(stringResource(R.string.deeplist_import_count, values.size))
                values.forEach { (key, value) ->
                    val label = key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
                    Text(if (key == DeeplistEmojiCorrectionsKey.name) stringResource(R.string.deeplist_correction_count, decodeEmojiCorrections(value as String).size) else "$label: $value", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { applyImport(false) }, enabled = !busy) { Text(stringResource(R.string.deeplist_merge_setup)) }
                TextButton(onClick = { applyImport(true) }, enabled = !busy) { Text(stringResource(R.string.deeplist_replace_setup)) }
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
    var phrase by remember { mutableStateOf(initialPhrase) }
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
                runCatching { saveEmojiCorrection(raw, EmojiCorrection(phrase, icon, if (onlySong) songId.orEmpty() else "")) }
                    .onSuccess { raw = it; phrase = ""; icon = "" }.onFailure { error = true }
            }, enabled = phrase.isNotBlank()) { Text(stringResource(R.string.deeplist_save_correction)) }
            if (error) Text(stringResource(R.string.deeplist_setup_invalid))
            corrections.filter { it.song.isEmpty() || it.song == songId }.forEach { rule ->
                Row {
                    TextButton(onClick = { phrase = rule.phrase; icon = rule.emoji; onlySong = rule.song.isNotEmpty() }, Modifier.weight(1f)) {
                        Text("${rule.phrase} → ${rule.emoji.ifEmpty { "∅" }} · " + stringResource(if (rule.song.isEmpty()) R.string.deeplist_all_songs else R.string.deeplist_only_song))
                    }
                    TextButton(onClick = { raw = encodeEmojiCorrections(corrections - rule) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wordstorm_done)) } })
}
