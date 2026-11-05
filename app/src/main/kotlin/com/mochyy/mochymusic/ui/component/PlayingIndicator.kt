// PlayingIndicator.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.EnterExitState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import kotlinx.coroutines.launch
import kotlin.random.Random

/** covers at least this wide count as big: Home cards and grids, rather than list thumbnails */
private val LargeCoverMinWidth = 96.dp

/** the rounded play glyph from the Figma (Image 6), light grey with no circle behind it */
val CoverGlyphColor = Color(0xFFD9D9D9)

/**
 * five small equaliser bars. each one grows out from its middle, up and down at once, rather than
 * up from the floor, and wanders between heights on its own clock.
 */
@Composable
fun PlayingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 5,
    barWidth: Dp = 2.5.dp,
    spacing: Dp = 2.dp,
) {
    val levels = remember(bars) { List(bars) { Animatable(0.3f + 0.4f * Random.nextFloat()) } }
    // Holds still while the player covers the app: the bars bounce forever otherwise, drawing a
    // frame every few milliseconds behind it. Likewise while the page it's on is opening, closing
    // or sinking behind another: that page is one cached picture then, and bars moving in it made
    // the whole page render again for every one of their frames.
    val obscured = com.mochyy.mochymusic.LocalAppContentObscured.current
    val navTransition = LocalNavAnimatedContentScope.current?.transition
    val held = remember(obscured, navTransition) {
        derivedStateOf {
            obscured.value || (navTransition != null &&
                (navTransition.currentState != EnterExitState.Visible || navTransition.targetState != EnterExitState.Visible))
        }
    }

    LaunchedEffect(levels) {
        levels.forEach { level ->
            launch {
                while (true) {
                    if (held.value) snapshotFlow { held.value }.first { !it }
                    level.animateTo(
                        targetValue = 0.22f + Random.nextFloat() * 0.78f,
                        animationSpec = tween(
                            durationMillis = 150 + Random.nextInt(140),
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
        }
    }

    // the bars move on every frame for as long as a song plays: bounded, that repaints these few
    // pixels instead of the whole screen and the nav bar's and mini player's glass along with it
    Canvas(modifier = modifier.width(barWidth * bars + spacing * (bars - 1)).damageBoundary()) {
        val w = barWidth.toPx()
        val gap = spacing.toPx()
        val radius = CornerRadius(w / 2f)
        levels.forEachIndexed { index, level ->
            val h = size.height * level.value
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (w + gap), (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * what an active cover shows: the bars while it plays, the play glyph while it's paused.
 * big covers keep the art visible: a light dim, the bars at the left middle and the glyph down in
 * the bottom right corner. list thumbnails keep everything centred, just smaller than before.
 */
@Composable
fun PlayingIndicatorBox(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    playWhenReady: Boolean,
    color: Color = Color.White,
    dimColor: Color = Color.Black,
    shape: Shape? = null,
) = PlayingIndicatorBox(modifier, isActive, { playWhenReady }, color, dimColor, shape)

// the same box, but playWhenReady is read only while it shows, so a play or pause skips every idle cover
@Composable
fun PlayingIndicatorBox(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    playWhenReady: () -> Boolean,
    color: Color = Color.White,
    dimColor: Color = Color.Black,
    shape: Shape? = null,
) {
    // starts at the current value, so a cover that is already active doesn't fade in
    val visibleState = remember { MutableTransitionState(isActive) }
    visibleState.targetState = isActive
    // a hidden cover that isn't mid-fade builds no transition at all
    if (!isActive && visibleState.isIdle) return

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(500)),
        exit = fadeOut(tween(500)),
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val large = maxWidth >= LargeCoverMinWidth
            if (shape != null && dimColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(dimColor.copy(alpha = if (large) 0.3f else ActiveBoxAlpha), shape)
                )
            }
            if (large) {
                if (playWhenReady()) {
                    // the same bottom-right slot as the play glyph, so playing and paused swap in place
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 9.dp)
                            .size(28.dp),
                    ) {
                        PlayingIndicator(
                            color = color,
                            barWidth = 3.dp,
                            spacing = 2.5.dp,
                            modifier = Modifier.height(18.dp),
                        )
                    }
                } else {
                    CoverPlayGlyph(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 9.dp),
                    )
                }
            } else {
                val side = maxWidth.coerceAtMost(maxHeight)
                if (playWhenReady()) {
                    PlayingIndicator(
                        color = color,
                        barWidth = 2.dp,
                        spacing = 1.5.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .height((side * 0.3f).coerceIn(10.dp, 15.dp)),
                    )
                } else {
                    val glyph = (side * 0.26f).coerceIn(10.dp, 14.dp)
                    Icon(
                        painter = painterResource(R.drawable.ic_untitled_play),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .align(Alignment.Center)
                            // a triangle looks off-centre when it's centred; nudge it right a hair
                            .offset(x = glyph * 0.08f)
                            .size(width = glyph * 0.9f, height = glyph),
                    )
                }
            }
        }
    }
}

/** the 28dp play button from the Figma, for the corner of a big cover */
@Composable
fun CoverPlayGlyph(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_untitled_play),
            contentDescription = null,
            tint = CoverGlyphColor,
            modifier = Modifier
                .offset(x = 1.5.dp)
                .size(width = 17.dp, height = 19.dp),
        )
    }
}
