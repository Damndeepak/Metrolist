package com.metrolist.music.deeplist

import com.metrolist.music.models.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal data class DeeplistImportTrack(val title: String, val artist: String, val id: String = "", val duration: Int = -1, val thumbnail: String? = null, val spotifyId: String = "") {
    fun metadata() = MediaMetadata(id, title, listOf(MediaMetadata.Artist(null, artist)), duration, thumbnail)
}
internal data class DeeplistSharedPlaylist(val name: String, val tracks: List<DeeplistImportTrack>)
internal fun deeplistReadLimited(input: java.io.InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val read = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size())); if (read < 0) break; output.write(buffer, 0, read); require(output.size() <= limit) }
    return output.toByteArray()
}
internal object DeeplistPlaylistTransfer {
    fun encode(playlist: DeeplistSharedPlaylist): String {
        require(playlist.tracks.isNotEmpty() && playlist.tracks.size <= 2000)
        val root = JSONObject().put("format", "deeplist-playlist").put("version", 1).put("name", playlist.name.take(150)).put("tracks", JSONArray().apply {
            playlist.tracks.forEach { track -> put(JSONObject().put("title", track.title.take(250)).put("artist", track.artist.take(250)).put("id", track.id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.orEmpty()).put("spotifyId", track.spotifyId.takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) }.orEmpty()).put("duration", track.duration).put("thumbnail", track.thumbnail?.takeIf { it.startsWith("https://") })) }
        })
        val output = ByteArrayOutputStream(); GZIPOutputStream(output).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
        require(code.length <= 100_000)
        return "https://deeplist-playlists.cherrybard2.chatgpt.site/playlist#$code"
    }
    fun decode(input: String): DeeplistSharedPlaylist {
        require(input.length <= 110_000)
        val code = (if (input.startsWith("https://")) input.substringAfter('#') else input.substringAfter("data=", input).substringBefore('&')).trim()
        val packed = Base64.getUrlDecoder().decode(code)
        val bytes = GZIPInputStream(ByteArrayInputStream(packed)).use { deeplistReadLimited(it, 1_000_000) }
        require(bytes.size <= 1_000_000)
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        require(root.getString("format") == "deeplist-playlist" && root.getInt("version") == 1)
        val array = root.getJSONArray("tracks"); require(array.length() in 1..2000)
        val tracks = List(array.length()) { i -> array.getJSONObject(i).let {
            val title = it.getString("title"); val artist = it.getString("artist"); val id = it.optString("id")
            require(title.isNotBlank() && title.length <= 250 && artist.length <= 250 && (id.isBlank() || id.matches(Regex("[A-Za-z0-9_-]{11}"))))
            DeeplistImportTrack(title, artist, id, it.optInt("duration", -1).coerceIn(-1, 86400), it.optString("thumbnail").takeIf { url -> url.startsWith("https://") && url.length <= 2000 }, it.optString("spotifyId").takeIf { value -> value.matches(Regex("[A-Za-z0-9]{22}")) }.orEmpty())
        } }
        return DeeplistSharedPlaylist(root.getString("name").take(150), tracks)
    }
    fun songList(text: String): List<DeeplistImportTrack> {
        require(text.length <= 1_000_000)
        val rows = parseCSV(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase().removePrefix("\uFEFF") }
        val titleColumn = header.indexOfFirst { it in setOf("track name", "track title", "title", "song", "song name", "name") }
        val artistColumn = header.indexOfFirst { it in setOf("artist name(s)", "artist name", "artist", "artists", "artist names") }
        val headerPresent = titleColumn >= 0
        return (if (headerPresent) rows.drop(1) else rows).take(2000).mapNotNull { row ->
            val title = row.getOrNull(if (headerPresent) titleColumn else 0)?.trim().orEmpty()
            val artist = row.getOrNull(if (headerPresent) artistColumn else 1)?.trim().orEmpty()
            if (title.isBlank()) null else DeeplistImportTrack(title.take(250), artist.take(250))
        }
    }
    private fun parseCSV(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && (c == ',' || c == '\t') -> { row.add(cell.toString()); cell.clear() }
                !quoted && c == '\n' -> { row.add(cell.toString().trimEnd('\r')); cell.clear(); rows.add(row); row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted)
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString().trimEnd('\r')); rows.add(row) }
        return rows
    }
}

internal fun deeplistSharePlaylist(context: android.content.Context, playlist: DeeplistSharedPlaylist) {
    runCatching {
        val link = DeeplistPlaylistTransfer.encode(playlist)
        context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, link) }, null))
    }.onFailure { android.widget.Toast.makeText(context, com.metrolist.music.R.string.deeplist_share_failed, android.widget.Toast.LENGTH_LONG).show() }
}
