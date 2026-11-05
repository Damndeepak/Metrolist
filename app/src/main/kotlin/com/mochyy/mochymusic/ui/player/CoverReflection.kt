// CoverReflection.kt
//
// The edge-to-edge cover mirrored under itself, heavily blurred and fading out downward, so the
// cover seems to spill into the moving backdrop. The picture is blurred once, small, off the main
// thread, and drawn stretched as one mesh whose vertex colours do the fade: no live blur and no
// offscreen layer, on every Android version.

package com.mochyy.mochymusic.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.mochyy.mochymusic.ui.utils.resize
import kotlin.math.max
import kotlin.math.roundToInt

// the picture is this small before it's blurred; stretched over the screen that's a soft, heavy blur for almost nothing
internal const val ReflectionPx = 96

// blur radius in those small pixels, roughly 95px on a phone screen
internal const val ReflectionBlurPx = 6

// how strong the mirror is where it meets the cover
private const val ReflectionStrength = 0.92f

// fully faded this far down its height, so it's gone well before the screen's bottom edge
private const val ReflectionFadeEnd = 0.9f

// rows in the mesh, spread over the whole picture; the fade is eased between them
private const val MeshRows = 32

// the cover's picture flipped under it; size this to start at the cover's bottom edge and as wide as the cover
@Composable
internal fun CoverReflection(
    url: String?,
    // the cover's height right now, px, so the mirror crops exactly as the cover does
    coverHeight: () -> Float,
    // how much the cover is zoomed about its centre (1.05 while playing)
    magnify: () -> Float,
    // 0 to 1, read while drawing, so fading it never needs a layer
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    // the new flagship's: kept whole all the way down, the cover's own picture blurred under the controls
    steady: Boolean = false,
) {
    if (url == null) return
    val context = LocalContext.current
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url.resize(ReflectionPx, ReflectionPx))
            .allowHardware(false)
            .transformations(BackdropBlurTransformation(radiusPx = ReflectionBlurPx))
            .crossfade(false)
            .size(CoilSize(ReflectionPx, ReflectionPx))
            .build()
    }
    val picture by produceState<Bitmap?>(null, request) {
        value = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
    }
    val mesh = remember { ReflectionMesh() }
    Spacer(
        modifier = modifier.drawBehind {
            val bitmap = picture ?: return@drawBehind
            val a = alpha().coerceIn(0f, 1f)
            if (a <= 0.004f) return@drawBehind
            mesh.draw(this, bitmap, coverHeight(), magnify(), a, steady)
        },
    )
}

// reused every frame: the vertices and their colours are only rewritten, never reallocated
private class ReflectionMesh {
    private val verts = FloatArray((MeshRows + 1) * 2 * 2)
    private val colors = IntArray((MeshRows + 1) * 2)
    private val paint = android.graphics.Paint().apply { isFilterBitmap = true }

    fun draw(scope: DrawScope, bitmap: Bitmap, coverHeight: Float, magnify: Float, alpha: Float, steady: Boolean): Unit = with(scope) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || coverHeight <= 0f) return
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        // the cover crops its picture to fill its box, so the mirror crops the same way, then zooms with it
        val fit = max(w / bw, coverHeight / bh)
        val srcLeft = (bw - w / fit) / 2f
        val srcBottom = (bh + coverHeight / fit) / 2f
        val scale = fit * magnify

        for (row in 0..MeshRows) {
            val v = bh * row / MeshRows
            // flipped: the cover's bottom row sits at the top, and rows further up the picture go further down
            val y = (srcBottom - v) * scale
            val fade = if (steady) alpha else fadeAt(y / h) * alpha
            val colour = android.graphics.Color.argb((fade * 255f).roundToInt().coerceIn(0, 255), 255, 255, 255)
            for (col in 0..1) {
                val u = bw * col
                val x = w / 2f + ((u - srcLeft) * fit - w / 2f) * magnify
                val i = row * 2 + col
                verts[i * 2] = x
                verts[i * 2 + 1] = y
                colors[i] = colour
            }
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val saved = native.save()
            native.clipRect(0f, 0f, w, h)
            native.drawBitmapMesh(bitmap, 1, MeshRows, verts, 0, colors, 0, paint)
            native.restoreToCount(saved)
        }
    }

    // strong where it meets the cover, easing out to nothing toward the bottom
    private fun fadeAt(t: Float): Float {
        val x = (t.coerceIn(0f, 1f) / ReflectionFadeEnd).coerceIn(0f, 1f)
        val eased = x * x * (3f - 2f * x)
        return ReflectionStrength * (1f - eased)
    }
}
