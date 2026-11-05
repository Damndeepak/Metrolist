package com.mochyy.mochymusic.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Re-sync only adds songs whose key it hasn't seen, so the same song must key the same every read. */
class PlaylistSourceRecordTest {

    @Test
    fun sameSongReadByLinkOrAccountKeysTheSame() {
        // Spotify's embed page joins the artists into one subtitle; its account API lists them
        val byLink = ImportedTrack(title = "Starboy (feat. Daft Punk)", artist = "The Weeknd, Daft Punk", album = null)
        val byAccount = ImportedTrack(title = "Starboy", artist = "The Weeknd", album = "Starboy", durationMs = 230_000)
        assertEquals(byLink.trackKey(), byAccount.trackKey())

        val apple = ImportedTrack(title = "Café", artist = "Artist & Friend", album = null)
        assertEquals(apple.trackKey(), ImportedTrack(title = "Cafe", artist = "Artist", album = null).trackKey())
    }

    @Test
    fun youTubeSongsKeyByTheirId() {
        val song = ImportedTrack(title = "Anything", artist = "Anyone", album = null, videoId = "abc123")
        assertEquals("yt:abc123", song.trackKey())
    }

    @Test
    fun differentSongsKeyApart() {
        val original = ImportedTrack(title = "Song", artist = "Band", album = null)
        assertNotEquals(original.trackKey(), ImportedTrack(title = "Song (Remix)", artist = "Band", album = null).trackKey())
        assertNotEquals(original.trackKey(), ImportedTrack(title = "Song", artist = "Other Band", album = null).trackKey())
    }
}
