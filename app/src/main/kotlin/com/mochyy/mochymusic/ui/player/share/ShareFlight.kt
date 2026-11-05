// ShareFlight.kt
//
// The held lyric line on its way between the lyrics and its row in the line picker. It starts as an
// exact copy of the line, zooms in and lights up, then flies to the row along a gentle curve,
// shrinking into the row's text and taking on the row's colours as it lands. Closing flies it back
// and lets the real line take over as it settles.
//
// Everything is drawn from the share's one clock in the draw phase, so the flight never recomposes
// or re-measures while it moves.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.mochyy.mochymusic.ui.component.smoothstep
import com.mochyy.mochymusic.ui.player.LyricsAlignment
import kotlin.math.roundToInt

@Composable
internal fun ShareFlight(session: LyricsShareSession) {
    val source = session.flightSource ?: return
    val choreo = session.choreography
    val measurer = rememberTextMeasurer()
    val clip = remember { Path() }
    // the last place the line was seen, for when it scrolls out of the lyrics mid-flight
    val lastSource = remember { arrayOfNulls<Rect>(1) }

    LaunchedEffect(Unit) {
        // one frame drawn first, so the lyrics never show a gap where neither copy is
        withFrameNanos { }
        choreo.pillShown = true
    }

    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (!choreo.pillVisible()) return@drawBehind
                val live = source.bounds()
                val from = live ?: lastSource[0] ?: return@drawBehind
                lastSource[0] = from
                val to = session.flightTarget?.takeIf { it.isAttached }?.fullBoundsInWindow()
                // flying back to a line that has scrolled away: fade out on the way instead
                val gone = if (live == null) choreo.travel() else 1f
                drawFlight(session, source, from, to, measurer, clip, choreo.pillAlpha() * gone)
            },
    )
}

private fun DrawScope.drawFlight(
    session: LyricsShareSession,
    source: ShareFlightSource,
    from: Rect,
    to: Rect?,
    measurer: TextMeasurer,
    clip: Path,
    alpha: Float,
) {
    if (alpha <= 0f) return
    val choreo = session.choreography
    val insets = source.insets
    val padH = insets.padH.toPx()
    val padV = insets.padV.toPx()
    val startInset = insets.start.toPx()
    val endInset = insets.end.toPx()
    val room = (from.width - startInset - endInset - 2 * padH).roundToInt().coerceAtLeast(1)
    val lyric = measurer.measure(source.text, source.style, constraints = Constraints(maxWidth = room))

    // The line's tap highlight, hugging its words, exactly where the lyrics page has it
    val hugWidth = lyric.size.width + 2 * padH
    val hugLeft = when (source.alignment) {
        LyricsAlignment.START -> from.left + startInset
        LyricsAlignment.CENTER -> from.left + startInset + (from.width - startInset - endInset - hugWidth) / 2f
        LyricsAlignment.END -> from.right - endInset - hugWidth
    }
    val hug = Rect(hugLeft, from.top + insets.top.toPx(), hugLeft + hugWidth, from.top + insets.top.toPx() + lyric.size.height + 2 * padV)
    // ...scaled the way the line is, about the same point its own zoom uses
    val pivot = Offset(
        when (source.alignment) {
            LyricsAlignment.START -> from.left + startInset
            LyricsAlignment.CENTER -> (from.left + startInset + from.right - endInset) / 2f
            LyricsAlignment.END -> from.right - endInset
        },
        from.center.y,
    )
    val lift = choreo.lift()
    val liftScale = source.scale * lerp(1f, ShareLiftZoom, lift)
    val lifted = hug.scaledAbout(pivot, liftScale)

    // Sideways and down at slightly different paces, so the line swoops instead of sliding
    val t = choreo.travel()
    val ex = easeInOut(t)
    val ey = easeInOut((t * 1.15f).coerceAtMost(1f))
    val land = to ?: lifted
    val box = Rect(
        lerp(lifted.left, land.left, ex),
        lerp(lifted.top, land.top, ey),
        lerp(lifted.right, land.right, ex),
        lerp(lifted.bottom, land.bottom, ey),
    )
    val corner = lerp(14.dp.toPx() * liftScale, ShareRowCorner.toPx(), ex)

    translate(box.left, box.top) {
        val size = box.size
        // a soft highlight while it lifts, the row's own look as it lands
        drawRoundRect(Color.White.copy(alpha = 0.13f * lift * (1f - ex) * alpha), size = size, cornerRadius = CornerRadius(corner))
        val landed = smoothstep(0.2f, 0.85f, ex) * alpha
        if (landed > 0f) {
            val picked = session.isPicked(session.held)
            drawShareRowLook(
                fill = if (picked && !session.pastCard(session.held)) 1f else 0f,
                outline = if (picked && session.pastCard(session.held)) 1f else 0f,
                palette = session.palette,
                corner = corner,
                size = size,
                alpha = landed,
            )
        }

        clip.reset()
        clip.addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
        clipPath(clip) {
            // the lyric shrinks into the row's text, then the row's own text takes over
            val swap = smoothstep(0.55f, 0.95f, ex)
            val textScale = lerp(liftScale, ShareRowFontSize.value / source.style.fontSize.value, ex)
            val brightness = lerp(source.alpha, 1f, lift)
            val textLeft = lerp(padH * liftScale, ShareRowPadH.toPx(), ex)
            val textTop = (size.height - lyric.size.height * textScale) / 2f
            translate(textLeft, textTop) {
                scale(textScale, pivot = Offset.Zero) {
                    drawText(lyric, color = Color.White, alpha = brightness * (1f - swap) * alpha)
                }
            }
            if (to != null && swap > 0f) {
                val row = rowText(measurer, source.text, to.width)
                translate(ShareRowPadH.toPx(), (size.height - row.size.height) / 2f) {
                    drawText(row, alpha = swap * alpha)
                }
            }
        }
    }
}

private fun DrawScope.rowText(measurer: TextMeasurer, text: String, rowWidth: Float): TextLayoutResult {
    val room = (rowWidth - 2 * ShareRowPadH.toPx()).roundToInt().coerceAtLeast(1)
    return measurer.measure(text, ShareRowTextStyle, constraints = Constraints(maxWidth = room))
}

private fun Rect.scaledAbout(pivot: Offset, s: Float) = Rect(
    pivot.x + (left - pivot.x) * s,
    pivot.y + (top - pivot.y) * s,
    pivot.x + (right - pivot.x) * s,
    pivot.y + (bottom - pivot.y) * s,
)

/**
 * Where a layout is in the window, without being cut down to what's on screen. A row still rising
 * with its sheet from below the screen keeps its real size, so the flight aims at the whole row.
 */
internal fun LayoutCoordinates.fullBoundsInWindow(): Rect {
    val root = findRootCoordinates()
    return root.localBoundingBoxOf(this, clipBounds = false).translate(root.positionInWindow())
}
