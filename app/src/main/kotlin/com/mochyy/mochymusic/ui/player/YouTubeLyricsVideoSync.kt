// YouTubeLyricsVideoSync.kt

package com.mochyy.mochymusic.ui.player

import com.mochyy.mochymusic.lyrics.CaptionAligner
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.lyrics.VideoCaptionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// where on a separately found music video the playing song is, from lining the song's lyrics up with the video's captions
class LyricVideoTimeline internal constructor(private val alignment: CaptionAligner.Alignment) {
    fun videoMsAt(songLyricMs: Long): Long = alignment.map.videoMsAt(songLyricMs)
}

// null when the video has no captions, or too few of the lyrics could be found in them to trust
suspend fun buildLyricVideoTimeline(videoId: String, songLyricsRaw: String): LyricVideoTimeline? {
    val lines = withContext(Dispatchers.Default) { LyricsUtils.parseLyrics(songLyricsRaw) }
    if (lines.size < 4 || LyricsUtils.isUnsynced(lines)) return null
    val captions = VideoCaptionSource.captions(videoId) ?: return null
    val aligned = VideoCaptionSource.align(captions, lines) ?: return null
    return LyricVideoTimeline(aligned.alignment)
}

object LyricVideoTimelineCache {
    private const val maxSize = 32
    private val map = LinkedHashMap<String, LyricVideoTimeline?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun get(key: String): LyricVideoTimeline? = map[key]

    @Synchronized
    fun put(key: String, timeline: LyricVideoTimeline?) {
        map[key] = timeline
        if (map.size > maxSize) map.remove(map.keys.first())
    }
}
