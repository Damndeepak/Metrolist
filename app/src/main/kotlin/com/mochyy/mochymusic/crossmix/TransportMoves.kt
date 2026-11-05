package com.mochyy.mochymusic.crossmix

import com.mochyy.mochymusic.constants.TransportEffect
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// rewind and tape stop read back through what just played, which only holds about 8 seconds
private const val RewindMaxMs = 2_500f
private const val TapeStopMaxMs = 3_000f

// one side of a pause, play or skip fade at progress p (0..1 across lengthMs), drawn with crossmix's own effects
fun TransportEffect.evaluate(role: CrossmixRole, p: Float, lengthMs: Float, out: FxParams) {
    out.reset()
    val t = p.coerceIn(0f, 1f)
    val leaving = role == CrossmixRole.Outgoing
    when (this) {
        TransportEffect.OFF -> out.gain = if (leaving) 1f - smooth(t) else smooth(t)

        TransportEffect.ECHO -> if (leaving) {
            // the voice lets go early and its repeats carry the rest of the fade, darker as they go
            out.gain = 1f - smooth(ramp(t, 0.05f, 0.7f))
            out.echoBeats = 0.75f
            out.echoSend = 0.85f * smooth(ramp(t, 0f, 0.4f))
            out.echoFeedback = 0.55f + 0.2f * ramp(t, 0.3f, 0.75f)
            out.echoTone = 0.55f - 0.25f * ramp(t, 0.5f, 1f)
            out.highCutHz = sweepDown(ramp(t, 0.45f, 1f), FxParams.OpenHz, 4_500f)
        } else {
            // the first notes come in as echoes that settle into the song
            out.gain = smooth(ramp(t, 0f, 0.75f))
            out.echoBeats = 0.5f
            out.echoSend = 0.6f * smooth(ramp(t, 0f, 0.15f)) * (1f - smooth(ramp(t, 0.15f, 0.85f)))
            out.echoFeedback = 0.45f
            out.echoTone = 0.5f
            out.lowCutHz = sweepDown(ramp(t, 0f, 0.6f), 300f, 0f)
        }

        TransportEffect.REWIND -> if (leaving) {
            // a dj pulling the record back: a gentle dip first, then the spin over the last stretch
            val spinStart = 1f - min(RewindMaxMs / lengthMs, 0.6f)
            val spin = ramp(t, spinStart, 1f)
            if (spin > 0f) { out.deck = FxParams.Deck.SpinBack; out.deckAmount = spin }
            out.highCutHz = sweepDown(spin, FxParams.OpenHz, 2_400f)
            out.reverbSend = 0.35f * spin
            out.gain = (1f - 0.25f * smooth(ramp(t, 0f, spinStart))) * (1f - smooth(ramp(spin, 0.6f, 1f)))
        } else {
            out.gain = smooth(ramp(t, 0f, 0.8f))
            out.highCutHz = sweepUp(ramp(t, 0f, 0.9f), 700f, FxParams.OpenHz)
            out.reverbSend = 0.4f * smooth(ramp(t, 0f, 0.15f)) * (1f - ramp(t, 0.3f, 1f))
        }

        TransportEffect.TAPE_STOP -> if (leaving) {
            // the tape slows to a halt; the level goes with it, since a stopped tape holds one sample
            val stopStart = 1f - min(TapeStopMaxMs / lengthMs, 0.7f)
            val stop = ramp(t, stopStart, 1f)
            if (stop > 0f) { out.deck = FxParams.Deck.TapeStop; out.deckAmount = stop }
            out.highCutHz = sweepDown(stop, FxParams.OpenHz, 1_600f)
            out.gain = (1f - 0.2f * smooth(ramp(t, 0f, stopStart))) * (1f - smooth(ramp(stop, 0.7f, 1f)))
        } else {
            out.gain = smooth(ramp(t, 0f, 0.7f))
            out.highCutHz = sweepUp(ramp(t, 0f, 1f), 900f, FxParams.OpenHz)
            out.crush = 0.3f * (1f - ramp(t, 0f, 0.6f))
        }

        TransportEffect.FAR_AWAY -> if (leaving) {
            out.gain = 1f - smooth(ramp(t, 0.5f, 1f))
            out.recede(smooth(ramp(t, 0f, 0.85f)), side = -1f)
        } else {
            out.gain = smooth(ramp(t, 0f, 0.35f))
            out.recede(1f - smooth(ramp(t, 0.1f, 0.95f)), side = 1f)
        }

        TransportEffect.REVERB -> if (leaving) {
            out.gain = 1f - smooth(ramp(t, 0.1f, 0.75f))
            out.reverbSend = 0.9f * smooth(ramp(t, 0f, 0.6f))
            out.reverbSize = 0.9f
            out.reverbFreeze = 0.6f * ramp(t, 0.7f, 1f)
            out.highCutHz = sweepDown(ramp(t, 0.2f, 1f), FxParams.OpenHz, 2_200f)
        } else {
            out.gain = smooth(ramp(t, 0.15f, 0.9f))
            out.reverbSend = 0.6f * smooth(ramp(t, 0f, 0.15f)) * (1f - smooth(ramp(t, 0.3f, 1f)))
            out.highCutHz = sweepUp(ramp(t, 0.1f, 0.9f), 1_200f, FxParams.OpenHz)
        }

        TransportEffect.FILTER -> if (leaving) {
            out.highCutHz = sweepDown(smooth(t), FxParams.OpenHz, 260f)
            out.resonance = 0.707f + 0.55f * sin(PI.toFloat() * t)
            out.gain = 1f - smooth(ramp(t, 0.55f, 1f))
        } else {
            out.lowCutHz = sweepDown(smooth(t), 1_400f, 0f)
            out.resonance = 0.707f + 0.35f * sin(PI.toFloat() * t)
            out.gain = smooth(ramp(t, 0f, 0.5f))
        }
    }
}
