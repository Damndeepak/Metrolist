package com.mochyy.mochymusic.ui.player.video

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.DownscaledBlur
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalBlurQuality
import com.mochyy.mochymusic.ui.component.drawGlassNode
import com.mochyy.mochymusic.ui.component.glassNodeHasContent
import com.mochyy.mochymusic.ui.player.QueueScreen
import com.mochyy.mochymusic.ui.player.styles.smooth
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.roundToInt

// how hard the pane blurs the video under it, at its right side
private const val PaneBlurPx = 64f

// the share of the pane's width over which the video goes from sharp to fully blurred
private const val PaneBlurRamp = 0.34f

// the video's edge colour laid over the blur, so the pane takes on the picture's light
private const val PaneTintAlpha = 0.6f

// without blur the pane is that colour alone, faded in over the same ramp
private const val FlatTintAlpha = 0.9f

// the bar a finger slides up to open the pane, and that sits on top of it once open
private const val HandleRestAlpha = 0.1f
private const val HandleOpenAlpha = 0.5f
private val HandleWidth = 140.dp
private val HandleHeight = 4.dp
private val HandleTop = 16.dp
private val HandleBottom = 10.dp

// the pane's backdrop: the video under it blurred more and more towards the right, tinted with the picture's own edge colour
@Composable
internal fun BoxScope.VideoPaneBackdrop(
    glass: GlassState,
    frame: VideoFrame,
    state: VideoModeState,
    tint: State<Color>,
) {
    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    val blur = !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // one pass whose radius grows across the pane on High (android 13+); elsewhere one blur faded in over the ramp
    val progressive = blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        LocalBlurQuality.current == BlurQuality.HIGH
    val density = LocalDensity.current
    val paneLeftPx = with(density) { (frame.width - frame.paneWidth).toPx() }
    val slidePx = with(density) { frame.videoSlide.toPx() }
    val rampMask = remember(progressive) {
        if (progressive) null else Brush.horizontalGradient(0f to Color.Transparent, PaneBlurRamp to Color.Black, 1f to Color.Black)
    }

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .width(frame.paneWidth)
            .fillMaxHeight()
            .graphicsLayer { alpha = smooth(state.pane.value * 1.4f) },
    ) {
        if (blur) {
            DownscaledBlur(
                radius = { BlurEffectCache.effectiveRadius(PaneBlurPx) },
                // clamped, not faded: the pane runs to the screen's edges, there is nothing past them to fade into
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                mask = rampMask,
                growDown = if (progressive) true else null,
                growAcross = true,
                growRamp = PaneBlurRamp,
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                val node = glass.renderNode ?: return@DownscaledBlur
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !glassNodeHasContent(node)) return@DownscaledBlur
                // the video slides left as the pane opens, so its picture is read from where it is right now
                val videoLeft = -slidePx * state.pane.value
                translate(left = videoLeft - paneLeftPx) {
                    drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val color = tint.value
                    val strength = if (blur) PaneTintAlpha else FlatTintAlpha
                    drawRect(
                        Brush.horizontalGradient(
                            0f to color.copy(alpha = 0f),
                            PaneBlurRamp to color.copy(alpha = strength),
                            1f to color.copy(alpha = strength),
                        )
                    )
                },
        )
    }
}

// what the pane holds, rising in as it opens
@Composable
internal fun BoxScope.VideoPaneContent(
    frame: VideoFrame,
    state: VideoModeState,
    lyrics: VideoLyrics,
    look: VideoLyricLook,
    positionProvider: () -> Long,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .width(frame.paneWidth)
            .fillMaxHeight(),
    ) {
        when (state.paneContent) {
            VideoPane.LYRICS -> VideoPaneLyrics(
                lyrics = lyrics,
                look = look,
                frame = frame,
                state = state,
                positionProvider = positionProvider,
            )

            VideoPane.QUEUE -> QueueScreen(
                onClose = state::closePane,
                contentBottomInset = 0.dp,
                controlsClearance = 0.dp,
                showHeader = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = PaneListTop)
                    .graphicsLayer {
                        val p = state.pane.value
                        alpha = smooth(p)
                        translationY = (1f - p) * 56.dp.toPx()
                    },
            )
        }
    }
}

// the little bar: at the bottom under the pane's place while it's away, sliding up to the pane's top as it opens
@Composable
internal fun BoxScope.VideoPaneHandle(
    frame: VideoFrame,
    state: VideoModeState,
) {
    val density = LocalDensity.current
    val travel = with(density) { (frame.height - HandleTop - HandleBottom - HandleHeight).toPx() }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = (frame.paneWidth - HandleWidth) / 2, top = HandleTop)
            .offset {
                IntOffset(0, (travel * (1f - state.pane.value)).roundToInt())
            }
            .graphicsLayer {
                // barely there at the bottom, a hint more on top of the open pane
                alpha = HandleRestAlpha + (HandleOpenAlpha - HandleRestAlpha) * state.pane.value
            }
            .size(HandleWidth, HandleHeight)
            .clip(CircleShape)
            .background(Color.White),
    )
    // turned sideways, the screen's bottom edge is the phone's right one: without this a swipe up from the bar is taken as back
    val closed by remember { derivedStateOf { state.pane.value < 0.001f && !state.paneDragging } }
    if (closed) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (frame.paneWidth - HandleExclusionWidth) / 2)
                .size(HandleExclusionWidth, HandleExclusionHeight)
                .systemGestureExclusion(),
        )
    }
}

// the stretch of edge kept from the system's back swipe around the bar; android allows 200dp an edge
private val HandleExclusionWidth = 180.dp
private val HandleExclusionHeight = 48.dp

// a tap here opens or closes the pane, in frame px: the bar itself at the bottom while it's away, the pane's top strip while it's open
internal fun paneHandleZone(frame: VideoFrame, open: Boolean, x: Float, y: Float, density: androidx.compose.ui.unit.Density): Boolean =
    with(density) {
        val left = (frame.width - frame.paneWidth).toPx()
        if (open) return x >= left && y < 56.dp.toPx()
        val center = (frame.width - frame.paneWidth / 2).toPx()
        kotlin.math.abs(x - center) < (HandleExclusionWidth / 2).toPx() && y > (frame.height - HandleExclusionHeight).toPx()
    }

// a drag up opens the pane only from its own side, the bar's side, so a stray swipe on the video doesn't pull it up
internal fun paneSide(frame: VideoFrame, x: Float, density: androidx.compose.ui.unit.Density): Boolean =
    with(density) { x >= (frame.width - frame.paneWidth).toPx() }
