package com.mochyy.mochymusic.ui.screens.update

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.component.rememberElementBlurAvailable
import kotlin.math.PI
import kotlin.math.floor

private const val RingScrollZoom = 0.42f
private const val RingMaxBlurRadius = 28f

// decal tile mode only exists on android 12+, keeping it in a guarded helper avoids loading the field on older phones
@RequiresApi(Build.VERSION_CODES.S)
private fun ringBlurEffect(radius: Float): RenderEffect? =
    BlurEffectCache.get(radius, Shader.TileMode.DECAL)

/** the thick ring from the mock with text running round it, turning slowly, zooming and blurring away as the sheet scrolls */
@Composable
fun VersionRing(
    text: String,
    modifier: Modifier = Modifier,
    scrollProgress: () -> Float = { 0f },
    radius: Dp = 140.dp,
    band: Dp = 46.dp,
    ringColor: Color = Color.White.copy(alpha = 0.08f),
    textColor: Color = Color.White.copy(alpha = 0.3f),
    turnMillis: Int = 26_000,
) {
    val canBlur = rememberElementBlurAvailable()
    val latestProgress = rememberUpdatedState(scrollProgress)
    val spinning by remember { derivedStateOf { latestProgress.value() < 0.99f } }
    val turn = if (spinning) {
        rememberInfiniteTransition(label = "versionRing").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(turnMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "versionRingTurn",
        ).value
    } else {
        0f
    }

    Box(
        modifier
            .size(radius * 2)
            .graphicsLayer {
                val p = latestProgress.value().coerceIn(0f, 1f)
                val eased = p * p * (3f - 2f * p)
                val scale = 1f + RingScrollZoom * eased
                scaleX = scale
                scaleY = scale
                // rides up with the app info header as it zooms so the ring stays inside the header area
                translationY = -p * 72.dp.toPx()
                val fade = 1f - eased
                alpha = fade
                renderEffect = if (canBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && p > 0.005f && fade > 0.01f) {
                    ringBlurEffect(RingMaxBlurRadius * eased)
                } else {
                    null
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .damageBoundary()
                .graphicsLayer { rotationZ = turn }
                .drawWithCache {
                    val bandPx = band.toPx()
                    val mid = radius.toPx() - bandPx / 2f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textColor.toArgb()
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        textSize = bandPx * 0.5f
                    }
                    // runs the way the mock reads: down the left side, M at the top
                    val path = Path().apply { addCircle(size.width / 2f, size.height / 2f, mid, Path.Direction.CCW) }
                    val circumference = (2 * PI * mid).toFloat()
                    val unit = "$text  ·  "
                    val repeats = floor(circumference / paint.measureText(unit)).toInt().coerceAtLeast(1)
                    val step = circumference / repeats
                    // the baseline sits half a capital below the middle of the band, so letters centre in it
                    val caps = Rect().also { paint.getTextBounds("M", 0, 1, it) }
                    val lift = caps.height() / 2f
                    onDrawBehind {
                        drawCircle(ringColor, radius = mid, style = Stroke(bandPx))
                        drawIntoCanvas { canvas ->
                            repeat(repeats) { i ->
                                canvas.nativeCanvas.drawTextOnPath(unit, path, i * step, lift, paint)
                            }
                        }
                    }
                },
        )
    }
}
