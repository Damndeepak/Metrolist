package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val DeeplistMomentReason.label: Int
    get() = when (this) {
        DeeplistMomentReason.BEAT -> R.string.dp_moment_beat
        DeeplistMomentReason.VOICE -> R.string.dp_moment_voice
        DeeplistMomentReason.LYRICS -> R.string.dp_moment_lyrics
        DeeplistMomentReason.MOOD -> R.string.dp_moment_mood
    }

@Composable
internal fun DeeplistMomentDialog(moment: DeeplistSavedLyric, onDismiss: () -> Unit, onSave: (DeeplistSavedLyric) -> Unit) {
    var reason by remember(moment) { mutableStateOf(moment.reason ?: DeeplistMomentReason.LYRICS) }
    var note by remember(moment) { mutableStateOf(moment.note) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dp_moment_why)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (moment.text.isNotBlank()) Text(moment.text, style = MaterialTheme.typography.titleMedium)
            Text(moment.title, style = MaterialTheme.typography.bodySmall)
            DeeplistMomentReason.entries.chunked(2).forEach { reasons ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { reasons.forEach { choice ->
                    FilterChip(selected = reason == choice, onClick = { reason = choice }, label = { Text(stringResource(choice.label)) })
                } }
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(160) },
                label = { Text(stringResource(R.string.dp_moment_note)) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        } },
        confirmButton = { TextButton(onClick = { onSave(moment.copy(reason = reason, note = note.trim())) }) { Text(stringResource(R.string.dp_moment_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun DeeplistMomentActions(moment: DeeplistSavedLyric, canControl: Boolean, onSave: (DeeplistSavedLyric) -> Unit) {
    val connection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var edit by remember(moment) { mutableStateOf(false) }
    var loading by remember(moment) { mutableStateOf(false) }
    var failed by remember(moment) { mutableStateOf(false) }
    moment.reason?.let { Text(stringResource(it.label), style = MaterialTheme.typography.labelMedium) }
    if (moment.note.isNotBlank()) Text(moment.note, style = MaterialTheme.typography.bodySmall)
    Column {
        TextButton(onClick = { edit = true }) { Text(stringResource(R.string.dp_moment_why)) }
        TextButton(enabled = canControl && connection != null && moment.songId.isNotBlank() && !loading, onClick = {
            loading = true
            failed = false
            scope.launch {
                try {
                    val song = YouTube.queue(listOf(moment.songId)).getOrThrow().firstOrNull()
                        ?: error("Saved song unavailable")
                    connection?.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally { loading = false }
            }
        }) { Text(stringResource(if (loading) R.string.dp_moment_loading else R.string.dp_moment_discover)) }
    }
    Text(stringResource(R.string.dp_moment_discovery_hint), style = MaterialTheme.typography.bodySmall)
    if (failed) Text(stringResource(R.string.dp_moment_error), color = MaterialTheme.colorScheme.error)
    if (edit) DeeplistMomentDialog(moment, { edit = false }) { onSave(it); edit = false }
}
