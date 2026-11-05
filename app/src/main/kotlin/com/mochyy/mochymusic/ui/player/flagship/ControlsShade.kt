package com.mochyy.mochymusic.ui.player.flagship

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

// the design's shade starts here on the screen (1370 of its 2548) and runs to the bottom
private const val ShadeTop = 0.538f

// clear at the top, 24% black by 46% of the way down, then held: just enough for white text over the blurred cover
private val Shade = Brush.verticalGradient(
    0f to Color.Transparent,
    0.46f to Color.Black.copy(alpha = 0.24f),
    1f to Color.Black.copy(alpha = 0.24f),
)

// the new flagship's dark shade under its controls, over the cover fading into its blurred copy; one gradient, no blur of its own
@Composable
fun ControlsShade(
    progressProvider: () -> Float,
    maxHeight: Dp,
    // gone while a page is up
    fadeProvider: () -> Float = { 1f },
    // how far the controls (and with them the cover's bottom) were moved, px down; read while placing only
    offsetProvider: () -> Float = { 0f },
) {
    val drawn by remember { derivedStateOf { progressProvider() > 0.5f && fadeProvider() > 0f } }
    if (!drawn) return
    val currentProgress by rememberUpdatedState(progressProvider)
    val currentFade by rememberUpdatedState(fadeProvider)
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxHeight * (1f - ShadeTop))
            .offset { IntOffset(0, (maxHeight.toPx() * ShadeTop + offsetProvider()).roundToInt()) }
            .drawWithCache {
                onDrawBehind {
                    val shown = ((currentProgress() - 0.5f) / 0.45f).coerceIn(0f, 1f) * currentFade().coerceIn(0f, 1f)
                    drawRect(Shade, alpha = shown)
                }
            },
    )
}
