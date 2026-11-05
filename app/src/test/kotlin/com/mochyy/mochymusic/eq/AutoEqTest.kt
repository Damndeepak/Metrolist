package com.mochyy.mochymusic.eq

import com.mochyy.mochymusic.eq.audio.ResponseCurve
import com.mochyy.mochymusic.eq.autoeq.AutoEqFitter
import com.mochyy.mochymusic.eq.autoeq.AutoEqMeasurement
import com.mochyy.mochymusic.eq.autoeq.AutoEqRepository
import com.mochyy.mochymusic.eq.autoeq.AutoEqTuning
import com.mochyy.mochymusic.eq.autoeq.HeadphoneForm
import com.mochyy.mochymusic.eq.autoeq.percentDecode
import com.mochyy.mochymusic.eq.data.ParametricEQ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** The AutoEq database parsing and the on-device fitter, against real AutoEq files. */
class AutoEqTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("autoeq/$name")) { "missing fixture $name" }.readText()

    private val models by lazy { AutoEqRepository.parseIndex(resource("INDEX.md")) }

    private fun xm5(): AutoEqMeasurement {
        val entry = models.first { it.name == "Sony WH-1000XM5" }.entries.first { it.source == "oratory1990" }
        return AutoEqRepository.parseMeasurement(entry, resource("Sony WH-1000XM5.csv"), resource("Sony WH-1000XM5 ParametricEQ.txt"))
    }

    @Test
    fun `index groups results by headphone and reads source, rig and form`() {
        val xm5 = models.first { it.name == "Sony WH-1000XM5" }
        assertEquals(3, xm5.entries.size)
        val rtings = xm5.entries.first { it.source == "Rtings" }
        assertEquals("Bruel & Kjaer 5128", rtings.rig)
        assertEquals("Rtings/Bruel & Kjaer 5128 over-ear/Sony WH-1000XM5", rtings.path)
        assertEquals(HeadphoneForm.OverEar, rtings.form)

        // names and paths with brackets survive
        val aero = models.first { it.name == "1MORE Aero (ANC Off)" }.entries.single()
        assertEquals("HypetheSonics/GRAS RA0045 in-ear/1MORE Aero (ANC Off)", aero.path)
        assertEquals(HeadphoneForm.InEar, aero.form)
        assertEquals("GRAS RA0045", aero.rig)
    }

    @Test
    fun `search ignores punctuation and spacing`() {
        val repo = AutoEqRepository
        assertTrue(repo.search(models, "wh1000xm5").any { it.name == "Sony WH-1000XM5" })
        assertTrue(repo.search(models, "hd 600").any { it.name == "Sennheiser HD 600" })
        assertTrue(repo.search(models, "sony xm5").any { it.name == "Sony WH-1000XM5" })
        assertTrue(repo.search(models, "zzzz nothing").isEmpty())
    }

    @Test
    fun `percent decoding keeps plus signs and multi-byte text`() {
        assertEquals("A + B", percentDecode("A%20+%20B"))
        assertEquals("Bruel & Kjaer", percentDecode("Bruel%20&%20Kjaer"))
        assertEquals("Café", percentDecode("Caf%C3%A9"))
    }

    @Test
    fun `measurement reads the curve and AutoEq's own fit`() {
        val m = xm5()
        assertEquals(695, m.frequencies.size)
        assertEquals(20.0, m.frequencies.first(), 0.01)
        assertEquals(10, m.preset.bands.size)
        assertEquals(-6.2, m.preset.preamp, 0.001)
    }

    @Test
    fun `default tuning uses AutoEq's fit untouched`() {
        val m = xm5()
        assertSame(m.preset, AutoEqFitter.correction(m, AutoEqTuning()))
    }

    @Test
    fun `own fit gets close to AutoEq's, quickly`() {
        val m = xm5()
        val start = System.nanoTime()
        val ours = AutoEqFitter.fit(m.frequencies, m.raw, m.target, bands = 10, maxHz = 16_000.0)
        val ms = (System.nanoTime() - start) / 1e6
        val before = residual(m, ParametricEQ(0.0, emptyList()))
        val autoEq = residual(m, m.preset)
        val mine = residual(m, ours)
        println("residual dB rms: none %.2f, AutoEq %.2f, ours %.2f, in %.0f ms".format(before, autoEq, mine, ms))
        assertTrue("fit should cut the error a lot (before $before, after $mine)", mine < before * 0.5)
        assertTrue("fit should be near AutoEq's ($autoEq vs $mine)", mine < autoEq * 1.6 + 0.3)
        assertTrue("headroom keeps boosts from clipping", ours.preamp <= 0.0)
        assertTrue("took $ms ms", ms < 4000)
    }

    @Test
    fun `fewer bands and a lower ceiling still fit`() {
        val m = xm5()
        val five = AutoEqFitter.correction(m, AutoEqTuning(bands = 5, maxHz = 10_000))
        assertTrue(five.bands.size <= 5)
        assertNotNull(five.bands.firstOrNull())
        assertTrue(residual(m, five, upTo = 10_000.0) < residual(m, ParametricEQ(0.0, emptyList()), upTo = 10_000.0))
    }

    /** RMS of target minus corrected over 20 Hz .. [upTo], both centred on the midrange */
    private fun residual(m: AutoEqMeasurement, eq: ParametricEQ, upTo: Double = 10_000.0): Double {
        val response = ResponseCurve(m.frequencies).response(eq.bands)
        val idx = m.frequencies.indices.filter { m.frequencies[it] <= upTo }
        val diff = idx.map { m.target[it] - (m.raw[it] + response[it]) }
        val mid = m.frequencies.indices.filter { m.frequencies[it] in 300.0..3000.0 }
            .map { m.target[it] - (m.raw[it] + response[it]) }.average()
        return sqrt(diff.sumOf { (it - mid) * (it - mid) } / diff.size)
    }
}
