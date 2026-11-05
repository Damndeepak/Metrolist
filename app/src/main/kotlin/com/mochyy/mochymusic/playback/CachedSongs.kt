// CachedSongs.kt
//
// What the Library's Cached tab lists: the songs the player kept whole in its cache while
// streaming them, so they play again without a connection.

package com.mochyy.mochymusic.playback

import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.di.PlayerCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and imported files are left out on purpose: they are the Local tab, and keeping the
 * two apart means no song shows up on both.
 */
@Singleton
class CachedSongs
@Inject
constructor(
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bumped whenever we delete from the cache ourselves, so the list updates straight away. */
    private val edits = MutableStateFlow(0)

    /**
     * What the cache holds for each song. SimpleCache can't tell anyone when it changes, so this
     * looks again every couple of seconds, and only while somebody is watching.
     */
    private val snapshots: SharedFlow<Map<String, CacheEntry>> = flow {
        while (true) {
            val seen = edits.value
            emit(readCache())
            withTimeoutOrNull(PollMillis) { edits.first { it != seen } }
        }
    }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    /**
     * The songs cached whole, most recently played first. The database is only asked again when
     * the set of songs in the cache changes, not on every look.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: SharedFlow<List<Song>> = snapshots
        .map { it.keys }
        .distinctUntilChanged()
        .flatMapLatest(::songsWithIds)
        .combine(snapshots) { songs, snapshot -> wholeSongs(songs, snapshot) }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Deletes these songs from the cache. They stream again the next time they play. */
    fun remove(songIds: Collection<String>) {
        scope.launch { delete(songIds) }
    }

    /** Empties the cache, songs only partly cached too. Downloads are kept in their own cache and stay. */
    fun clear(): Job = scope.launch { delete(playerCache.keys) }

    private fun delete(keys: Collection<String>) {
        keys.forEach { runCatching { playerCache.removeResource(it) } }
        edits.update { it + 1 }
    }

    private fun readCache(): Map<String, CacheEntry> = runCatching {
        playerCache.keys.mapNotNull { key ->
            val spans = playerCache.getCachedSpans(key)
            if (spans.isEmpty()) return@mapNotNull null
            key to CacheEntry(
                length = ContentMetadata.getContentLength(playerCache.getContentMetadata(key)),
                cachedBytes = spans.sumOf { it.length },
                lastUsed = spans.maxOf { it.lastTouchTimestamp },
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun songsWithIds(ids: Set<String>): Flow<List<Song>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        // under the 999 bound parameters older SQLite builds allow in one statement
        return combine(ids.chunked(900).map(database::songsByIds)) { parts -> parts.flatMap { it } }
    }

    private fun wholeSongs(songs: List<Song>, snapshot: Map<String, CacheEntry>): List<Song> =
        songs
            .filter { !it.song.isDownloaded && !it.song.isLocal }
            .mapNotNull { song -> snapshot[song.id]?.takeIf { isWhole(song, it) }?.let { song to it.lastUsed } }
            .sortedByDescending { it.second }
            .map { it.first }

    /**
     * The player streams in short ranges, so the cache often never learns how long a song is. The
     * length saved with the song's format fills in, the same way playback checks it.
     */
    private fun isWhole(song: Song, entry: CacheEntry): Boolean {
        val length = entry.length.takeIf { it > 0 } ?: song.format?.contentLength ?: return false
        return entry.cachedBytes >= length &&
            runCatching { playerCache.isCached(song.id, 0, length) }.getOrDefault(false)
    }

    /**
     * One song's place in the cache. [cachedBytes] grows while it streams, which is what makes a
     * song show up as soon as its last byte lands. [lastUsed] is when it was last played or cached.
     */
    private data class CacheEntry(val length: Long, val cachedBytes: Long, val lastUsed: Long)

    private companion object {
        const val PollMillis = 2_000L
    }
}
