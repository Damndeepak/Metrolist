package com.metrolist.music.ui.component

import kotlin.math.abs
import kotlin.math.exp

internal class AppleLyricsMotion(initial: Float = 0f, private val timeline: () -> List<Long>) {
    var position = initial
        private set
    private var previousFrame: Long? = null

    fun advance(timeMs: Long, frameNanos: Long, preview: Boolean = false, intensity: Float = 1f): Float {
        val target = wordstormLineIndex(timeline(), timeMs).coerceAtLeast(0).toFloat()
        val dt = previousFrame?.let { ((frameNanos - it) / 1e9f).coerceIn(0f, .1f) } ?: 0f
        position = if (preview || intensity == 0f || abs(target - position) > 4f) target
            else position + (target - position) * (1f - exp(-dt * (4f + 4f * (1f - intensity))))
        previousFrame = frameNanos
        return position
    }
}
