// ExclusivePass.kt

package com.mochyy.mochymusic.playback.audio.pass

import android.media.AudioDeviceInfo
import android.os.Build
import java.nio.ByteBuffer

/** an aaudio output opened only for mochy, at the song's own rate: the usb dac, or a music player's own headphone jack */
internal class ExclusivePass private constructor(
    private var handle: Long,
    override val sampleRate: Int,
    override val channels: Int,
    override val bits: Int,
    override val deviceName: String,
) : PassTransport {

    override val capacityFrames: Long = PassNative.exclusiveCapacity(handle).toLong()

    override fun write(buffer: ByteBuffer): Int {
        val taken = call { PassNative.exclusiveWrite(handle, buffer, buffer.position(), buffer.remaining()) }
        buffer.position(buffer.position() + taken)
        return taken
    }

    override fun play() = call { PassNative.exclusivePlay(handle, true) }

    override fun pause() = call { PassNative.exclusivePlay(handle, false) }

    override fun flush() = call { PassNative.exclusiveFlush(handle) }

    override fun playedFrames(): Long = call { PassNative.exclusivePlayed(handle) }

    override fun release() {
        if (handle != 0L) PassNative.exclusiveClose(handle)
        handle = 0L
    }

    private inline fun <T> call(block: () -> T): T {
        if (handle == 0L) throw PassFailure("the output is closed")
        return try {
            block()
        } catch (error: IllegalStateException) {
            throw PassFailure(error.message ?: "the output went away", error)
        }
    }

    companion object {
        /** 24 and 32-bit containers only reached aaudio on android 12; before that it's 16-bit songs or nothing */
        fun containerFor(sourceBits: Int): Int? = when {
            sourceBits <= 16 -> 16
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> null
            sourceBits <= 24 -> 24
            else -> 32
        }

        fun open(device: AudioDeviceInfo, rate: Int, channels: Int, sourceBits: Int): ExclusivePass {
            if (!PassNative.loaded) throw PassFailure("the native part didn't load")
            val bits = containerFor(sourceBits) ?: throw PassFailure("this android only opens 16-bit exclusively")
            val handle = try {
                PassNative.exclusiveOpen(rate, channels, bits, device.id)
            } catch (error: IllegalStateException) {
                throw PassFailure(error.message ?: "no exclusive output", error)
            }
            val name = device.productName?.toString()?.removePrefix("USB-Audio - ")?.trim().orEmpty().ifEmpty { "DAC" }
            return ExclusivePass(handle, rate, channels, bits, name)
        }
    }
}
