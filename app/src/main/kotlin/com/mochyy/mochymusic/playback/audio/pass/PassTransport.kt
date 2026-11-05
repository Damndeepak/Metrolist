// PassTransport.kt

package com.mochyy.mochymusic.playback.audio.pass

import java.nio.ByteBuffer

/** one output only mochy plays to, opened at one fixed format; nothing in here mixes, resamples or scales a sample */
internal interface PassTransport {
    val sampleRate: Int
    val channels: Int

    /** the integer container written: 16, 24 (packed) or 32 */
    val bits: Int
    val deviceName: String

    /** how much audio it holds before the dac, in frames */
    val capacityFrames: Long

    /** takes as much of the buffer as fits without waiting, and says how many bytes */
    fun write(buffer: ByteBuffer): Int

    fun play()

    fun pause()

    /** drops what's queued and starts the frame count over */
    fun flush()

    /** frames the dac has really played since the last flush, silence it was padded with not counted */
    fun playedFrames(): Long

    fun release()
}

/** the output can't carry on: unplugged, taken over, or it never opened; the song goes back to the normal way */
internal class PassFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
