package com.mochyy.mochymusic.core.updater.downloadmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mochyy.mochymusic.core.updater.DeeplistApkUrl
import com.mochyy.mochymusic.core.updater.downloadApk
import com.mochyy.mochymusic.core.updater.getLatestRelease
import kotlinx.coroutines.CancellationException

class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            require(inputData.getString("apk_url") == DeeplistApkUrl)
            val release = requireNotNull(getLatestRelease(force = true).getOrThrow())
            DownloadNotificationManager.showDownloadStarting(release.version, inputData.getString("file_size").orEmpty())
            val file = downloadApk(context, release) { progress ->
                DownloadNotificationManager.updateDownloadProgress((progress * 100).toInt(), release.version)
                setProgressAsync(workDataOf("progress" to progress))
            }.getOrThrow()
            DownloadNotificationManager.showDownloadComplete(release.version, file.absolutePath)
            Result.success(workDataOf("file_path" to file.absolutePath))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            DownloadNotificationManager.showDownloadFailed(inputData.getString("version").orEmpty(), e.message ?: "Update verification failed")
            Result.failure()
        }
    }
}
