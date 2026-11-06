/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.constants.SongSortTypeKey
import com.metrolist.music.constants.SongSortDescendingKey
import com.metrolist.music.utils.rememberPreference
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalNavController
import com.metrolist.music.R
import com.metrolist.music.constants.AlbumViewTypeKey
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.constants.LibraryViewType
import com.metrolist.music.constants.PlaylistViewTypeKey
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.menu.DeeplistPlaylistImportDialog
import com.metrolist.music.utils.rememberEnumPreference

@Composable
fun LibraryScreen() {
    val navController = LocalNavController.current
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    var libraryViewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    var playlistViewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)

    var songSort by rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    var songDescending by rememberPreference(SongSortDescendingKey, true)
    var importLink by rememberSaveable { mutableStateOf<String?>(null) }
    importLink?.let { link ->
        DeeplistPlaylistImportDialog(initial = link, onDismiss = { importLink = null })
    }

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips = listOf(
                    LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                    LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                    LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                    LibraryFilter.PODCASTS to stringResource(R.string.filter_podcasts),
                ),
                currentValue = filterType,
                onValueUpdate = {
                    filterType = if (filterType == it) LibraryFilter.LIBRARY else it
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(
                navController = navController,
                filterContent = {
                    Column {
                        filterContent()
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { importLink = "https://open.spotify.com/playlist/" }) {
                                Icon(painterResource(R.drawable.import_spotify), null, Modifier.size(20.dp))
                                Text(stringResource(R.string.deeplist_import_spotify), Modifier.padding(start = 8.dp))
                            }
                            FilledTonalButton(onClick = { importLink = "https://music.youtube.com/playlist?list=" }) {
                                Icon(painterResource(R.drawable.import_youtube_music), null, Modifier.size(20.dp))
                                Text(stringResource(R.string.deeplist_import_youtube_music), Modifier.padding(start = 8.dp))
                            }
                        }
                        LibraryCategoryCards(
                            onRecent = {
                                songSort = SongSortType.CREATE_DATE
                                songDescending = true
                                filterType = LibraryFilter.SONGS
                            },
                            onSelect = { filterType = it },
                            onLiked = { navController.navigate("auto_playlist/liked") },
                        )
                    }
                },
                viewType = libraryViewType,
                onViewTypeChange = { libraryViewType = it },
            )
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = playlistViewType,
                onViewTypeChange = { playlistViewType = it },
            )
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.PODCASTS -> LibraryPodcastsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
        }
    }
}


@Composable
private fun LibraryCategoryCards(
    onRecent: () -> Unit,
    onSelect: (LibraryFilter) -> Unit,
    onLiked: () -> Unit,
) {
    val database = LocalDatabase.current
    val songs by remember(database) { database.songsByCreateDateAsc() }.collectAsStateWithLifecycle(emptyList())
    val playlists by remember(database) { database.playlistsByCreateDateAsc() }.collectAsStateWithLifecycle(emptyList())
    val albums by remember(database) { database.albumsByCreateDateAsc() }.collectAsStateWithLifecycle(emptyList())
    val artists by remember(database) { database.artistsByCreateDateAsc() }.collectAsStateWithLifecycle(emptyList())
    val liked by remember(database) { database.likedSongsByCreateDateAsc() }.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryCategoryCard(R.string.filter_songs, songs.size, songs.lastOrNull()?.thumbnailUrl, Modifier.weight(1f)) {
                onSelect(LibraryFilter.SONGS)
            }
            LibraryCategoryCard(R.string.filter_playlists, playlists.size, playlists.asReversed().firstNotNullOfOrNull { it.thumbnails.firstOrNull() }, Modifier.weight(1f)) {
                onSelect(LibraryFilter.PLAYLISTS)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryCategoryCard(R.string.filter_albums, albums.size, albums.lastOrNull()?.thumbnailUrl, Modifier.weight(1f)) {
                onSelect(LibraryFilter.ALBUMS)
            }
            LibraryCategoryCard(R.string.filter_artists, artists.size, artists.lastOrNull()?.thumbnailUrl, Modifier.weight(1f)) {
                onSelect(LibraryFilter.ARTISTS)
            }
        }
        LibraryCategoryCard(R.string.deeplist_recently_added, songs.size, songs.lastOrNull()?.thumbnailUrl, Modifier.fillMaxWidth(), onRecent)
        LibraryCategoryCard(R.string.liked, liked.size, liked.lastOrNull()?.thumbnailUrl, Modifier.fillMaxWidth(), onLiked)
    }
}

@Composable
private fun LibraryCategoryCard(
    label: Int,
    count: Int,
    artwork: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            }
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
