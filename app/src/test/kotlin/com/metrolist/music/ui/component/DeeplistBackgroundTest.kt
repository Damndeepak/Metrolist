package com.metrolist.music.ui.component

import com.metrolist.music.lyrics.canvas.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeeplistBackgroundTest {
    @Test fun `song choices remain independent and removing a video returns to auto`() {
        val videos = """{"first":"content://media/video/1"}"""
        assertEquals("custom", deeplistSongBackdropMode("{}", videos, "first"))
        assertEquals("auto", deeplistSongBackdropMode("{}", videos, "second"))
        assertEquals("cover", deeplistSongBackdropMode("""{"first":"cover"}""", videos, "first"))
        assertEquals("auto", deeplistSongBackdropMode("""{"first":"auto"}""", videos, "first"))
        assertEquals("auto", deeplistSongBackdropMode("{}", "{}", "first"))
        assertEquals("auto", deeplistSongBackdropMode("broken", "broken", "first"))
    }

    @Test fun `artwork matching rejects other songs and preserves Hindi names`() {
        val video = CanvasArtwork("https://example.com/a.mp4", title = "Tum Hi Ho", artist = "Arijit Singh")
        assertTrue(video.matches("Tum Hi Ho", "Arijit Singh"))
        assertFalse(video.matches("Channa Mereya", "Arijit Singh"))
        assertFalse(video.matches("Tum Hi Ho", "Other Artist"))
        assertFalse(CanvasArtwork("https://example.com/a.mp4", title = "दिल", artist = "अरिजीत").matches("रात", "अरिजीत"))
        assertTrue("दिल".normalizeForMatch().isNotBlank())
        assertEquals("beyonce", "Beyoncé".normalizeForMatch())
    }

    @Test fun `untrusted video references fall back instead of being opened`() {
        assertNull(deeplistSongVideo("""{"s":"file:///etc/passwd"}""", "s"))
        assertFalse(validCanvasUrl("http://example.com/movie.mp4"))
        assertFalse(validCanvasUrl("https://user:password@example.com/movie.mp4"))
        assertTrue(validCanvasUrl("https://example.com/movie.m3u8"))
    }
}
