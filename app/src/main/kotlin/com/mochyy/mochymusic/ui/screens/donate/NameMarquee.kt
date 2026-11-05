// the blue band at the top of the support sheet, two rows of huge MOCHY MUSIC gliding past each other

package com.mochyy.mochymusic.ui.screens.donate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.damageBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

private const val MarqueeText = "MOCHY MUSIC"

// the band is this many capital-heights tall: two rows, the gap between them, a sliver under the bottom one, the top one tucked a bit past the edge
private const val BandInCaps = 2.13f
private const val RowGapInCaps = 0.1f
private const val TopTuckInCaps = 0.04f

// space between one MOCHY MUSIC and the next, as a share of the capital height
private const val WordGapInCaps = 0.45f

// the bottom row starts this far through a word, so the two rows never line up
private const val BottomRowHeadStart = 0.42f

// a long pause (app in the background) shouldn't make the words leap ahead when it comes back
private const val MaxFrameStepNanos = 50_000_000L
private const val FadeInNanos = 300_000_000L

val MarqueeBlue = Color(0xFF9FC6E2)
private val MarqueeInk = Color(0xFFE6F0F7)

@Immutable
private class MarqueeStrip(val image: ImageBitmap, val capHeight: Float, val margin: Float)

/** two rows of MOCHY MUSIC on the blue band, the top one drifting right and the bottom one left, forever */
@Composable
fun NameMarquee(modifier: Modifier = Modifier, speed: Dp = 34.dp) {
    val context = LocalContext.current
    val speedPx = with(LocalDensity.current) { speed.toPx() }
    var bandHeight by remember { mutableStateOf(0) }

    // the words are drawn into a picture once, off the main thread, so every frame after that only slides the picture
    val strip by produceState<MarqueeStrip?>(null, bandHeight) {
        if (bandHeight > 0) value = withContext(Dispatchers.Default) { buildStrip(context, bandHeight) }
    }

    val elapsedNanos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(strip != null) {
        if (strip == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                elapsedNanos.longValue += (now - last).coerceAtMost(MaxFrameStepNanos)
                last = now
            }
        }
    }

    Box(
        modifier
            .onSizeChanged { bandHeight = it.height }
            // only this band repaints each frame, not the whole sheet
            .damageBoundary()
            .drawBehind {
                drawRect(MarqueeBlue)
                val ready = strip ?: return@drawBehind
                val elapsed = elapsedNanos.longValue
                // in doubles, a float would start to wobble after the sheet sat open for a few hours
                val travelled = elapsed / 1_000_000_000.0 * speedPx
                val alpha = (elapsed.toFloat() / FadeInNanos).coerceAtMost(1f)
                val cap = ready.capHeight
                val unit = ready.image.width - ready.margin * 2 + cap * WordGapInCaps

                val topRowStart = (travelled % unit).toFloat() - unit
                drawRow(ready, startX = topRowStart, capTop = -cap * TopTuckInCaps, unit = unit, alpha = alpha)

                val bottomRowStart = -((travelled + unit * BottomRowHeadStart) % unit).toFloat()
                val bottomCapTop = cap * (1f - TopTuckInCaps + RowGapInCaps)
                drawRow(ready, startX = bottomRowStart, capTop = bottomCapTop, unit = unit, alpha = alpha)
            },
    )
}

// lays copies of the words side by side from startX until the band is covered
private fun DrawScope.drawRow(strip: MarqueeStrip, startX: Float, capTop: Float, unit: Float, alpha: Float) {
    var x = startX
    while (x < size.width) {
        drawImage(strip.image, topLeft = Offset(x - strip.margin, capTop - strip.margin), alpha = alpha)
        x += unit
    }
}

private fun buildStrip(context: Context, bandHeight: Int): MarqueeStrip {
    val capHeight = bandHeight / BandInCaps
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.inter_extrabold)
        color = MarqueeInk.toArgb()
        letterSpacing = -0.03f
        textSize = 100f
    }
    // size the font by its real capital height, so the rows fill the band exactly whatever the font
    val capAt100 = Rect().also { paint.getTextBounds("M", 0, 1, it) }.height()
    paint.textSize = 100f * capHeight / capAt100

    // round letters dip a little past the baseline and the cap line, the margin keeps them whole
    val margin = ceil(capHeight * 0.05f) + 2f
    val width = ceil(paint.measureText(MarqueeText) + margin * 2).toInt()
    val height = ceil(capHeight + margin * 2).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawText(MarqueeText, margin, margin + capHeight, paint)
    bitmap.prepareToDraw()
    return MarqueeStrip(bitmap.asImageBitmap(), capHeight, margin)
}
