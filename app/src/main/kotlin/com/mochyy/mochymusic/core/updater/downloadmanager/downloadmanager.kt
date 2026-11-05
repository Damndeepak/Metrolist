package com.mochyy.mochymusic.core.updater.downloadmanager

import android.content.Context
import com.mochyy.mochymusic.core.updater.DeeplistApkUrl
import com.mochyy.mochymusic.core.updater.downloadApk as downloadVerifiedApk
import com.mochyy.mochymusic.core.updater.getLatestRelease
import kotlinx.coroutines.*
import java.io.File

class CustomDownloadManager {
    private var downloadJob: Job? = null
    fun downloadApk(context: Context, apkUrl: String, onProgress: (Float) -> Unit,
        onDownloadComplete: (File) -> Unit, onError: (String) -> Unit) {
        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                require(apkUrl == DeeplistApkUrl)
                val release = requireNotNull(getLatestRelease(force = true).getOrThrow())
                val file = downloadVerifiedApk(context, release, onProgress).getOrThrow()
                withContext(Dispatchers.Main) { onDownloadComplete(file) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "Update verification failed") } }
        }
    }
    fun pauseDownload() { downloadJob?.cancel() }
    fun cancelDownload() { downloadJob?.cancel(); downloadJob = null }
}
