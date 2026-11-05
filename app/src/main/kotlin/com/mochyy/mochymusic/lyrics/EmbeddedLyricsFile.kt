// EmbeddedLyricsFile.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** The lyrics in the file at [uri], as [EmbeddedLyrics.find] reads them; null when none or unreadable. */
fun readEmbeddedLyrics(context: Context, uri: Uri): String? = runCatching {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching null
    // the stream owns the descriptor and closes it once, with the channel: closing the raw file
    // descriptor and the ParcelFileDescriptor separately is a double close Android can abort on
    ParcelFileDescriptor.AutoCloseInputStream(descriptor).channel.use { channel ->
        EmbeddedLyrics.find(ChannelSource(channel))
    }
}.getOrNull()

private class ChannelSource(private val channel: FileChannel) : EmbeddedLyrics.Source {
    override val size: Long = channel.size()

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || position >= size || length <= 0) return ByteArray(0)
        val buffer = ByteBuffer.allocate(minOf(length.toLong(), size - position).toInt())
        var at = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, at)
            if (read <= 0) break
            at += read
        }
        return buffer.array().copyOf(buffer.position())
    }
}
