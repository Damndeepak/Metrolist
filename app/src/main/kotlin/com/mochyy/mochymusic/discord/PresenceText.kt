// PresenceText.kt

package com.mochyy.mochymusic.discord

/** The words the user can drop into any status line or button, and Discord's length rules. */
object PresenceText {
    const val AppName = "Mochy"

    /** the placeholder, and what it means, in the order the Discord page lists them */
    val Placeholders = listOf(
        "{name}" to "Song name",
        "{artist}" to "Artist name",
        "{album}" to "Album",
        "{app}" to AppName,
        "{quality}" to "Format and bitrate, like OPUS · 160 kbps",
        "{apple}" to "The Apple logo (only Apple devices can show it)",
    )

    fun fill(template: String, track: NowPlaying): String = template
        .replace("{name}", track.title)
        .replace("{artist}", track.artist)
        .replace("{album}", track.album.orEmpty())
        .replace("{app}", AppName)
        .replace("{quality}", track.quality.orEmpty())
        .replace("{apple}", "")

    /**
     * A filled-in line as Discord takes it: null when there's nothing left to show (so the line is
     * left off), padded when it's one letter (Discord turns those down), cut at Discord's limit.
     */
    fun line(template: String, track: NowPlaying, maxLength: Int = 128): String? {
        val text = fill(template, track).trim().replace(Whitespace, " ")
        return when {
            text.isEmpty() -> null
            text.length == 1 -> text + Blank
            text.length > maxLength -> text.take(maxLength - 1).trimEnd() + "…"
            else -> text
        }
    }

    private val Whitespace = Regex("\\s+")

    /** an empty-looking character that still counts as one, for Discord's two-letter minimum */
    private const val Blank = "⠀"
}
