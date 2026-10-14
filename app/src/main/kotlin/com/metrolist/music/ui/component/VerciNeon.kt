package com.metrolist.music.ui.component

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.VerciAudioReactiveKey
import com.metrolist.music.constants.VerciBrightnessKey
import com.metrolist.music.constants.VerciColorKey
import com.metrolist.music.constants.VerciNeonKey
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.isActive
import kotlin.math.exp

internal val verciColors = intArrayOf(0xFFC2A1FF.toInt(), 0xFF7EDAE3.toInt(), 0xFFFFB296.toInt(), 0xFF88F5B2.toInt(), 0xFFFF698F.toInt())

internal fun bezelPath(width: Float, height: Float, radius: Float, cutouts: List<RectF>, inset: Float): Path {
    val r = radius.coerceIn(inset, minOf(width, height) / 3)
    val right = width - inset
    val bottom = height - inset
    return Path().apply {
        moveTo(inset + r, inset)
        for (box in cutouts.filter { it.bottom > 0 && it.top < height / 3 && it.width() > 0 }.sortedBy { it.left }) {
            val camera = cameraOrbit(box, inset)
            if (camera.left <= inset + r || camera.right >= right - r) continue
            val x = camera.centerX()
            val y = camera.top
            val shoulder = camera.width()
            lineTo(x - shoulder, inset)
            cubicTo(x - shoulder / 2, inset, x - shoulder / 2, y, x, y)
            arcTo(camera, -90f, 180f, false)
            arcTo(camera, 90f, 180f, false)
            cubicTo(x + shoulder / 2, y, x + shoulder / 2, inset, x + shoulder, inset)
        }
        lineTo(right - r, inset); quadTo(right, inset, right, inset + r)
        lineTo(right, bottom - r); quadTo(right, bottom, right - r, bottom)
        lineTo(inset + r, bottom); quadTo(inset, bottom, inset, bottom - r)
        lineTo(inset, inset + r); quadTo(inset, inset, inset + r, inset)
        close()
    }
}

// OEM bounds can include the blank area above a punch-hole; anchor its circle at the bottom.
internal fun cameraOrbit(box: RectF, gap: Float): RectF {
    val diameter = minOf(box.width(), box.height())
    val cx = box.centerX()
    val cy = box.bottom - diameter / 2
    return RectF(cx - diameter / 2 - gap, cy - diameter / 2 - gap,
        cx + diameter / 2 + gap, cy + diameter / 2 + gap)
}

internal fun neonContours(path: Path): List<Path> {
    val measure = PathMeasure(path, true)
    return buildList {
        do {
            if (measure.length > 0) add(Path().apply { measure.getSegment(0f, measure.length, this, true); close() })
        } while (measure.nextContour())
    }
}

@Composable
internal fun VerciEdgeNeon(playing: Boolean, physicalEdges: Boolean) {
    val enabled by rememberPreference(VerciNeonKey, true)
    val brightness by rememberPreference(VerciBrightnessKey, .8f)
    val reactive by rememberPreference(VerciAudioReactiveKey, true)
    val colorIndex by rememberPreference(VerciColorKey, 0)
    val connection = LocalPlayerConnection.current ?: return
    if (!enabled || brightness <= 0f) return
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    var cutouts by remember { mutableStateOf(emptyList<RectF>()) }
    val fixedRing by rememberPreference(com.metrolist.music.constants.VerciCameraRingKey, false)
    var corner by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    var envelope by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, reactive, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (isActive && (playing || envelope > .001f)) withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else ((now - last) / 1e9).coerceIn(0.0, .05)
                last = now
                val target = if (!playing) 0f else if (reactive) connection.service.lyricsAudioEnergy else .35f
                val smoothing = 1 - exp(-dt / if (target > envelope) .09 else .32)
                envelope += ((target - envelope) * smoothing).toFloat()
                phase = (phase + dt.toFloat() * (if (playing) (.055f + .19f * envelope) else .055f * envelope)) % 1f
            }
        }
    }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        if (physicalEdges && Build.VERSION.SDK_INT >= 28) {
            val origin = coordinates.positionInWindow()
            cutouts = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty().map {
                RectF(it.left - origin.x, it.top - origin.y, it.right - origin.x, it.bottom - origin.y)
            }
            if (Build.VERSION.SDK_INT >= 31) {
                corner = view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f

            }
        } else { cutouts = emptyList(); corner = 0f }
    }.drawWithCache {
        val inset = .75.dp.toPx()
        val outline = bezelPath(size.width, size.height, if (corner > 0) corner else 22.dp.toPx(), cutouts, inset)
        val rings = if (fixedRing) cutouts.filter { it.width() > 0 && it.height() > 0 }.map { box ->
            Path().apply { addOval(cameraOrbit(box, inset), Path.Direction.CW) }
        } else emptyList()
        val paths = listOf(outline)
        val measures = paths.map { PathMeasure(it, true) }
        val comet = Path()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; this.color = verciColors[colorIndex.coerceIn(0, 4)] }
        val thin = 1.5.dp.toPx()
        val glow = 6.dp.toPx()
        onDrawBehind {
            val c = drawContext.canvas.nativeCanvas
            val energy = envelope
            val alpha = brightness.coerceIn(0f, 1f) * (.35f + .65f * energy)
            paint.strokeWidth = thin; paint.alpha = (180 * brightness).toInt()
            rings.forEach { c.drawPath(it, paint) }
            paths.forEachIndexed { index, path ->
                paint.strokeWidth = thin; paint.alpha = (45 * brightness).toInt()
                c.drawPath(path, paint)
                val m = measures[index]
                for (tail in 0..1) {
                    comet.reset()
                    val start = ((phase + index * .13f + tail * .5f + 1f) % 1f) * m.length
                    val length = m.length * .32f
                    m.getSegment(start, minOf(start + length, m.length), comet, true)
                    if (start + length > m.length) m.getSegment(0f, start + length - m.length, comet, true)
                    paint.strokeWidth = glow; paint.alpha = (20 * alpha).toInt()
                    c.drawPath(comet, paint)
                    paint.strokeWidth = thin; paint.alpha = (255 * alpha).toInt()
                    c.drawPath(comet, paint)
                }
            }
        }
    }) {}
}
