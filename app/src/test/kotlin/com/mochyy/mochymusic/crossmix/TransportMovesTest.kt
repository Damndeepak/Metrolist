package com.mochyy.mochymusic.crossmix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.mochyy.mochymusic.constants.TransportEffect
import com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class TransportMovesTest {

    // every move starts where the music is and ends where it should: silent after leaving, dry after arriving
    @Test
    fun `moves start and end at the right level`() {
        val p = FxParams()
        for (effect in TransportEffect.entries) {
            for (lengthMs in listOf(500f, 2_000f, 10_000f)) {
                val name = "${effect.label} ${lengthMs}ms"
                effect.evaluate(CrossmixRole.Outgoing, 0f, lengthMs, p)
                assertTrue("$name out starts loud (${p.gain})", p.gain >= 0.7f)
                effect.evaluate(CrossmixRole.Outgoing, 1f, lengthMs, p)
                assertTrue("$name out ends silent (${p.gain})", p.gain <= 0.02f)

                effect.evaluate(CrossmixRole.Incoming, 0f, lengthMs, p)
                assertTrue("$name in starts silent (${p.gain})", p.gain <= 0.02f)
                effect.evaluate(CrossmixRole.Incoming, 1f, lengthMs, p)
                assertTrue("$name in ends at full level (${p.gain})", p.gain >= 0.98f)
                assertEquals("$name in ends without echo", 0f, p.echoSend, 1e-3f)
                assertEquals("$name in ends without reverb", 0f, p.reverbSend, 1e-3f)
                assertTrue("$name in ends unfiltered", p.lowCutHz < 1f && p.highCutHz >= FxParams.OpenHz - 1f)
                assertEquals("$name in ends uncrushed", 0f, p.crush, 1e-3f)
                assertEquals("$name in ends on the live deck", FxParams.Deck.Live, p.deck)

                for (step in 0..100) {
                    for (role in CrossmixRole.entries) {
                        effect.evaluate(role, step / 100f, lengthMs, p)
                        assertFalse("$name $role NaN at $step", p.gain.isNaN() || p.highCutHz.isNaN() || p.lowCutHz.isNaN())
                    }
                }
            }
        }
    }

    // run through the real crossmix renderer: no clicks, no clipping, and silence once the leaving side is done
    @Test
    fun `moves render cleanly through the crossmix renderer`() {
        for (effect in TransportEffect.entries) {
            for (role in CrossmixRole.entries) {
                val lengthMs = 2_000f
                val out = render(effect, role, lengthMs, seconds = 3.5)
                val name = "${effect.label} $role"
                var maxJump = 0f
                for (i in 1 until out.size) {
                    assertFalse("$name NaN at $i", out[i].isNaN())
                    assertTrue("$name clipped at $i", abs(out[i]) <= 1f)
                    maxJump = maxOf(maxJump, abs(out[i] - out[i - 1]))
                }
                assertTrue("$name jump $maxJump", maxJump < 0.6f)
                val rate = 44_100
                // anchored at 1 s: the fade runs 1..3 s
                if (role == CrossmixRole.Outgoing) {
                    assertTrue("$name loud before the fade", rms(out, 0, rate) > 0.25f)
                    assertTrue("$name silent once done", rms(out, (3.25 * rate).toInt(), out.size) < 1e-3f)
                } else {
                    assertTrue("$name quiet as it arrives", rms(out, rate, (rate * 1.05).toInt()) < 0.12f)
                    assertTrue("$name full once arrived", rms(out, (3.1 * rate).toInt(), out.size) > 0.3f)
                }
            }
        }
    }

    private fun render(effect: TransportEffect, role: CrossmixRole, lengthMs: Float, seconds: Double): FloatArray {
        val rate = 44_100
        val processor = CrossmixTransitionAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.arm(
            CrossmixTransitionAudioProcessor.Script(
                role = role,
                style = CrossmixStyle.SilkBlend,
                variation = 0,
                lengthBeats = 1f,
                anchorMs = 1_000.0,
                mediaPerBeatMs = lengthMs.toDouble(),
                musicalBeatMs = 500.0,
                endBeat = if (role == CrossmixRole.Outgoing) 1.5f else 1f,
                calibrationMs = 0.0,
                calibrationNanos = 0L,
                transport = effect,
                transportLengthMs = lengthMs,
            )
        )
        val frames = (seconds * rate).toInt()
        val output = FloatArray(frames)
        val block = 1_024
        var frame = 0
        var settled = false
        while (frame < frames) {
            val count = minOf(block, frames - frame)
            val buffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until count) {
                val s = (0.5 * sin(2 * PI * 440.0 * (frame + i) / rate) * 32_767).toInt().toShort()
                buffer.putShort(s); buffer.putShort(s)
            }
            buffer.flip()
            // the controller fades the leaving side's tails out once the move is over, as here
            if (role == CrossmixRole.Outgoing && frame >= 3 * rate && !settled) {
                processor.fadeOutNow()
                settled = true
            }
            processor.onInputTimestamp(frame * 1_000_000L / rate)
            processor.queueInput(buffer)
            val out = processor.output
            for (i in 0 until count) {
                output[frame + i] = out.getShort() / 32_768f
                out.getShort()
            }
            frame += count
        }
        return output
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Float {
        var sum = 0.0
        for (i in from until to) sum += x[i] * x[i]
        return sqrt(sum / (to - from).coerceAtLeast(1)).toFloat()
    }
}
