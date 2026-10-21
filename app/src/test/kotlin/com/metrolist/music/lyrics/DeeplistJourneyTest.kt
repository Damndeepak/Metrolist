package com.metrolist.music.lyrics

import com.metrolist.music.constants.VerciStyle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeeplistJourneyTest {
    @Test fun `song cues sort replace nearby times and isolate tracks`() {
        var raw = deeplistPutCue("{}", "song-a", 10000, VerciStyle.APPLE)
        raw = deeplistPutCue(raw, "song-a", 0, VerciStyle.SHIP)
        raw = deeplistPutCue(raw, "song-a", 10200, VerciStyle.VISUAL)
        assertEquals(listOf(0L, 10200L), deeplistCues(raw, "song-a").map { it.time })
        assertEquals(VerciStyle.VISUAL, deeplistCues(raw, "song-a").last().style)
        assertTrue(deeplistCues(raw, "song-b").isEmpty())
        assertEquals(0, deeplistSinger("bad", "song-a", 10))
    }
}
