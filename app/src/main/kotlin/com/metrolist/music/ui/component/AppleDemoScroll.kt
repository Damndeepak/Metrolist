package com.metrolist.music.ui.component

import kotlin.math.abs

internal fun appleDemoWordFill(now: Long, start: Long, end: Long): Float =
    ((now - start).toDouble() / (end - start).coerceAtLeast(80L)).toFloat().coerceIn(0f, 1f)

internal fun appleDemoLineAlpha(distance: Float): Float = when {
    distance <= 1f -> 1f - .2f * distance.coerceAtLeast(0f)
    else -> (.8f - .25f * (distance - 1f)).coerceAtLeast(.55f)
}

// The website uses a 700 ms cubic-bezier(.22, 1, .36, 1) transition.
internal class AppleDemoScroll {
    var position = 0f
        private set
    private var origin = 0f
    private var target = 0f
    private var started = 0L

    fun reset(value: Float) { position = value; origin = value; target = value; started = 0L }

    fun advance(value: Float, frameNanos: Long, intensity: Float = 1f): Float {
        if (intensity <= 0f) { reset(value); return position }
        if (value != target) { origin = position; target = value; started = frameNanos }
        if (abs(target - position) < .001f) { position = target; return position }
        val progress = ((frameNanos - started) / (700_000_000f * intensity.coerceIn(.5f, 1.5f))).coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        repeat(14) {
            val t = (low + high) * .5f
            val x = 3f * (1f - t) * (1f - t) * t * .22f + 3f * (1f - t) * t * t * .36f + t * t * t
            if (x < progress) low = t else high = t
        }
        val t = (low + high) * .5f
        val eased = if (progress == 0f) 0f else if (progress == 1f) 1f else 1f - (1f - t) * (1f - t) * (1f - t)
        position = origin + (target - origin) * eased
        return position
    }
}
