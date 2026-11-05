package com.mochyy.mochymusic.ui.player.ipod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import kotlin.math.max

// a cover to draw by hand, which keeps showing the last song's cover until the next one is ready and then fades over it
@Composable
fun rememberCoverPainter(url: String?, sizePx: Int): Painter {
    val context = LocalContext.current
    var lastKey by remember { mutableStateOf<MemoryCache.Key?>(null) }
    val request = remember(url, sizePx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(CoilSize(sizePx, sizePx))
            .placeholderMemoryCacheKey(lastKey)
            .crossfade(CoverCrossfadeMs)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    LaunchedEffect(painter) {
        painter.state.collect { state ->
            if (state is AsyncImagePainter.State.Success) lastKey = state.result.memoryCacheKey
        }
    }
    return painter
}

// the painter cropped to fill a square, the way ContentScale.Crop would
fun DrawScope.drawCoverSquare(painter: Painter, side: Float, blank: Color) {
    drawRect(blank, size = Size(side, side))
    val intrinsic = painter.intrinsicSize
    if (intrinsic.isUnspecifiedOrEmpty()) {
        with(painter) { draw(Size(side, side)) }
        return
    }
    val fill = max(side / intrinsic.width, side / intrinsic.height)
    val w = intrinsic.width * fill
    val h = intrinsic.height * fill
    clipRect(0f, 0f, side, side) {
        translate((side - w) / 2f, (side - h) / 2f) {
            with(painter) { draw(Size(w, h)) }
        }
    }
}

// draws the square cover mapped onto any four corners, and its mirror image fading out below when reflect is above zero
fun DrawScope.drawCoverQuad(
    painter: Painter,
    side: Float,
    corners: FloatArray,
    blank: Color,
    background: Color,
    reflect: Float,
    matrix: android.graphics.Matrix,
) {
    val canvas = drawContext.canvas.nativeCanvas
    matrix.setPolyToPoly(floatArrayOf(0f, 0f, side, 0f, side, side, 0f, side), 0, corners, 0, 4)
    canvas.save()
    canvas.concat(matrix)
    drawCoverSquare(painter, side, blank)
    canvas.restore()
    if (reflect <= 0f) return
    // the mirror: the cover's bottom edge stays put and its top folds down under it
    val mirrored = floatArrayOf(
        corners[6] + (corners[6] - corners[0]), corners[7] + (corners[7] - corners[1]),
        corners[4] + (corners[4] - corners[2]), corners[5] + (corners[5] - corners[3]),
        corners[4], corners[5],
        corners[6], corners[7],
    )
    matrix.setPolyToPoly(floatArrayOf(0f, 0f, side, 0f, side, side, 0f, side), 0, mirrored, 0, 4)
    canvas.save()
    canvas.concat(matrix)
    drawCoverSquare(painter, side, blank)
    // fades into the screen behind it, so no layer or mask is needed
    drawRect(
        Brush.verticalGradient(
            0f to background,
            (1f - reflect).coerceIn(0f, 1f) to background,
            1f to background.copy(alpha = ReflectionStrength),
            startY = 0f,
            endY = side,
        ),
        size = Size(side, side),
    )
    canvas.restore()
}

private fun Size.isUnspecifiedOrEmpty(): Boolean =
    this == Size.Unspecified || width <= 0f || height <= 0f || width.isInfinite() || height.isInfinite()

private const val CoverCrossfadeMs = 220
// how much of the background still covers the reflection right under the cover
private const val ReflectionStrength = 0.62f
