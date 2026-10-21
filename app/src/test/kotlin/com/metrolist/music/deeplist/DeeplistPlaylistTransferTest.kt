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
    @Test fun `bounded reads reject oversized data`() {
        assertArrayEquals(byteArrayOf(1, 2), deeplistReadLimited(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        assertTrue(runCatching { deeplistReadLimited(ByteArrayInputStream(ByteArray(100)), 8) }.isFailure)
        assertTrue(runCatching { DeeplistPlaylistTransfer.decode("bad") }.isFailure)
        assertTrue(runCatching { DeeplistPlaylistTransfer.encode(DeeplistSharedPlaylist("Empty", emptyList())) }.isFailure)
    }
}
