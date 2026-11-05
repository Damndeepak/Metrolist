// LocalFolders.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import timber.log.Timber
import java.io.File

/** music folders the user picked themselves (like salt player), picking one keeps access to everything inside it so mochy can read those songs without seeing all the music on the phone, re-scan reads them again */
object LocalFolders {
    private const val FILE_NAME = "local_music_folders.txt"
    private const val MAX_FILES = 5_000
    private val lock = Any()

    private val AudioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "alac", "aiff", "aif", "ape", "mka")

    fun read(context: Context): List<Uri> = synchronized(lock) {
        runCatching {
            file(context).takeIf { it.exists() }?.readLines()
                ?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { it.toUri() }
        }.getOrNull().orEmpty()
    }

    /** keeps access to tree for good and remembers it for the next re-scan */
    fun add(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        synchronized(lock) {
            val folders = (read(context) + tree).distinct()
            runCatching { file(context).writeText(folders.joinToString("\n")) }
        }
    }

    /** audio files in tree (and folders inside it) that aren't in the library yet, known are the library's file uris, on android 10+ each file's MediaStore uri is checked too since the phone-wide scan stores another uri */
    fun newAudioFiles(context: Context, tree: Uri, known: Set<String>): List<Uri> {
        val files = audioFiles(context, tree)
        val checkMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            known.any { it.startsWith("content://media/") }
        return files.filter { uri ->
            if (uri.toString() in known) return@filter false
            !checkMediaStore || mediaStoreUri(context, uri)?.toString() !in known
        }
    }

    /** the MediaStore uri of a file picked through a folder, or null if MediaStore doesn't have it */
    fun mediaStoreUri(context: Context, document: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { MediaStore.getMediaUri(context, document) }.getOrNull()
    }

    /** every audio file under tree, folders inside it included. hidden folders are skipped */
    private fun audioFiles(context: Context, tree: Uri): List<Uri> {
        val found = ArrayList<Uri>()
        val pending = ArrayDeque<String>()
        pending += runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return found
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        while (pending.isNotEmpty() && found.size < MAX_FILES) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeAt(pending.lastIndex))
            try {
                context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        val mime = cursor.getString(1).orEmpty()
                        val name = cursor.getString(2).orEmpty()
                        if (name.startsWith(".")) continue
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending += id
                            mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AudioExtensions ->
                                found += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        }
                    }
                }
            } catch (e: Exception) {
                // a folder that was deleted or lost its access: skip it, keep the rest
                Timber.w(e, "Couldn't read a music folder")
            }
        }
        return found
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
