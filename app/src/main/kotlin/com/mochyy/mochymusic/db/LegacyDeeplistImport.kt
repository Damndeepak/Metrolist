package com.mochyy.mochymusic.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Copies library data into a fresh Mochy database; never opens the source with Room migrations. */
internal object LegacyDeeplistImport {
    sealed interface Result {
        data object NoLegacyData : Result
        data object AlreadyImported : Result
        data object DestinationInUse : Result
        data class Imported(val rows: Int, val omittedTables: List<String>) : Result
    }

    data class State(val completed: Boolean = false, val error: String? = null)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(State())
    val state: kotlinx.coroutines.flow.StateFlow<State> = mutableState

    fun requireImported(context: Context, target: SupportSQLiteDatabase) {
        try {
            check(importIfFresh(context, target) != Result.DestinationInUse) {
                "The new library already contains data. Original Deeplist data is safe; automatic import needs recovery."
            }
            mutableState.value = State(completed = true)
        } catch (error: Exception) {
            mutableState.value = State(error = error.message ?: "Library import could not finish. Original data is safe.")
            throw IllegalStateException("Deeplist library import failed; original data was retained", error)
        }
    }

    private val tables = listOf(
        "artist", "album", "song", "playlist", "song_artist_map", "song_album_map",
        "album_artist_map", "playlist_song_map", "search_history", "lyrics", "event",
        "related_song_map", "playCount", "recognition_history", "speed_dial_item",
    )

    /** Call once before displaying the library, on Dispatchers.IO. Failure leaves source intact. */
    @Synchronized
    fun importIfFresh(context: Context, target: SupportSQLiteDatabase): Result {
        if (hasReceipt(target)) return Result.AlreadyImported
        val file = context.getDatabasePath("song.db")
        if (!file.isFile) return Result.NoLegacyData
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { source ->
            require(source.version == 38) { "Unsupported Deeplist database version: ${source.version}" }
            val available = source.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            val selected = tables.filter { it in available && columns(target, it).isNotEmpty() }
            var count = 0
            // Android may start a write transaction for SQL BEGIN even on a read-only connection.
            target.beginTransaction()
            try {
                // Check while holding the write transaction so a background library write cannot race import.
                if (selected.any { table -> target.query("SELECT 1 FROM ${quoted(table)} LIMIT 1").use { it.moveToFirst() } }) {
                    return Result.DestinationInUse
                }
                target.execSQL("PRAGMA defer_foreign_keys=ON")
                for (table in selected) {
                    val destinationColumns = columns(target, table)
                    source.rawQuery("SELECT * FROM ${quoted(table)}", null).use { rows ->
                        val shared = rows.columnNames.withIndex().filter { it.value in destinationColumns }
                        require(shared.isNotEmpty()) { "No compatible columns for $table" }
                        while (rows.moveToNext()) {
                            val values = ContentValues()
                            for ((index, name) in shared) {
                                // Cache state, expiring URLs and service tokens are deliberately not migrated.
                                if (name in setOf("isDownloaded", "isCached")) values.put(name, 0)
                                else if (name in setOf("dateDownload", "libraryAddToken", "libraryRemoveToken")) values.putNull(name)
                                else put(values, name, rows, index)
                            }
                            target.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
                            count++
                        }
                    }
                }
                target.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Imported library has unresolved references" } }
                // Receipt and library commit together, including if the process dies immediately after commit.
                target.execSQL("CREATE TABLE IF NOT EXISTS deeplist_legacy_import (id INTEGER NOT NULL PRIMARY KEY, source_version INTEGER NOT NULL, imported_rows INTEGER NOT NULL)")
                target.execSQL("INSERT INTO deeplist_legacy_import (id, source_version, imported_rows) VALUES (1, 38, ?)", arrayOf(count))
                target.setTransactionSuccessful()
            } finally { target.endTransaction() }
            return Result.Imported(count, (available - selected.toSet()).filterNot { it.startsWith("sqlite_") || it == "room_master_table" }.sorted())
        }
    }

    private fun hasReceipt(db: SupportSQLiteDatabase): Boolean {
        val exists = db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='deeplist_legacy_import'").use { it.moveToFirst() }
        return exists && db.query("SELECT 1 FROM deeplist_legacy_import WHERE id=1 AND source_version=38").use { it.moveToFirst() }
    }

    private fun quoted(name: String) = "\"${name.replace("\"", "\"\"")}\""
    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(${quoted(table)})").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private fun put(values: ContentValues, name: String, cursor: Cursor, index: Int) {
        when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> values.putNull(name)
            Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(index))
            Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(index))
            else -> values.put(name, cursor.getString(index))
        }
    }
}
