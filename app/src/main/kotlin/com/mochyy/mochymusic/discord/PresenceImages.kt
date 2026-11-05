// PresenceImages.kt

package com.mochyy.mochymusic.discord

import com.mochyy.mochymusic.core.updater.GithubOwner
import com.mochyy.mochymusic.core.updater.GithubRepo
import com.mochyy.mochymusic.ui.utils.resize
import kotlin.math.roundToInt

/** A status picture before Discord has it: what to show, not yet where it lives. */
/** How the rotating artwork turns: one full turn every [secondsPerTurn], drawn at [fps]. */
data class SpinStyle(val secondsPerTurn: Int, val fps: Int)

sealed interface PresencePicture {
    data class Web(val url: String) : PresencePicture

    /** the play/pause badge, with its ring [step] of [PresenceImages.StatusSteps] round */
    data class Status(val step: Int, val playing: Boolean) : PresencePicture

    /** the rotating disc made from [sourceUrl], turning as [style] says */
    data class Spinning(val sourceUrl: String, val style: SpinStyle) : PresencePicture

    data object Logo : PresencePicture
}

/**
 * Picks the picture for each image slot. The badge and logo are plain images in this repo
 * (assets/discord, drawn by scripts/discord_presence_images.py), which Discord fetches from GitHub.
 */
object PresenceImages {
    /** the ring moves in 5% steps: finer than that doesn't show on a 40px badge */
    const val StatusSteps = 20

    // bump with VERSION in the script: Discord keeps its own copy of each address
    private const val AssetsBase = "https://raw.githubusercontent.com/$GithubOwner/$GithubRepo/main/assets/discord/v1"

    const val LogoUrl = "$AssetsBase/mochy.png"

    fun statusUrl(status: PresencePicture.Status): String {
        val state = if (status.playing) "playing" else "paused"
        return "$AssetsBase/status_${state}_${status.step.toString().padStart(2, '0')}.png"
    }

    /** What [source] shows for [track]; artwork that's missing falls back to the next best thing. */
    fun pick(source: PresenceImage, track: NowPlaying, nowMs: Long, spin: SpinStyle): PresencePicture? {
        // YouTube hands out small thumbnails; Discord shows the big picture at up to 240px
        val artwork = track.artworkUrl?.takeIf(::isWebImage)?.resize(512, 512)
        val artistArtwork = track.artistArtworkUrl?.takeIf(::isWebImage)?.resize(512, 512)
        return when (source) {
            PresenceImage.OFF -> null
            PresenceImage.ARTWORK -> artwork?.let(PresencePicture::Web) ?: PresencePicture.Logo
            PresenceImage.ARTIST -> (artistArtwork ?: artwork)?.let(PresencePicture::Web) ?: PresencePicture.Logo
            PresenceImage.APP_LOGO -> PresencePicture.Logo
            PresenceImage.PLAYBACK_STATUS -> PresencePicture.Status(
                step = (track.progressAt(nowMs) * StatusSteps).roundToInt().coerceIn(0, StatusSteps),
                playing = track.isPlaying,
            )
            PresenceImage.SPINNING_ARTWORK -> artwork?.let { PresencePicture.Spinning(it, spin) } ?: PresencePicture.Logo
            PresenceImage.SPINNING_ARTIST ->
                (artistArtwork ?: artwork)?.let { PresencePicture.Spinning(it, spin) } ?: PresencePicture.Logo
        }
    }

    /** a local file or content:// cover can't be fetched by Discord */
    private fun isWebImage(url: String) = url.startsWith("https://") || url.startsWith("http://")
}
