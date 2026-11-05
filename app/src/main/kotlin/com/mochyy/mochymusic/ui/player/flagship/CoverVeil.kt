package com.mochyy.mochymusic.ui.player.flagship

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import com.mochyy.mochymusic.ui.player.CarouselPictures
import com.mochyy.mochymusic.ui.player.SongCarousel

// the colour stays clear over the top of the blurred mirror and rises toward the bottom of the screen
private const val VeilFrom = 0.3f
private const val VeilStrength = 0.7f

// under the controls, from the cover's edge down: each song's own colour rising toward the bottom, on its cover's page so a swipe slides it in and out with the cover
@Composable
fun CoverVeil(
    carousel: SongCarousel?,
    // the playing song's picture, and the colours that follow its video or animated cover while one plays
    playingUrl: String?,
    live: AmbientColors,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    CarouselPictures(
        carousel = carousel,
        fallbackUrl = playingUrl,
        modifier = modifier,
    ) { url, _ ->
        // a flat wash has nothing to shift for depth, so it keeps the page's own place
        VeilPage(url = url, live = if (url == playingUrl) live else null, alpha = alpha)
    }
}

@Composable
private fun VeilPage(url: String?, live: AmbientColors?, alpha: () -> Float) {
    // the song's own colours, worked out from its picture before it ever slides in
    val own = rememberAmbientColors()
    FollowPicture(own, url)
    // the live colours take over only once a video or animated cover is really feeding them, so a song change never jumps
    val colors = if (live != null && live.isLive) live else own
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val seam = colors.seam()
                val veil = Brush.verticalGradient(
                    0f to seam.copy(alpha = 0f),
                    VeilFrom to seam.copy(alpha = 0f),
                    1f to seam.copy(alpha = VeilStrength),
                )
                onDrawBehind {
                    val a = alpha().coerceIn(0f, 1f)
                    if (a > 0.004f) drawRect(veil, alpha = a)
                }
            },
    )
}
