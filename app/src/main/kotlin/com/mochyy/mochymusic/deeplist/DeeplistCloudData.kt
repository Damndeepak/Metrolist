package com.mochyy.mochymusic.deeplist

import android.database.Cursor
import androidx.datastore.preferences.core.*
import com.mochyy.mochymusic.db.MusicDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal object DeeplistCloudData {
    private data class Table(val name: String, val columns: String, val keys: List<String>, val where: String = "") {
        val fields = columns.split(',')
        val quoted get() = fields.joinToString(",") { "`$it`" }
        fun id(row: JSONObject) = name + ":" + MessageDigest.getInstance("SHA-256").digest(keys.joinToString("\u0000") { row.get(it).toString() }.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private val tables = listOf(
        Table("album", "id,playlistId,title,year,thumbnailUrl,themeColor,songCount,duration,explicit,lastUpdateTime,bookmarkedAt,likedDate,inLibrary", listOf("id"), "WHERE isLocal=0 AND isUploaded=0"),
        Table("artist", "id,name,thumbnailUrl,channelId,lastUpdateTime,bookmarkedAt,isPodcastChannel", listOf("id"), "WHERE isLocal=0"),
        Table("song", "id,title,duration,thumbnailUrl,albumId,albumName,explicit,year,liked,likedDate,totalPlayTime,inLibrary,lyricsOffset,romanizeLyrics,isVideo,isEpisode,playbackPosition", listOf("id"), "WHERE isLocal=0 AND isUploaded=0"),
        Table("song_artist_map", "songId,artistId,position", listOf("songId", "artistId"), "WHERE songId IN (SELECT id FROM song WHERE isLocal=0 AND isUploaded=0) AND artistId IN (SELECT id FROM artist WHERE isLocal=0)"),
        Table("song_album_map", "songId,albumId,index", listOf("songId", "albumId"), "WHERE songId IN (SELECT id FROM song WHERE isLocal=0 AND isUploaded=0) AND albumId IN (SELECT id FROM album WHERE isLocal=0 AND isUploaded=0)"),
        Table("album_artist_map", "albumId,artistId,order", listOf("albumId", "artistId"), "WHERE albumId IN (SELECT id FROM album WHERE isLocal=0 AND isUploaded=0) AND artistId IN (SELECT id FROM artist WHERE isLocal=0)"),
        Table("event", "songId,timestamp,playTime", listOf("songId", "timestamp", "playTime"), "WHERE songId IN (SELECT id FROM song WHERE isLocal=0 AND isUploaded=0)"),
        Table("search_history", "query", listOf("query")),
    )
    private val playlist = Table("playlist", "id,name,browseId,createdAt,lastUpdateTime,isEditable,bookmarkedAt,remoteSongCount,thumbnailUrl,isLocal,isAutoSync", listOf("id"), "WHERE id NOT IN ('LP_LIKED','LP_DOWNLOADED','LP_WEEKLY_MOST','LP_MONTHLY_MOST')")
    private fun columns(db: MusicDatabase, table: Table): Set<String> =
        db.openHelper.readableDatabase.query("PRAGMA table_info(`${table.name}`)").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
    private fun rows(db: MusicDatabase, table: Table, preserved: JSONObject?): List<JSONObject> {
        val available = columns(db, table)
        val projection = table.fields.joinToString(",") { if (it in available) "`$it`" else "0 AS `$it`" }
        return db.openHelper.readableDatabase.query("SELECT $projection FROM ${table.name} ${table.where}").use { cursor ->
            buildList { while (cursor.moveToNext()) {
                val row = JSONObject().apply { table.fields.forEachIndexed { i, key -> put(key, when(cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                    else -> cursor.getString(i)
                }) } }
                val prior = preserved?.optJSONObject("records")?.optJSONObject(table.id(row))
                table.fields.filter { it !in available }.forEach { field ->
                    if (prior?.has(field) == true) row.put(field, prior.get(field))
                }
                add(row)
            } }
        }
    }
    fun snapshot(db: MusicDatabase, preserved: JSONObject? = null): JSONObject {
        val records = JSONObject()
        tables.forEach { table -> rows(db, table, preserved).forEach { records.put(table.id(it), it) } }
        rows(db, playlist, preserved).forEach { row ->
            val items = JSONArray()
            db.openHelper.readableDatabase.query("SELECT m.songId FROM playlist_song_map m JOIN song s ON s.id=m.songId WHERE m.playlistId=? AND s.isLocal=0 AND s.isUploaded=0 ORDER BY m.position,m.id", arrayOf(row.getString("id"))).use { c -> while(c.moveToNext()) items.put(c.getString(0)) }
            records.put(playlist.id(row), JSONObject().put("row", row).put("items", items))
        }
        return JSONObject().put("format", "deeplist-cloud").put("version", 1).put("records", records).put("settings", preserved?.optJSONObject("settings") ?: JSONObject())
    }
    fun validate(after: JSONObject) {
        require(after.optJSONObject("settings") != null)
        require(after.optString("format") == "deeplist-cloud" && after.optInt("version") == 1)
        val incoming = after.getJSONObject("records")
        require(incoming.length() <= 200000)
        // Validate every row before touching SQLite. Table and column names are always from this allowlist.
        incoming.keys().forEach { key ->
            val table = (tables + playlist).firstOrNull { key.startsWith(it.name + ":") } ?: error("Unsupported cloud record")
            val entry = incoming.getJSONObject(key)
            val row = if(table == playlist) entry.getJSONObject("row") else entry
            require(row.keys().asSequence().toSet() == table.fields.toSet() && table.id(row) == key)
            table.keys.forEach { require(!row.isNull(it)) }
            row.keys().forEach { field -> val value=row.get(field); require(value == JSONObject.NULL || value is String || value is Number); if(value is String) require(value.length <= 16000) }
            if(table == playlist) { val items=entry.getJSONArray("items"); require(items.length() <= 50000); (0 until items.length()).forEach { require(items.get(it) is String) } }
        }
    }
    fun apply(db: MusicDatabase, before: JSONObject, after: JSONObject) {
        validate(after)
        val incoming=after.getJSONObject("records")
        fun upsert(table: Table, row: JSONObject) {
            val fields=table.fields.filter { it in columns(db, table) }
            val args=fields.map { if(row.isNull(it)) null else row.get(it) }.toTypedArray()
            if(table.name == "event") {
                val exists=db.openHelper.writableDatabase.query("SELECT 1 FROM event WHERE songId=? AND timestamp=? AND playTime=? LIMIT 1", table.keys.map { row.get(it) }.toTypedArray()).use { it.moveToFirst() }
                if(!exists) db.openHelper.writableDatabase.execSQL("INSERT INTO event(${fields.joinToString(",")}) VALUES(?,?,?)", args)
            } else {
                db.openHelper.writableDatabase.execSQL("INSERT OR IGNORE INTO ${table.name}(${fields.joinToString(",") { "`$it`" }}) VALUES(${fields.joinToString(",") { "?" }})",args)
                val changed=fields-table.keys.toSet()
                if(changed.isNotEmpty()) db.openHelper.writableDatabase.execSQL("UPDATE ${table.name} SET ${changed.joinToString(",") { "`$it`=?" }} WHERE ${table.keys.joinToString(" AND ") { "`$it`=?" }}",(changed+table.keys).map { if(row.isNull(it)) null else row.get(it) }.toTypedArray())
            }
        }
        tables.forEach { table -> incoming.keys().asSequence().filter { it.startsWith(table.name+":") }.forEach { upsert(table,incoming.getJSONObject(it)) } }
        incoming.keys().asSequence().filter { it.startsWith("playlist:") }.forEach { key ->
            val entry=incoming.getJSONObject(key);val row=entry.getJSONObject("row");upsert(playlist,row)
            val id=row.getString("id")
            db.openHelper.writableDatabase.execSQL("DELETE FROM playlist_song_map WHERE playlistId=? AND songId IN (SELECT id FROM song WHERE isLocal=0 AND isUploaded=0)",arrayOf(id))
            val items=entry.getJSONArray("items")
            (0 until items.length()).forEach { i -> db.openHelper.writableDatabase.execSQL("INSERT INTO playlist_song_map(playlistId,songId,position) SELECT ?,?,? WHERE EXISTS(SELECT 1 FROM song WHERE id=?)",arrayOf<Any>(id,items.getString(i),i,items.getString(i))) }
        }
        val old=before.optJSONObject("records") ?: JSONObject()
        old.keys().asSequence().filter { !incoming.has(it) }.forEach { key ->
            val table=(tables+playlist).firstOrNull { key.startsWith(it.name+":") } ?: return@forEach
            val entry=old.getJSONObject(key);val row=if(table == playlist) entry.getJSONObject("row") else entry
            val args=table.keys.map { row.get(it) }.toTypedArray()
            if(table.name=="song") db.openHelper.writableDatabase.execSQL("UPDATE song SET liked=0,likedDate=NULL,inLibrary=NULL WHERE id=?",args)
            else if(table.name !in setOf("artist","album")) db.openHelper.writableDatabase.execSQL("DELETE FROM ${table.name} WHERE ${table.keys.joinToString(" AND ") { "`$it`=?" }}",args)
        }
        db.openHelper.writableDatabase.execSQL("UPDATE song SET totalPlayTime=MAX(totalPlayTime,COALESCE((SELECT SUM(playTime) FROM event WHERE songId=song.id),0)) WHERE isLocal=0")
    }
}
