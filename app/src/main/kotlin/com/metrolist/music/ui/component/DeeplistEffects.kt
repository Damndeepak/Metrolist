package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.music.constants.DeeplistNeonKey
import com.metrolist.music.constants.DeeplistPerformanceKey
import com.metrolist.music.constants.WordstormAccentKey
import com.metrolist.music.constants.WordstormMotionKey
import com.metrolist.music.constants.WordstormPresetKey
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.isActive
import kotlin.math.sin

@Composable
internal fun rememberDeeplistClock(playing: Boolean, fastForMs: Long = 0L): LongState {
    val clock = remember { mutableLongStateOf(0L) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(playing, owner, fastForMs) {
        if (playing) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (isActive) withFrameMillis { now ->
                if (last == 0L) last = now
                val interval = if (clock.longValue < fastForMs) 16L else 32L
                if (now - last >= interval) { clock.longValue += (now - last).coerceAtMost(100L); last = now }
            }
        }
    }
    return clock
}

@Composable
internal fun DeeplistWordRain(words: List<String>, playing: Boolean, accent: Color) {
    val clock = rememberDeeplistClock(playing)
    val measurer = rememberTextMeasurer()
    val labels = remember(words) {
        words.filter { it.isNotBlank() && it.length <= 28 }.distinct().take(14).mapIndexed { index, word ->
            measurer.measure(word, TextStyle(fontSize = (13 + index % 5 * 2).sp, fontWeight = FontWeight.Bold))
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        labels.forEachIndexed { index, label ->
            val duration = 7500L + index % 4 * 900L
            val p = ((clock.longValue + index * 593L) % duration).toFloat() / duration
            val x = ((index * 43 + 7) % 100) / 100f * (size.width - label.size.width).coerceAtLeast(0f) + sin(p * 6.28f + index) * 12.dp.toPx()
            val y = p * (size.height + 100.dp.toPx()) - 50.dp.toPx()
            val fade = (p * 8).coerceAtMost(1f) * ((1 - p) * 8).coerceAtMost(1f)
            drawText(label, color = accent.copy(alpha = 0.16f * fade), topLeft = Offset(x, y))
        }
    }
}

@Composable
internal fun DeeplistEdgeLights(playing: Boolean, neonOverride: Float? = null, performanceOverride: Boolean? = null) {
    val motion by rememberPreference(WordstormMotionKey, true)
    val preset by rememberPreference(WordstormPresetKey, 0)
    val colour by rememberPreference(WordstormAccentKey, 0)
    val intensity by rememberPreference(DeeplistNeonKey, 0.65f)
    val performance by rememberPreference(DeeplistPerformanceKey, false)
    val light = (neonOverride ?: intensity).coerceIn(0f, 1f)
    if (light == 0f) return
    val clock = rememberDeeplistClock(playing && motion && !(performanceOverride ?: performance))
    val accent = listOf(Color(0xFFC2A1FF), Color(0xFF7EDAE3), Color(0xFFFFB296))[colour.coerceIn(0, 2)]
    Canvas(Modifier.fillMaxSize()) {
        val phase = ((clock.longValue + 1200L) % 5200L).toFloat() / 5200f
        val x0 = (phase * 1.2f - 0.2f) * size.width
        val x1 = phase * 1.2f * size.width
        val y0 = size.height - phase * 1.2f * size.height
        val y1 = size.height - (phase * 1.2f - 0.2f) * size.height
        fun edge(start: Offset, end: Offset) {
            drawLine(accent.copy(alpha = 0.13f * light), start, end, 6.dp.toPx())
            drawLine(Brush.linearGradient(listOf(Color.Transparent, accent.copy(alpha = light), Color.Transparent), start, end), start, end, 1.5.dp.toPx())
        }
        edge(Offset(x0, 1.dp.toPx()), Offset(x1, 1.dp.toPx()))
        edge(Offset(size.width - x0, size.height - 1.dp.toPx()), Offset(size.width - x1, size.height - 1.dp.toPx()))
        edge(Offset(1.dp.toPx(), y0), Offset(1.dp.toPx(), y1))
        edge(Offset(size.width - 1.dp.toPx(), y0), Offset(size.width - 1.dp.toPx(), y1))
    }
}

