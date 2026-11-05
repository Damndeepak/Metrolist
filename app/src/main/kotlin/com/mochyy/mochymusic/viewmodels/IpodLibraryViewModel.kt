package com.mochyy.mochymusic.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.mochyy.mochymusic.constants.AlbumSortType
import com.mochyy.mochymusic.constants.ArtistSongSortType
import com.mochyy.mochymusic.constants.ArtistSortType
import com.mochyy.mochymusic.constants.PlaylistSortType
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.ui.player.ipod.IpodList
import com.mochyy.mochymusic.ui.player.ipod.IpodMainList
import com.mochyy.mochymusic.ui.player.ipod.IpodMusicList
import com.mochyy.mochymusic.ui.player.ipod.IpodPage
import com.mochyy.mochymusic.ui.player.ipod.IpodPick
import com.mochyy.mochymusic.ui.player.ipod.IpodRow
import com.mochyy.mochymusic.ui.utils.resize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

// one album as cover flow shows it
@Immutable
class IpodAlbum(val id: String, val title: String, val artist: String, val cover: String?)

// the library as the ipod player's menus see it; warmed in the background so opening a menu doesn't wait on the database
@HiltViewModel
class IpodLibraryViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    private val cache = ConcurrentHashMap<IpodPage, IpodList>().apply {
        put(IpodPage.Main, IpodMainList)
        put(IpodPage.Music, IpodMusicList)
    }

    @Volatile
    var cachedCoverFlow: List<IpodAlbum>? = null
        private set

    @Volatile
    var cachedCovers: List<String>? = null
        private set

    init {
        // warm the menus and playlists off the main thread as soon as the style is up
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { covers() }
            runCatching { coverFlow() }
            val playlists = runCatching { load(IpodPage.Playlists) }.getOrNull()
            runCatching { load(IpodPage.Artists) }
            runCatching { load(IpodPage.Albums) }
            runCatching { load(IpodPage.LikedSongs) }
            runCatching { load(IpodPage.Songs) }
            playlists?.rows?.forEach { row ->
                val open = row.pick as? IpodPick.Open ?: return@forEach
                runCatching { load(open.page) }
            }
        }
    }

    fun cached(page: IpodPage): IpodList? = cache[page]

    // reads a page once and keeps it in memory so opening it a second time or after warming is instant
    suspend fun load(page: IpodPage): IpodList = withContext(Dispatchers.Default) {
        cache[page]?.let { return@withContext it }
        buildList(page).also { cache[page] = it }
    }

    // refreshes a cached page quietly in the background after the slide has already started
    fun refreshAsync(page: IpodPage) {
        if (page == IpodPage.Main || page == IpodPage.Music || page == IpodPage.NowPlaying || page == IpodPage.CoverFlow) return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { cache[page] = buildList(page) }
        }
    }

    private suspend fun buildList(page: IpodPage): IpodList = when (page) {
        IpodPage.Main -> IpodMainList
        IpodPage.Music -> IpodMusicList
        IpodPage.NowPlaying, IpodPage.CoverFlow -> IpodList.Loading
        IpodPage.Playlists -> withArt(
            database.playlists(PlaylistSortType.NAME, false).first().map {
                IpodRow(
                    key = it.id,
                    title = it.playlist.name,
                    pick = IpodPick.Open(IpodPage.Playlist(it.id, it.playlist.name)),
                    subtitle = songCount(it.songCount),
                    art = it.thumbnails.firstOrNull()?.resize(RowPx, RowPx),
                )
            },
            "No playlists yet",
        )
        IpodPage.Artists -> withArt(
            database.artists(ArtistSortType.NAME, false).first().map {
                IpodRow(
                    key = it.id,
                    title = it.artist.name,
                    pick = IpodPick.Open(IpodPage.Artist(it.id, it.artist.name)),
                    subtitle = songCount(it.songCount),
                    art = it.artist.thumbnailUrl?.resize(RowPx, RowPx),
                    roundArt = true,
                )
            },
            "No artists yet",
        )
        IpodPage.Albums -> withArt(
            albums().map {
                IpodRow(
                    key = it.id,
                    title = it.album.title,
                    pick = IpodPick.Open(IpodPage.Album(it.id, it.album.title)),
                    subtitle = it.artists.joinToString { a -> a.name }.ifBlank { null },
                    art = it.album.thumbnailUrl?.resize(RowPx, RowPx),
                )
            },
            "No albums yet",
        )
        IpodPage.Songs -> songs(database.songs(SongSortType.NAME, false).first(), art = true)
        IpodPage.LikedSongs -> songs(database.likedSongs(SongSortType.NAME, false).first(), art = true)
        is IpodPage.Playlist -> songs(database.playlistSongs(page.id).first().map { it.song }, art = true)
        is IpodPage.Artist -> songs(database.artistSongs(page.id, ArtistSongSortType.NAME, false).first(), art = true)
        // an album's own tracks all share its cover, so they're a plain track list like on the classic
        is IpodPage.Album -> albumTracks(page.id)
    }

    // album covers for the slideshow beside the main menu, newest first
    suspend fun covers(): List<String> = withContext(Dispatchers.Default) {
        cachedCovers?.let { return@withContext it }
        database.albums(AlbumSortType.CREATE_DATE, true).first()
            .plus(database.albumsLiked(AlbumSortType.CREATE_DATE, true).first())
            .mapNotNull { it.album.thumbnailUrl?.resize(CoverPx, CoverPx) }
            .distinct()
            .take(MaxCovers)
            .also { cachedCovers = it }
    }

    // every album, saved or with songs in the library, by name, for cover flow
    suspend fun coverFlow(): List<IpodAlbum> = withContext(Dispatchers.Default) {
        cachedCoverFlow?.let { return@withContext it }
        albums()
            .map { IpodAlbum(it.id, it.album.title, it.artists.joinToString { a -> a.name }, it.album.thumbnailUrl?.resize(CoverPx, CoverPx)) }
            .also { cachedCoverFlow = it }
    }

    // the whole library in a random order, ready to hand to the player
    suspend fun shuffledLibrary(): List<MediaItem> = withContext(Dispatchers.Default) {
        database.songs(SongSortType.NAME, false).first().shuffled().map { it.toMediaItem() }
    }

    // the albums you saved plus the ones your library songs come from; the library list alone missed saved albums
    private suspend fun albums(): List<Album> {
        val all = (database.albumsLiked(AlbumSortType.NAME, false).first() + database.albums(AlbumSortType.NAME, false).first())
            .distinctBy { it.id }
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        return all.sortedWith(compareBy(collator) { it.album.title })
    }

    // a saved album whose tracks were never stored gets them from youtube once, the way opening its page does
    private suspend fun albumTracks(id: String): IpodList {
        val stored = database.albumWithSongs(id).first()?.songs.orEmpty()
        if (stored.isNotEmpty()) return songs(stored, art = false)
        val page = withTimeoutOrNull(FetchTimeoutMs) { YouTube.album(id).getOrNull() }
            ?: return IpodList(listOf(IpodRow("empty", "Couldn't load this album, check your connection", null)))
        val existing = database.album(id).first()
        database.withTransaction {
            if (existing == null) insert(page) else update(existing.album, page, existing.artists)
        }
        return songs(database.albumWithSongs(id).first()?.songs.orEmpty(), art = false)
    }

    private fun withArt(rows: List<IpodRow>, emptyText: String) =
        if (rows.isEmpty()) IpodList(listOf(IpodRow("empty", emptyText, null))) else IpodList(rows, art = true)

    private fun songs(songs: List<Song>, art: Boolean): IpodList {
        if (songs.isEmpty()) return IpodList(listOf(IpodRow("empty", "No songs yet", null)))
        val rows = songs.mapIndexed { index, song ->
            IpodRow(
                key = "${song.id}#$index",
                title = song.song.title,
                pick = IpodPick.Play(index),
                subtitle = if (art) song.artists.joinToString { it.name }.ifBlank { null } else null,
                art = if (art) song.song.thumbnailUrl?.resize(RowPx, RowPx) else null,
            )
        }
        return IpodList(rows, songs, art = art)
    }

    private fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

    private companion object {
        const val CoverPx = 400
        const val RowPx = 120
        const val MaxCovers = 24
        const val FetchTimeoutMs = 10_000L
    }
}
