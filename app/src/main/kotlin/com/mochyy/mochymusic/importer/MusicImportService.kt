// MusicImportService.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ImportSession
import com.mochyy.mochymusic.extensions.isUserLoggedIn
import com.mochyy.mochymusic.importer.account.AccountImportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Timber.e(throwable, "Music import coroutine exception")
        }
    }
    private val importJob = SupervisorJob()
    private val importScope = CoroutineScope(Dispatchers.IO + importJob + exceptionHandler)
    private var runningJob: Job? = null

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    /** the work itself; one import or re-sync runs at a time, so they share it */
    private val steps = ImportSteps(context, database, _progress)

    /** re-syncs waiting behind the one running, and whether a re-sync run is going (guarded by the queue) */
    private val resyncQueue = ArrayDeque<ResyncRequest>()
    private var resyncing = false

    val isRunning: Boolean get() = runningJob?.isActive == true

    /** starts an import in the background; false if one is already running */
    fun startImport(parsed: ParsedImport, options: ImportOptions = ImportOptions()): Boolean {
        if (isRunning) return false
        steps.source = parsed.provider ?: ImportSource.OTHER
        _progress.value = ImportProgress(isRunning = true, totalTracks = parsed.totalSongs, source = parsed.provider)
        runningJob = importScope.launch {
            try {
                steps.sessionId = database.insert(ImportSession(source = steps.source.key, startedAt = LocalDateTime.now()))
                steps.runImport(parsed, options)
                _progress.update { it.copy(isRunning = false, isDone = true, currentLabel = "") }
                val done = _progress.value
                ImportNotices.importFinished(context, done.matchedTracks, done.totalTracks)
            } catch (e: CancellationException) {
                _progress.update { it.copy(isRunning = false, isDone = true, cancelled = true, currentLabel = "") }
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Import failed")
                _progress.update {
                    it.copy(isRunning = false, isDone = true, currentLabel = "", error = e.message ?: "Import failed")
                }
            } finally {
                steps.sessionId?.let { database.deleteImportSessionIfEmpty(it) }
                steps.sessionId = null
            }
        }
        return true
    }

    /** re-reads imported playlists and adds songs new on the service to their end, ones the user removed here stay out, requests during a run join it, false while an import runs */
    fun startResync(requests: List<ResyncRequest>): Boolean {
        val first = requests.firstOrNull() ?: return true
        synchronized(resyncQueue) {
            if (resyncing) {
                resyncQueue.addAll(requests)
                _progress.update { it.copy(resync = it.resync?.copy(playlists = it.resync.playlists + requests.size)) }
                return true
            }
            if (isRunning) return false
            resyncQueue.addAll(requests)
            resyncing = true
        }
        _progress.value = ImportProgress(isRunning = true, source = first.source, resync = ResyncSummary(first.playlistName, playlists = requests.size))
        runningJob = importScope.launch {
            try {
                while (true) {
                    val next = synchronized(resyncQueue) {
                        resyncQueue.removeFirstOrNull().also { if (it == null) resyncing = false }
                    } ?: break
                    resyncOne(next)
                }
                _progress.update { it.copy(isRunning = false, isDone = true, currentLabel = "") }
                ImportNotices.resyncFinished(context, _progress.value)
            } catch (e: CancellationException) {
                synchronized(resyncQueue) {
                    resyncQueue.clear()
                    resyncing = false
                }
                _progress.update { it.copy(isRunning = false, isDone = true, cancelled = true, currentLabel = "") }
                throw e
            }
        }
        return true
    }

    /** one playlist of a re-sync run. a playlist that fails is noted and the run goes on */
    private suspend fun resyncOne(request: ResyncRequest) {
        steps.source = request.source
        _progress.update {
            it.copy(
                source = request.source,
                currentLabel = "Reading \"${request.playlistName}\" on ${request.source.label}",
                resync = it.resync?.copy(playlistName = request.playlistName),
            )
        }
        var ownSession: Long? = null
        try {
            val read = request.read()
            val playlist = database.playlist(request.playlistId).first()?.playlist
                ?: throw LinkImportException("That playlist isn't in your library any more.")
            // new songs go in under the import that brought the playlist so undoing it takes them too, a playlist copied from a youtube page never put its songs in the library
            val owner = database.getImportedPlaylist(playlist.id)?.sessionId
            if (owner != null) {
                steps.sessionId = owner.takeIf { database.importSessionExists(it) }
                    ?: database.insert(ImportSession(source = steps.source.key, startedAt = LocalDateTime.now())).also { ownSession = it }
            }
            val fresh = steps.record.newTracks(playlist.id, read.tracks)
            _progress.update { it.copy(totalTracks = it.totalTracks + fresh.size) }
            val added = steps.topUp(steps.newMatcher(), playlist, fresh, context.isUserLoggedIn(), intoLibrary = owner != null)
            steps.record.remember(steps.source, playlist.id, read.tracks, read.origin)
            _progress.update { progress ->
                progress.copy(resync = progress.resync?.let {
                    it.copy(
                        done = it.done + 1,
                        added = it.added + added.size,
                        playlistsChanged = it.playlistsChanged + if (added.isEmpty()) 0 else 1,
                        note = it.note ?: read.note,
                    )
                })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Re-sync of ${request.playlistName} failed")
            val reason = when (e) {
                is LinkImportException, is AccountImportException -> e.message
                else -> null
            } ?: "Check your connection and try again."
            _progress.update { progress ->
                progress.copy(
                    error = reason,
                    resync = progress.resync?.let { it.copy(done = it.done + 1, failed = it.failed + request.playlistName) },
                )
            }
        } finally {
            ownSession?.let { database.deleteImportSessionIfEmpty(it) }
            steps.sessionId = null
        }
    }

    /** stops after the current song; everything imported so far stays */
    fun cancel() {
        runningJob?.cancel()
    }

    /** forgets a finished import's summary so the next one starts clean */
    fun clearFinished() {
        if (!isRunning) _progress.value = ImportProgress()
    }

    /** early imports made a playlist named after the service and never filled the library, file their songs into the library once and keep the playlist since it may have been edited */
    suspend fun repairEarlierImports() {
        if (isRunning) return
        val legacy = database.playlistsByNameAsc().first()
            .filter { it.playlist.isEditable && it.playlist.browseId == null && it.playlist.name in LegacyLibraryPlaylists }
        for (playlist in legacy) {
            val songIds = database.playlistSongs(playlist.id).first().map { it.song.id }
            if (songIds.isEmpty()) continue
            val tagged = database.importedSongIds().toSet()
            val missing = songIds.filter { id ->
                id !in tagged || database.getSongById(id)?.song?.inLibrary == null
            }
            if (missing.isEmpty()) continue
            steps.source = LegacyLibraryPlaylists.getValue(playlist.playlist.name)
            steps.fileIntoLibrary(missing)
        }
        steps.source = ImportSource.OTHER
    }

    private companion object {
        /** what earlier versions named the playlist they made of a service's whole library */
        val LegacyLibraryPlaylists = mapOf("Apple Music library" to ImportSource.APPLE_MUSIC)
    }
}
