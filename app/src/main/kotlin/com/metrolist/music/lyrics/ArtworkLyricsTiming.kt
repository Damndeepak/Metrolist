package com.metrolist.music.lyrics

internal fun artworkActiveWord(words: List<WordTimestamp>?, positionMs: Long): Int {
    if (words.isNullOrEmpty() || words.any { !it.startTime.isFinite() || !it.endTime.isFinite() || it.startTime < 0 || it.endTime <= it.startTime }) return -1
    val seconds = positionMs / 1000.0
    return words.indexOfLast { seconds >= it.startTime && seconds < it.endTime }
}
