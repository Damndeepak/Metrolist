package com.metrolist.music.ui.component

internal fun wordstormLineIndex(times: List<Long>, positionMs: Long): Int =
    times.indexOfLast { it <= positionMs }

internal fun wordstormLyricsForTrack(trackId: String?, lyricsId: String?, text: String?): String? =
    text.takeIf { trackId != null && trackId == lyricsId }

internal fun deeplistProgress(position: Long, start: Long, end: Long): Float =
    ((position - start).toFloat() / (end - start).coerceAtLeast(1L)).coerceIn(0f, 1f)

internal fun deeplistWebsiteUrl(value: String): String? = runCatching {
    val uri = java.net.URI(value.trim())
    value.trim().takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null }
}.getOrNull()
