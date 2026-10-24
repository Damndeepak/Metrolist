package com.metrolist.music.lyrics

import kotlin.math.abs
import kotlin.math.exp

internal class DeeplistLyricSpring {
    var position = 0f
        private set
    private var velocity = 0f

    fun reset(value: Float) { position = value; velocity = 0f }

    fun advance(target: Float, seconds: Float, frequency: Float = 10f): Float {
        val dt = seconds.coerceIn(0f, .1f)
        val displacement = position - target
        val coefficient = velocity + frequency * displacement
        val decay = exp(-frequency * dt)
        val next = target + (displacement + coefficient * dt) * decay
        velocity = (velocity - frequency * coefficient * dt) * decay
        if ((target - position) * (target - next) < 0f) reset(target)
        else position = next
        return position
    }
}

internal fun deeplistWordEmphasis(now: Long, start: Long, end: Long): Float {
    if (now < start || now >= end + 220L) return 0f
    val attack = ((now - start) / minOf(100f, (end - start).coerceAtLeast(1L) * .35f)).coerceIn(0f, 1f)
    val release = ((end + 220L - now) / 220f).coerceIn(0f, 1f)
    val p = minOf(attack, release)
    return p * p * (3f - 2f * p)
}

internal fun deeplistLineFocus(distance: Float): Float {
    val p = (1f - abs(distance)).coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

internal fun deeplistNearestCenter(centers: FloatArray, value: Float): Int {
    if (centers.isEmpty()) return 0
    var low = 0
    var high = centers.lastIndex
    while (low < high) {
        val mid = (low + high) ushr 1
        if (centers[mid] < value) low = mid + 1 else high = mid
    }
    return if (low > 0 && value - centers[low - 1] < centers[low] - value) low - 1 else low
}
