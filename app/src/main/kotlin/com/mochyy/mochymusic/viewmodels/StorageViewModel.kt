// StorageViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import coil3.SingletonImageLoader
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.playback.CachedSongs
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.playback.ExoDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** How much room Mochy takes, in the four parts the Storage page shows, and the phone around it. */
data class StorageUsage(
    val deviceTotal: Long,
    val deviceFree: Long,
    /** songs streamed and kept for next time, cover pictures, and other throwaway files */
    val cache: Long,
    /** downloaded songs and the covers saved for local files */
    val downloads: Long,
    val database: Long,
    /** the app itself and its settings, everything not counted above */
    val appData: Long,
) {
    val mochy: Long get() = cache + downloads + database + appData
    val othersUsed: Long get() = (deviceTotal - deviceFree - mochy).coerceAtLeast(0L)
}

/** A finished download, with what it costs on the phone. */
data class DownloadedSong(
    val song: Song,
    val bytes: Long,
    /** when the download finished, ms since 1970 */
    val downloadedAt: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val cachedSongs: CachedSongs,
    private val downloadUtil: DownloadUtil,
) : ViewModel() {

    private val _usage = MutableStateFlow<StorageUsage?>(null)
    val usage: StateFlow<StorageUsage?> = _usage.asStateFlow()

    /** True while something is being deleted, so the buttons can't be tapped twice. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Every finished download, newest first; the sheet sorts it however it likes. */
    val downloads: StateFlow<List<DownloadedSong>?> = downloadUtil.downloads
        .map { all -> all.values.filter { it.state == Download.STATE_COMPLETED } }
        .distinctUntilChanged { old, new -> old.map { it.request.id } == new.map { it.request.id } }
        .mapLatest { done ->
            val byId = done.associateBy { it.request.id }
            val songs = if (byId.isEmpty()) emptyList() else database.getSongsByIds(byId.keys.toList())
            songs.mapNotNull { song ->
                val download = byId[song.id] ?: return@mapNotNull null
                DownloadedSong(song, download.bytesDownloaded.coerceAtLeast(0L), download.updateTimeMs)
            }.sortedByDescending { it.downloadedAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Walks Mochy's folders again. The page calls it on open and whenever the downloads change. */
    fun measure() {
        viewModelScope.launch { _usage.value = withContext(Dispatchers.IO) { readUsage() } }
    }

    /** Songs kept from streaming and every cover picture. Downloads and the library stay. */
    fun clearCaches() = runBusy {
        cachedSongs.clear().join()
        SingletonImageLoader.get(context).let { loader ->
            loader.memoryCache?.clear()
            withContext(Dispatchers.IO) { loader.diskCache?.clear() }
        }
    }

    fun removeAllDownloads() = runBusy {
        DownloadService.sendRemoveAllDownloads(context, ExoDownloadService::class.java, false)
        // the service deletes in the background; give it a moment before measuring again
        delay(1_200)
    }

    fun removeDownloads(songIds: Collection<String>) = runBusy {
        songIds.forEach { DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, it, false) }
        delay(800)
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _usage.value = withContext(Dispatchers.IO) { readUsage() }
                _busy.value = false
            }
        }
    }

    private fun readUsage(): StorageUsage {
        val stat = StatFs(Environment.getDataDirectory().path)
        val dataDir = context.dataDir
        val songCache = context.filesDir.resolve("exoplayer")
        val downloadDir = context.filesDir.resolve("download")
        val localArtwork = context.filesDir.resolve("local_artwork")
        val databases = context.getDatabasePath("x").parentFile

        val cache = sizeOf(songCache) + sizeOf(context.cacheDir) + sizeOf(context.codeCacheDir)
        val downloads = sizeOf(downloadDir) + sizeOf(localArtwork)
        val db = sizeOf(databases)
        // the data folder holds all of the above too, so they come off before the rest is counted
        val rest = (sizeOf(dataDir) - cache - downloads - db).coerceAtLeast(0L)
        val apk = context.applicationInfo.run {
            (listOf(sourceDir) + splitSourceDirs.orEmpty()).sumOf { File(it).length() }
        }
        return StorageUsage(
            deviceTotal = stat.totalBytes,
            deviceFree = stat.availableBytes,
            cache = cache,
            downloads = downloads,
            database = db,
            appData = rest + apk,
        )
    }

    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        return runCatching { file.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
    }
}
