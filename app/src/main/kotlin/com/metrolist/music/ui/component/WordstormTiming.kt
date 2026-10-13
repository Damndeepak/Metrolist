package com.metrolist.music.ui.component

internal fun wordstormLineIndex(times: List<Long>, positionMs: Long): Int =
    times.indexOfLast { it <= positionMs }

internal fun wordstormLyricsForTrack(trackId: String?, lyricsId: String?, text: String?): String? =
    text.takeIf { trackId != null && trackId == lyricsId }
