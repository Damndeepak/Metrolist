package com.music.directlyrics

import com.music.directlyrics.models.NetEaseLyricResponse
import com.music.directlyrics.models.NetEaseSearchResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** netease cloud music asked directly with no account, many songs including english ones come with real word-by-word timing */
object NetEase {

    suspend fun getLyrics(title: String, artist: String, duration: Int): Result<String> = runCatching {
        val songId = search(title, artist).bestMatch(title, artist, duration)
            ?: throw IllegalStateException("No matching NetEase song")
        val response = lyricsHttp.get("https://music.163.com/api/song/lyric") {
            parameter("id", songId)
            parameter("lv", 1)
            parameter("yv", 1)
        }.body<NetEaseLyricResponse>()
        val lines = response.yrc?.lyric?.let(::parseYrc)?.takeIf { it.isNotEmpty() }
            ?: response.lrc?.lyric?.let(::parseLrc).orEmpty()
        lines.withoutCredits().takeIf { it.hasLyrics() }?.toLrc()
            ?: throw IllegalStateException("NetEase has no lyrics for this song")
    }

    private suspend fun search(title: String, artist: String): List<TrackCandidate<Long>> =
        lyricsHttp.get("https://music.163.com/api/search/get") {
            parameter("s", "$title $artist")
            parameter("type", 1)
            parameter("limit", SearchLimit)
        }.body<NetEaseSearchResponse>().result?.songs.orEmpty().map { song ->
            TrackCandidate(
                titles = listOf(song.name) + song.alias,
                artists = song.artists.map { it.name },
                durationSec = (song.duration / 1000).toInt(),
                ref = song.id,
            )
        }

    /** netease's word-timed format "[lineStart,lineLength](wordStart,wordLength,0)word" in milliseconds, words keep their trailing space and untimed json credit lines are skipped */
    private fun parseYrc(yrc: String): List<TimedLine> = yrc.lineSequence().mapNotNull { raw ->
        val line = YrcLineRegex.matchEntire(raw.trim()) ?: return@mapNotNull null
        val words = YrcWordRegex.findAll(line.groupValues[2]).map { word ->
            val start = word.groupValues[1].toLong()
            TimedWord(word.groupValues[3], start, start + word.groupValues[2].toLong())
        }.toList()
        TimedLine(line.groupValues[1].toLong(), words.joinToString("") { it.text }.trim(), words)
    }.toList()

    private const val SearchLimit = 10
    private val YrcLineRegex = Regex("""^\[(\d+),\d+](.*)$""")
    // a word runs until the next "(start,length,0)" marker so brackets inside the words survive
    private val YrcWordRegex = Regex("""\((\d+),(\d+),-?\d+\)(.*?)(?=\(\d+,\d+,-?\d+\)|$)""")
}
