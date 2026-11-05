package com.music.musixmatch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// real track.search answers (trimmed) from 2026-10-31, so the picks are checked against what musixmatch actually sends
class MusixmatchPickTest {

    private fun tracks(name: String): List<JsonObject> {
        val text = javaClass.classLoader!!.getResource(name)!!.readText()
        return (Json.parseToJsonElement(text) as JsonArray).map { (it as JsonObject)["track"] as JsonObject }
    }

    private fun page(candidate: MusixmatchApi.Candidate) = candidate.track.string("track_share_url").orEmpty().substringAfter("/lyrics/")

    @Test fun `a title written two ways is searched each way`() {
        assertEquals(listOf("フィナーレ。 - Finale.", "フィナーレ。", "Finale."), titleVariants("フィナーレ。 - Finale."))
        assertEquals(listOf("フィナーレ。(Finale.)", "フィナーレ。", "Finale."), titleVariants("フィナーレ。(Finale.)"))
        assertEquals(listOf("Chandelier"), titleVariants("Chandelier"))
    }

    @Test fun `finale is found by its latin half and keeps its analysed page`() {
        val titles = titleVariants("フィナーレ。 - Finale.")
        assertTrue(MusixmatchApi.rank(tracks("finale_full.json"), titles, "eill", 247).isEmpty())
        val ranked = MusixmatchApi.rank(tracks("finale_latin.json"), titles, "eill", 247)
        assertEquals("eill-3/Finale", page(ranked.first()))
        assertTrue(ranked.first().analysed)
    }

    @Test fun `the artist's own copy beats a tribute act of the same length`() {
        val ranked = MusixmatchApi.rank(tracks("someone_like_you.json"), listOf("Someone Like You"), "Adele", 285)
        assertEquals("Adele-5/Someone-Like-You-1", page(ranked.first()))
        // the other copies with the very same title are there to fall back on
        assertTrue(ranked.drop(1).any { it.exactTitle && page(it).startsWith("Hello-I-m-Adele/") })
    }

    @Test fun `a named page beats one made before the song had its name`() {
        val ranked = MusixmatchApi.rank(tracks("drop_dead.json"), listOf("drop dead"), "Olivia Rodrigo", 225)
        assertEquals("Olivia-Rodrigo/drop-dead-1", page(ranked.first()))
        assertTrue(ranked.any { page(it) == "Olivia-Rodrigo/track-1" && it.exactTitle })
    }
}
