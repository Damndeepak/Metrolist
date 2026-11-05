// ShareColourSpectrum.kt
//
// The full colour spectrum for a share's plain background: every hue left to right, white at the
// top fading through the pure colour to black at the bottom. Drag anywhere on it and the picture
// changes as you go.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** The hue strip's stops: at full strength the hue wheel is straight lines between these, so the strip matches [colourAt] exactly. */
private val HueStops = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/** A light tick each time the finger crosses into the next of this many slices of the rainbow. */
private const val HapticSlices = 12

/** A point on the spectrum: [hue] 0 to 1 across, [shade] 0 (white) to 1 (black) down. */
private data class Spot(val hue: Float, val shade: Float)

@Composable
internal fun ColourSpectrum(colour: Color, onPick: (Color) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    var spot by remember { mutableStateOf(spotOf(colour)) }
    var dragging by remember { mutableStateOf(false) }
    // a colour chosen elsewhere (a swatch) moves the ring to roughly where it sits here
    LaunchedEffect(colour) {
        if (colourAt(spot).toArgb() != colour.toArgb()) spot = spotOf(colour)
    }
    // the ring follows the finger exactly, but glides when a swatch moves it
    val follow = if (dragging) snap<Float>() else spring(dampingRatio = 0.75f, stiffness = 380f)
    val ringX by animateFloatAsState(spot.hue, follow, label = "spectrumX")
    val ringY by animateFloatAsState(spot.shade, follow, label = "spectrumY")

    Spacer(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                fun choose(at: Offset) {
                    val next = Spot((at.x / size.width).coerceIn(0f, 1f), (at.y / size.height).coerceIn(0f, 1f))
                    if ((next.hue * HapticSlices).toInt() != (spot.hue * HapticSlices).toInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    spot = next
                    pick(colourAt(next))
                }
                awaitEachGesture {
                    // taken straight away, so a drag up or down picks a shade instead of scrolling the sheet
                    val down = awaitFirstDown()
                    dragging = true
                    down.consume()
                    choose(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        choose(change.position)
                    }
                    dragging = false
                }
            }
            .drawWithCache {
                val hues = Brush.horizontalGradient(HueStops)
                val half = size.height / 2f
                val toWhite = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), startY = 0f, endY = half)
                val toBlack = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black), startY = half, endY = size.height)
                onDrawBehind {
                    drawRect(hues)
                    drawRect(toWhite, size = Size(size.width, half))
                    drawRect(toBlack, topLeft = Offset(0f, half), size = Size(size.width, half))

                    val at = Offset(ringX * size.width, ringY * size.height)
                    val radius = 13.dp.toPx()
                    drawCircle(colourAt(Spot(ringX, ringY)), radius, at)
                    drawCircle(Color.Black.copy(alpha = 0.25f), radius + 2.dp.toPx(), at, style = Stroke(1.dp.toPx()))
                    drawCircle(Color.White, radius, at, style = Stroke(3.dp.toPx()))
                }
            },
    )
}

/** Exactly the colour the spectrum shows at [spot]: the pure hue blended toward white above the middle, toward black below. */
private fun colourAt(spot: Spot): Color {
    val base = Color.hsv((spot.hue * 360f).coerceIn(0f, 359.99f), 1f, 1f)
    return if (spot.shade < 0.5f) {
        val white = 1f - spot.shade * 2f
        Color(base.red + (1f - base.red) * white, base.green + (1f - base.green) * white, base.blue + (1f - base.blue) * white)
    } else {
        val black = spot.shade * 2f - 1f
        Color(base.red * (1f - black), base.green * (1f - black), base.blue * (1f - black))
    }
}

/**
 * Roughly where a colour sits on the spectrum. Only white-ish and black-ish blends of a pure hue
 * are on it exactly; anything greyer lands on the nearest of the two.
 */
private fun spotOf(colour: Color): Spot {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colour.toArgb(), hsv)
    val (hue, saturation, value) = Triple(hsv[0] / 360f, hsv[1], hsv[2])
    val shade = if (1f - value < 1f - saturation) saturation / 2f else 1f - value / 2f
    return Spot(hue, shade)
}
