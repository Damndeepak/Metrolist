package com.metrolist.music.lyrics

import java.nio.ByteBuffer
import kotlin.math.sqrt

/** Sparse absolute reads leave both the PCM bytes and the buffer position untouched. */
internal fun pcmEnergy(buffer: ByteBuffer, bytes: Int, floating: Boolean): Float {
    if (bytes !in 2..4 || buffer.remaining() < bytes) return 0f
    var sum = 0.0
    var count = 0
    var i = buffer.position()
    while (i <= buffer.limit() - bytes) {
        val sample = when {
            floating -> buffer.getFloat(i).toDouble()
            bytes == 2 -> buffer.getShort(i) / 32768.0
            bytes == 3 -> {
                val value = (buffer.get(i).toInt() and 255) or ((buffer.get(i + 1).toInt() and 255) shl 8) or (buffer.get(i + 2).toInt() shl 16)
                value / 8388608.0
            }
            else -> buffer.getInt(i) / 2147483648.0
        }
        if (sample.isFinite()) { sum += sample * sample; count++ }
        i += bytes * 31
    }
    return if (count == 0) 0f else (sqrt(sum / count) * 3.0).toFloat().coerceIn(0f, 1f)
}
