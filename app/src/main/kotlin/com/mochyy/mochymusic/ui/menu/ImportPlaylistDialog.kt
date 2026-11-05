// ImportPlaylistDialog.kt

package com.mochyy.mochymusic.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.importer.PlaylistSourceRecord
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportPlaylistDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>,
    playlistTitle: String,
    onDismiss: () -> Unit,
    onImportedPlaylist: (String) -> Unit = {},
    /** The YouTube playlist being copied, so the copy can be re-synced with it later. */
    sourcePlaylistId: String? = null,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    val textFieldValue by remember { mutableStateOf(TextFieldValue(text = playlistTitle)) }
    if (isVisible) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            initialTextFieldValue = textFieldValue,
            autoFocus = false,
            onDismiss = onDismiss,
            onDone = { finalName ->
                val newPlaylist = PlaylistEntity(
                    name = finalName
                )
                coroutineScope.launch {
                    val importedPlaylistId = withContext(Dispatchers.IO) {
                        database.query { insert(newPlaylist) }
                        val playlist = database.playlist(newPlaylist.id).firstOrNull()

                        if (playlist != null) {
                            val importedSongIds = onGetSong()
                            database.addSongToPlaylist(playlist, importedSongIds)
                            sourcePlaylistId?.let {
                                PlaylistSourceRecord(database).rememberYouTubeCopy(newPlaylist.id, it, importedSongIds)
                            }
                        }

                        newPlaylist.id
                    }

                    onDismiss()
                    onImportedPlaylist(importedPlaylistId)
                }
            }
        )
    }
}
