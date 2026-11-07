package com.metrolist.music.ui.menu

import android.net.Uri
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
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.deeplist.*
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeeplistPlaylistImportDialog(initial: String = "", onDismiss: () -> Unit, onSaved: (String) -> Unit = {}) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initial) }
    var name by remember { mutableStateOf("Playlist") }
    var tracks by remember { mutableStateOf<List<DeeplistImportTrack>>(emptyList()) }
    var choices by remember { mutableStateOf<List<List<SongItem>>>(emptyList()) }
    var selected by remember { mutableStateOf<Map<Int, com.metrolist.music.models.MediaMetadata?>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            busy = false
        } else {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { stream -> val bytes = deeplistReadLimited(stream, 1_000_000); require(bytes.size <= 1_000_000); String(bytes) } ?: error("No file") } }
                    .onSuccess { input = it; tracks = emptyList(); selected = emptyMap(); choices = emptyList(); error = false }.onFailure { error = true }
                busy = false
            }
        }
    }
    fun preview() { if (busy) return; scope.launch {
        busy = true; error = false
        tracks = emptyList(); selected = emptyMap(); choices = emptyList()
        runCatching { withContext(Dispatchers.IO) {
            val value = input.trim()
            val uri = Uri.parse(value)
            when {
                value.startsWith("deeplist://") ||
                    value.startsWith("https://deeplist-playlists.cherrybard2.chatgpt.site/playlist") ->
                    DeeplistPlaylistTransfer.decode(value)
                uri.scheme == "https" && uri.host in setOf("music.youtube.com", "www.youtube.com", "youtube.com") -> {
                    val playlistId = uri.getQueryParameter("list").orEmpty()
                    require(playlistId.matches(Regex("[A-Za-z0-9_-]+")))
                    val page = YouTube.playlist(playlistId).completed().getOrThrow()
                    require(page.songs.isNotEmpty() && page.songs.size <= 2000)
                    DeeplistSharedPlaylist(
                        page.playlist.title,
                        page.songs.map { song ->
                            DeeplistImportTrack(
                                title = song.title,
                                artist = song.artists.joinToString { it.name },
                                id = song.id,
                                duration = song.duration ?: -1,
                                thumbnail = song.thumbnail,
                            )
                        },
                    )
                }
                else -> DeeplistSharedPlaylist(name, DeeplistPlaylistTransfer.songList(input))
            }
        } }.onSuccess { parsed ->
            if (parsed.tracks.isEmpty()) {
                error = true
                busy = false
                return@launch
            }
            tracks = parsed.tracks; name = parsed.name
            selected = parsed.tracks.mapIndexedNotNull { i, t -> if (t.id.isNotBlank()) i to t.metadata() else null }.toMap()
            choices = List(tracks.size) { emptyList() }
            for ((i, track) in tracks.withIndex()) {
                if (track.id.isNotBlank()) continue
                val found = withContext(Dispatchers.IO) { YouTube.search("${track.title} ${track.artist}", YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.take(5).orEmpty() }
                choices = choices.toMutableList().apply { set(i, found) }
                // Matches remain visible for review, including unmatched rows and alternative versions.
                if (found.isNotEmpty()) selected = selected + (i to found.first().toMediaMetadata())
            }
        }.onFailure { error = true }
        busy = false
    } }
    LaunchedEffect(initial) { if (initial.startsWith("deeplist://") || initial.startsWith("https://deeplist-playlists.cherrybard2.chatgpt.site/playlist")) preview() }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.deeplist_playlist_import)) }, text = {
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(150) }, label = { Text(stringResource(R.string.deeplist_playlist_name)) }, enabled = !busy)
            if (tracks.isEmpty()) {
                Text(stringResource(R.string.deeplist_import_hint))
                OutlinedTextField(input, { input = it.take(1_000_000) }, modifier = Modifier.heightIn(max = 150.dp), label = { Text(stringResource(R.string.deeplist_playlist_input)) }, enabled = !busy)
                TextButton(onClick = {
                    busy = true
                    runCatching { chooseFile.launch(arrayOf("text/*", "application/octet-stream", "application/json")) }
                        .onFailure { busy = false; error = true }
                }, enabled = !busy) { Text(stringResource(R.string.deeplist_import_csv)) }
                TextButton(onClick = { preview() }, enabled = input.isNotBlank() && !busy) { Text(stringResource(R.string.deeplist_preview_matches)) }
            } else {
                Text(stringResource(R.string.deeplist_matches_count, selected.values.count { it != null }, tracks.size))
                tracks.forEachIndexed { i, track ->
                    val match = selected[i]
                    Text("${i + 1}. ${track.title} — ${track.artist}", style = MaterialTheme.typography.bodyMedium)
                    Text(match?.let { "${it.title} · ${it.artists.joinToString { a -> a.name }}" } ?: stringResource(R.string.deeplist_no_match), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { searching = i; query = "${track.title} ${track.artist}"; candidates = choices.getOrNull(i).orEmpty() }, enabled = !busy) { Text(stringResource(R.string.deeplist_change_match)) }
                        TextButton(onClick = { selected = selected + (i to null) }, enabled = match != null && !busy) { Text(stringResource(R.string.deeplist_skip)) }
                    }
                    HorizontalDivider()
                }
                TextButton(onClick = { tracks = emptyList(); selected = emptyMap() }, enabled = !busy) { Text(stringResource(R.string.deeplist_start_over)) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error) Text(stringResource(R.string.deeplist_import_failed), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { scope.launch {
        busy = true; error = false
        runCatching { withContext(Dispatchers.IO) {
            val playlist = PlaylistEntity(name = name.trim(), isLocal = true, bookmarkedAt = java.time.LocalDateTime.now())
            database.withTransaction {
                insert(playlist)
                tracks.indices.mapNotNull { selected[it] }.forEachIndexed { index, item -> insert(item); insert(PlaylistSongMap(songId = item.id, playlistId = playlist.id, position = index)) }
            }
            playlist.id
        } }.onSuccess { onSaved(it); onDismiss() }.onFailure { error = true }
        busy = false
    } }, enabled = !busy && tracks.isNotEmpty() && tracks.indices.any { selected[it] != null } && name.isNotBlank()) { Text(stringResource(R.string.deeplist_save_playlist)) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } })
    searching?.let { index -> AlertDialog(onDismissRequest = { searching = null }, title = { Text(stringResource(R.string.deeplist_change_match)) }, text = {
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(query, { query = it.take(250) })
            TextButton(onClick = { scope.launch { busy = true; candidates = withContext(Dispatchers.IO) { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.take(10).orEmpty() }; busy = false } }, enabled = !busy) { Text(stringResource(R.string.search)) }
            candidates.forEach { candidate -> TextButton(onClick = { selected = selected + (index to candidate.toMediaMetadata()); searching = null }) { Text("${candidate.title} · ${candidate.artists.joinToString { it.name }}") } }
        }
    }, confirmButton = { TextButton(onClick = { searching = null }) { Text(stringResource(android.R.string.cancel)) } }) }
}
