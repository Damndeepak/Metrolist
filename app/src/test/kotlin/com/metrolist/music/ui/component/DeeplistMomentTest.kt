package com.metrolist.music.ui.component

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeeplistMomentTest {
    @Test fun `old bookmarks survive new fields and malformed neighbours`() {
        val raw = """[{"text":"line","title":"song","artist":"artist","songId":"a","position":12000},false]"""
        val moment = deeplistSavedLyrics(raw).single()
        assertEquals(12000L, moment.position)
        assertNull(moment.reason)
        assertEquals(listOf(moment), deeplistSavedLyrics(deeplistEncodeLyrics(listOf(moment))))
    }

    @Test fun `reason edits replace bookmark and deleting another preserves jump target`() {
        val first = DeeplistSavedLyric("line", "song", "artist", "a", 15000)
        val second = first.copy(songId = "b", title = "other")
        val updated = first.copy(reason = DeeplistMomentReason.BEAT, note = "soft guitar")
        val raw = deeplistSaveLyric(deeplistSaveLyric("[]", first), second)
        val items = deeplistSavedLyrics(deeplistSaveLyric(raw, updated))
        assertEquals(2, items.size)
        val remaining = deeplistSavedLyrics(deeplistEncodeLyrics(items.filter { it.songId != "b" })).single()
        assertEquals(updated, remaining)
    }

    @Test fun `instrumental moment needs no lyric text`() {
        val moment = DeeplistSavedLyric("", "Instrumental", "Artist", "track", 32000, DeeplistMomentReason.BEAT, "soft guitar")
        assertEquals(moment, deeplistSavedLyrics(deeplistSaveLyric("[]", moment)).single())
    }

    @Test fun `repeated chorus can be saved at different timestamps`() {
        val first = DeeplistSavedLyric("chorus", "song", "artist", "a", 10000)
        val second = first.copy(position = 60000)
        assertEquals(2, deeplistSavedLyrics(deeplistSaveLyric(deeplistSaveLyric("[]", first), second)).size)
    }

    @Test fun `saved history remains bounded and negative positions clamp to zero`() {
        var raw = "[]"
        repeat(40) { raw = deeplistSaveLyric(raw, DeeplistSavedLyric("$it", "title", "artist", "$it", -5, note = "x".repeat(200))) }
        val items = deeplistSavedLyrics(raw)
        assertEquals(30, items.size)
        assertTrue(items.all { it.position == 0L && it.note.length == 160 })
    }
}
