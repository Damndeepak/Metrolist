package com.mochyy.mochymusic.core.updater

import android.content.Context
import java.io.File

fun getDownloadedApksDir(context: Context): File = context.cacheDir

private fun downloadedApks(context: Context): List<File> =
    getDownloadedApksDir(context).listFiles { file ->
        file.isFile && file.name.matches(Regex("Deeplist-[0-9]+\\.apk"))
    }?.toList().orEmpty()

fun getDownloadedApkCount(context: Context): Int = downloadedApks(context).size

private fun deleteApk(file: File): Boolean {
    val deleted = file.delete()
    File(file.parentFile, file.name + ".json").delete()
    return deleted
}

fun clearDownloadedApks(context: Context): Boolean {
    var success = true
    downloadedApks(context).forEach { if (!deleteApk(it)) success = false }
    return success
}

fun autoClearOldApks(context: Context) {
    val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    downloadedApks(context).filter { it.lastModified() < oneDayAgo }.forEach { deleteApk(it) }
}
