// ScrollAwayCover.kt

package com.mochyy.mochymusic.ui.component

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.constants.ElementBlurKey
import com.mochyy.mochymusic.utils.rememberPreference

private const val CoverZoom = 0.22f
private const val CoverSoftBy = 0.55f
private const val CoverFadeFrom = 0.2f
private const val CoverFadeTo = 0.85f
private const val CoverDim = 0.22f
private const val CoverSoftRadius = 30f

/** 0 up to [from], 1 from [to] on, an s-curve in between */
internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** whether glass can blur here: android 12 and up, and the user hasn't turned blur off */
@Composable
fun rememberCoverBlurAvailable(): Boolean {
    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    return !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

// whether a thing can blur itself (the cover zooming away, a ring fading out): android 12 and up, with element blur on
@Composable
fun rememberElementBlurAvailable(): Boolean {
    val (elementBlur) = rememberPreference(ElementBlurKey, defaultValue = true)
    return elementBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/** what a big cover does as the list scrolls it away: rides up, zooms, blurs, dims, dissolves from the bottom and fades; the Home hero and the playlist backdrop share it */
@Composable
fun ScrollAwayCover(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    // extra travel as a share of its height: about -0.8 for a backdrop pinned behind a list, +0.2 for an item that already scrolls
    follow: Float = 0f,
    // holds the blur back until this much progress, for content that is still moving at rest
    softFrom: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val frameBlur = rememberElementBlurAvailable()
    val highQuality = LocalBlurQuality.current == BlurQuality.HIGH
    val frame = rememberGraphicsLayer()

    Box(
        modifier = modifier
            .graphicsLayer {
                val p = progress()
                translationY = p * size.height * follow
                val zoom = 1f + p * CoverZoom
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 0.35f)
                alpha = 1f - smoothstep(CoverFadeFrom, CoverFadeTo, p)
                // the bottom dissolve cuts into its own buffer; at rest there's nothing to cut
                compositingStrategy = if (p > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                val p = progress()
                if (p > 0f) {
                    drawRect(Color.Black.copy(alpha = CoverDim * p))
                    // the dissolve only changes its bottom band: above it the gradient is solid,
                    // and DstIn with solid is nothing, so only the band is drawn
                    val clear = 0.1f + 0.45f * smoothstep(0f, 0.8f, p)
                    val top = size.height * (1f - clear)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                            startY = top,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, size.height - top),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        // the sharp frame. it sits on its own layer and never reads the scroll while drawing, so
        // scrolling doesn't record it again (which would make the soft copy blur again). once the
        // scroll has it moving, that layer keeps its own buffer: the dim and the dissolve above
        // redraw on every frame of the scroll, and they'd otherwise draw the whole frame (picture,
        // glass bands, scrims, text) again underneath each time instead of one cached picture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = if (progress() > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .then(
                    if (frameBlur) {
                        Modifier.drawWithContent {
                            frame.record { this@drawWithContent.drawContent() }
                            drawLayer(frame)
                        }
                    } else {
                        Modifier
                    }
                ),
            content = content,
        )
        if (frameBlur && highQuality) {
            // high quality: the same recording blurred more as it scrolls; fading a fixed blur over the sharp frame made its text bloom
            DownscaledBlur(
                radius = { CoverSoftRadius * smoothstep(softFrom, CoverSoftBy, progress()) },
                tileMode = Shader.TileMode.CLAMP,
                mask = null,
                modifier = Modifier
                    .matchParentSize()
                    // at rest the sharp frame below already shows everything, so nothing is drawn or blurred here
                    .graphicsLayer { alpha = if (progress() > softFrom) 1f else 0f },
            ) {
                drawLayer(frame)
            }
        } else if (frameBlur) {
            // low quality: one fixed blur faded in, cheaper while scrolling but it blooms a little around text
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val soft = smoothstep(softFrom, CoverSoftBy, progress())
                        alpha = soft
                        // a blur layer re-blurs on every frame change even at alpha 0, so it's only switched on while it shows
                        renderEffect = if (soft > 0f) BlurEffectCache.get(CoverSoftRadius, Shader.TileMode.CLAMP) else null
                        clip = true
                    }
                    .drawBehind { drawLayer(frame) },
            )
        }
    }
}
