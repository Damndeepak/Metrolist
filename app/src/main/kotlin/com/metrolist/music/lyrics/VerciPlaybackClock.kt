package com.metrolist.music.lyrics

import kotlin.math.abs
import kotlin.math.exp

/** Interpolates coarse player samples using the monotonic display clock. */
internal class VerciPlaybackClock {
    private var raw: Long? = null
    private var anchorMs = 0.0
    private var anchorNanos = 0L

    fun reset() { raw = null }

    fun position(sampleMs: Long, frameNanos: Long, speed: Float): Long {
        val previous = raw
        if (previous == null) {
            anchorMs = sampleMs.toDouble()
            anchorNanos = frameNanos
        } else if (previous != sampleMs) {
            val predicted = anchorMs + (frameNanos - anchorNanos) / 1_000_000.0 * speed
            anchorMs = if (abs(sampleMs - predicted) > 150.0) sampleMs.toDouble()
                else predicted + (sampleMs - predicted) * .08
            anchorNanos = frameNanos
        }
        raw = sampleMs
        return (anchorMs + (frameNanos - anchorNanos) / 1_000_000.0 * speed).toLong()
    }
}

internal fun verciDampPosition(current: Float, target: Float, elapsedNanos: Long): Float {
    val amount = (1.0 - exp(-elapsedNanos.coerceAtLeast(0) / 45_000_000.0)).toFloat()
    return current + (target - current) * amount
}

internal fun lyricsRefreshRate(rates: List<Float>): Float? =
    rates.filter { it >= 119f }.minOrNull() ?: rates.maxOrNull()
