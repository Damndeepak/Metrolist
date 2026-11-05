// LibraryArtistsScreen.kt

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.menu.ArtistMenu
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel
import com.mochyy.mochymusic.viewmodels.LibraryShelfKind

/**
 * The library's artists, or with a [shelf] the Local or Cached tab's: only the artists of that
 * tab's songs, each with how many of its songs are there.
 */
@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val artists by (shelf?.let(viewModel::shelf)?.artists ?: viewModel.artists).collectAsStateWithLifecycle()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    LibraryEntityListScreen(
        title = "Artist",
        subtitle = when (shelf) {
            LibraryShelfKind.Local -> "Artists with music on this device."
            LibraryShelfKind.Cached -> "Artists with songs in your cache."
            null -> "All artist on your library listed here."
        },
        searchPlaceholder = "Find an artist by name",
        items = artists,
        idOf = { it.id },
        nameOf = { it.artist.name },
        subtitleOf = {
            val count = if (it.songCount == 1) "1 song" else "${it.songCount} songs"
            when (shelf) {
                LibraryShelfKind.Local -> "$count on this device"
                LibraryShelfKind.Cached -> "$count cached"
                null -> count
            }
        },
        thumbnailOf = { it.artist.thumbnailUrl },
        roundThumbnails = true,
        onClick = { artist -> navController.navigate("artist/${artist.id}") },
        onLongClick = { artist ->
            menuState.show {
                ArtistMenu(
                    originalArtist = artist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
