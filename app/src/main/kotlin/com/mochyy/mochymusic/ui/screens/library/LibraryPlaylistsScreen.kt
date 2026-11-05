// LibraryPlaylistsScreen.kt

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.menu.PlaylistMenu
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import com.mochyy.mochymusic.viewmodels.LibraryShelfKind

/**
 * The library's playlists, or with a [shelf] the Local or Cached tab's: only the playlists that
 * have one of that tab's songs in them. With [source], only the playlists imported from that service.
 */
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
    source: ImportSource? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val all by (shelf?.let(viewModel::shelf)?.playlists ?: viewModel.playlists).collectAsStateWithLifecycle()
    val playlistSources by viewModel.playlistSources.collectAsState()
    val playlists = remember(all, playlistSources, source) {
        if (source == null) all else all.filter { playlistSources[it.id] == source }
    }
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    LibraryEntityListScreen(
        title = "Playlist",
        subtitle = when {
            source != null -> "Playlists imported from ${source.label}."
            shelf == LibraryShelfKind.Local -> "Playlists with music on this device."
            shelf == LibraryShelfKind.Cached -> "Playlists with songs in your cache."
            else -> "All playlist on your library listed here."
        },
        searchPlaceholder = "Find a playlist by its name",
        items = playlists,
        idOf = { it.id },
        nameOf = { it.playlist.name },
        subtitleOf = { "${it.songCount} songs" },
        thumbnailOf = { it.thumbnails.firstOrNull() },
        // the same key Home's cards use, so the playlist opens growing out of its cover
        sharedElementKeyOf = { "playlist-${it.id}" },
        onClick = { playlist -> navController.navigate("local_playlist/${playlist.id}") },
        onLongClick = { playlist ->
            menuState.show {
                PlaylistMenu(
                    playlist = playlist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    navController = navController,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
