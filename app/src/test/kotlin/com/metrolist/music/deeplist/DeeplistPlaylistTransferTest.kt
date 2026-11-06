package com.metrolist.music.deeplist

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class DeeplistPlaylistTransferTest {
    @Test fun `share preserves order metadata and duplicates without local paths`() {
        val song = DeeplistImportTrack("A song", "Artist", "dQw4w9WgXcQ", 200, "https://example.com/art.jpg")
        val shared = DeeplistSharedPlaylist("My playlist", listOf(song, song, DeeplistImportTrack("Local", "B", "/private/file.mp3")))
        val decoded = DeeplistPlaylistTransfer.decode(DeeplistPlaylistTransfer.encode(shared))
        assertTrue(DeeplistPlaylistTransfer.encode(shared).startsWith("https://"))
        assertEquals(shared.name, decoded.name)
        assertEquals(listOf(song, song), decoded.tracks.take(2))
        assertEquals("", decoded.tracks.last().id)
    }
    @Test fun `CSV handles quoted commas newlines BOM and Spotify export headers`() {
        val csv = "\uFEFFTrack Name,Artist Name(s)\r\n\"Song, name\",\"Artist A; Artist B\"\r\n\"Long\nname\",C"
        val rows = DeeplistPlaylistTransfer.songList(csv)
        assertEquals(2, rows.size); assertEquals("Song, name", rows[0].title); assertEquals("Artist A; Artist B", rows[0].artist)
        assertEquals("Long\nname", rows[1].title)
        assertTrue(runCatching { DeeplistPlaylistTransfer.songList("\"unterminated") }.isFailure)
    }
    @Test fun `rejects unsupported service links and oversized playlists without truncation`() {
        assertTrue(runCatching { DeeplistPlaylistTransfer.songList("https://music.apple.com/playlist/example") }.isFailure)
        assertTrue(runCatching { DeeplistPlaylistTransfer.songList((1..2001).joinToString("\n") { "Track $it,Artist" }) }.isFailure)
        val rows = DeeplistPlaylistTransfer.songList((1..2000).joinToString("\n") { "Track $it,Artist" })
        assertEquals(2000, rows.size)
        assertEquals("Track 1", rows.first().title)
        assertEquals("Track 2000", rows.last().title)
    }
    @Test fun `legacy playlist metadata still decodes after Spotify import removal`() {
        val legacy = """{"format":"deeplist-playlist","version":1,"name":"Old playlist","tracks":[{"title":"A song","artist":"Artist","id":"dQw4w9WgXcQ","spotifyId":"1234567890123456789012"}]}"""
        val bytes = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bytes).use { it.write(legacy.toByteArray(Charsets.UTF_8)) }
        val code = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())
        val decoded = DeeplistPlaylistTransfer.decode("https://deeplist-playlists.cherrybard2.chatgpt.site/playlist#$code")
        assertEquals("Old playlist", decoded.name)
        assertEquals("dQw4w9WgXcQ", decoded.tracks.single().id)
        assertEquals("A song", decoded.tracks.single().title)
        assertTrue(runCatching { DeeplistPlaylistTransfer.songList("https://open.spotify.com/playlist/1234567890123456789012") }.isFailure)
        assertTrue(runCatching { DeeplistPlaylistTransfer.songList("spotify:playlist:1234567890123456789012") }.isFailure)
    }
    @Test fun `bounded reads reject oversized data`() {
        assertArrayEquals(byteArrayOf(1, 2), deeplistReadLimited(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        assertTrue(runCatching { deeplistReadLimited(ByteArrayInputStream(ByteArray(100)), 8) }.isFailure)
        assertTrue(runCatching { DeeplistPlaylistTransfer.decode("bad") }.isFailure)
        assertTrue(runCatching { DeeplistPlaylistTransfer.encode(DeeplistSharedPlaylist("Empty", emptyList())) }.isFailure)
    }
}
