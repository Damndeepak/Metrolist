package com.metrolist.music.ui.component

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun rememberDeeplistEconomy(playing: Boolean, enabled: Boolean, track: String?): Boolean {
    val context = LocalContext.current
    val lowRam = remember { (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true }
    var reduced by remember(track) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(playing, enabled, track, owner) {
        reduced = false
        if (playing && enabled && !lowRam) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            var slow = 0
            while (isActive && !reduced) withFrameMillis { now ->
                if (previous != 0L) slow = deeplistSlowFrames(slow, now - previous)
                previous = now
                if (slow >= 8) reduced = true
            }
        }
    }
    return enabled && (lowRam || reduced)
}

@Composable
internal fun DeeplistAtmosphere(words: List<String>, playing: Boolean, accent: Color,
    mode: Int, density: Int, speed: Float, brightness: Float, rings: Boolean, glow: Float,
    performance: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberDeeplistClock(playing && !performance)
    val measurer = rememberTextMeasurer()
    val labels = remember(words, density, performance) {
        val source = words.filter { it.isNotBlank() && it.length <= 28 }.distinct().take(80)
        if (source.isEmpty()) emptyList() else List(if (performance) 8 else density.coerceIn(6, 36)) { i ->
            measurer.measure(source[(i * 7 + 3) % source.size], TextStyle(fontSize = (14 + i * 11 % 23).sp, fontWeight = FontWeight.Bold))
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawWithCache {
            val brush = Brush.radialGradient(listOf(accent.copy(alpha = glow.coerceIn(0f, 1f) * 0.13f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.46f), radius = size.maxDimension * 0.55f)
            onDrawBehind { drawRect(brush) }
        })
        Canvas(Modifier.fillMaxSize()) {
            val seconds = clock.longValue / 1000f * speed.coerceIn(0.2f, 2f)
            if (mode != 2) labels.forEachIndexed { i, label ->
                val baseX = ((i * 37 + 13) % 101) / 100f * size.width
                val baseY = ((i * 61 + 7) % 103) / 102f * size.height
                val x = baseX + sin(seconds * 0.22f + i * 2.13f) * 26.dp.toPx()
                val y = if (mode == 1 && !performance) ((baseY + seconds * (105 + i % 4 * 12).dp.toPx()) % (size.height + 100.dp.toPx())) - 50.dp.toPx()
                    else baseY + cos(seconds * 0.18f + i * 2.13f) * 32.dp.toPx()
                val centre = Offset(size.width / 2, size.height * 0.46f)
                val dim = if ((Offset(x,y) - centre).getDistance() < size.width * 0.28f) 0.22f else 1f
                drawText(label, color = accent.copy(alpha = brightness.coerceIn(0.07f, 0.32f) * dim), topLeft = Offset(x - label.size.width / 2, y))
            }
            if (rings) {
                val centre = Offset(size.width / 2, size.height * 0.46f)
                val radius = size.minDimension * 0.43f * (1f + sin(seconds * 0.6f) * 0.025f)
                drawCircle(accent.copy(alpha = 0.12f), radius, centre, style = Stroke(1.dp.toPx()))
                drawCircle(accent.copy(alpha = 0.045f), radius * 0.86f, centre, style = Stroke(1.dp.toPx()))
                if (!performance) repeat(4) { i ->
                    val angle = seconds * 0.12f + i * 1.57f
                    drawCircle(accent.copy(alpha = 0.35f), 1.5.dp.toPx(), centre + Offset(cos(angle), sin(angle)) * radius)
                }
            }
        }
    }
}
