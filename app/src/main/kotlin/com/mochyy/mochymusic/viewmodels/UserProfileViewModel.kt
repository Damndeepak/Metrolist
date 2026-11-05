// UserProfileViewModel.kt
//
// What the profile page shows: listening totals, what you've been playing, your playlists and the
// artists you play most. All of it comes from the local library and play history.

package com.mochyy.mochymusic.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.screens.recap.toStoredMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** One "Total listens" card: how many different songs, and for how long, over [label]'s span. */
@Immutable
data class ListenTotal(val label: String, val songs: Int, val playTimeMs: Long) {
    companion object {
        /** Shown until the first count arrives, so the numbers roll up from zero instead of popping in. */
        val Placeholder = listOf(ListenTotal("ALL TIME", 0, 0L), ListenTotal("THIS MONTH", 0, 0L), ListenTotal("THIS WEEK", 0, 0L))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    val recentSongs: StateFlow<List<Song>?> = database.recentlyPlayedSongs(limit = 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Bookmarked playlists, the most recently changed first. */
    val playlists: StateFlow<List<Playlist>?> = database.playlistsByUpdatedDateAsc()
        .map { it.asReversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val likedCount: StateFlow<Int> = database.likedSongsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val topArtists: StateFlow<List<Artist>?> = database.mostPlayedArtists(fromTimeStamp = 0L, limit = 12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // the total play time changes with every logged play, which is exactly when these go stale
    val listens: StateFlow<List<ListenTotal>?> = database.totalListeningTimeMs()
        .distinctUntilChanged()
        .mapLatest { countListens() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Keeps every list above loaded for as long as the caller stays collecting, so the profile page
     * has them the moment it opens instead of filling in while it animates.
     */
    suspend fun keepWarm() {
        merge(recentSongs, playlists, likedCount, topArtists, listens).collect { }
    }

    private suspend fun countListens(): List<ListenTotal> {
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1).toStoredMillis()
        val weekStart = today.with(DayOfWeek.MONDAY).toStoredMillis()
        // one read of the whole history; the month and the week are slices of it
        val events = database.recapEvents(0L, today.plusDays(1).toStoredMillis())

        fun total(label: String, from: Long): ListenTotal {
            val songs = HashSet<String>()
            var ms = 0L
            for (e in events) {
                if (e.timestamp < from) continue
                songs += e.songId
                ms += e.playTime.coerceAtLeast(0L)
            }
            return ListenTotal(label, songs.size, ms)
        }

        return listOf(total("ALL TIME", 0L), total("THIS MONTH", monthStart), total("THIS WEEK", weekStart))
    }
}
