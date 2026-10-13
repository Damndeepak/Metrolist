package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.lyrics.LyricsEntry
import kotlin.math.sin

private data class MotionPart(val word: DeeplistMotionWord, val bounds: List<Rect>, val order: Int, val fraction: Float)

@Composable
internal fun DeeplistLine(entry: LyricsEntry, position: LongState, offset: Long,
    fontSize: Float, accent: Color, wordstorm: Boolean, playing: Boolean,
    nextTime: Long?, modifier: Modifier = Modifier, speed: Float = 1f,
    performance: Boolean = false, heightBudget: Dp = 400.dp) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val clock = rememberDeeplistClock(playing, fastForMs = if (performance) 0L else 1800L)
    val samples = remember(entry) { longArrayOf(Long.MIN_VALUE, 0L) }
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = entry.text }) {
        val heightLimit = with(density) { heightBudget.toPx() }.coerceAtLeast(90f)
        val layout = remember(entry.text, fontSize, constraints.maxWidth, heightLimit) {
            var size = fontSize
            fun measure() = measurer.measure(entry.text, TextStyle(fontSize = size.sp,
                lineHeight = (size * 1.18f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                constraints = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth))
            var result = measure()
            repeat(5) {
                if (result.size.height > heightLimit && size > 18f) {
                    size = (size * (heightLimit / result.size.height).coerceIn(0.65f, 0.92f)).coerceAtLeast(18f)
                    result = measure()
                }
            }
            result
        }
        val parts = remember(entry, nextTime, layout, performance) {
            val ranges = deeplistLetterRanges(entry.text)
            // Long verses and complex joining scripts use word motion to limit draw work and preserve shaping.
            val letters = !performance && ranges.size <= 96 && entry.text.none { it in '\u0600'..'\u08ff' }
            var order = 0
            deeplistMotionWords(entry, nextTime).flatMap { word ->
                val groups = if (letters) ranges.filter { it.first >= word.startChar && it.last < word.endChar } else listOf(word.startChar until word.endChar)
                groups.mapIndexed { i, chars ->
                    val rows = chars.groupBy { layout.getLineForOffset(it) }
                    val bounds = rows.values.map { row ->
                        val rects = row.map { layout.getBoundingBox(it) }
                        Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
                    }
                    MotionPart(word, bounds, order++, i.toFloat() / groups.size.coerceAtLeast(1))
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() } + 40.dp)) {
            val elapsed = clock.longValue
            val actual = position.longValue
            if (samples[0] != actual) { samples[0] = actual; samples[1] = elapsed }
            val now = actual + offset + if (playing) (elapsed - samples[1]).coerceIn(0L, 100L) else 0L
            val pad = 20.dp.toPx()
            parts.forEach { part ->
                val arrival = if (playing) deeplistArrival((elapsed * speed.coerceIn(0.7f, 1.7f)).toLong(), part.order, wordstorm) else 1f
                val remaining = 1f - arrival
                val progress = deeplistProgress(now, part.word.start, part.word.end)
                val hot = now >= part.word.start && now < part.word.end
                val wave = if (hot && playing) sin(((progress - part.fraction * 0.2f).coerceIn(0f, 1f)) * 3.14159f) else 0f
                val lift = wave * 7.dp.toPx()
                val dx = if (wordstorm) remaining * (if (part.order % 2 == 0) -1 else 1) * size.width * 0.28f else remaining * (if (part.order % 2 == 0) -12 else 12).dp.toPx()
                val dy = remaining * (if (wordstorm && part.order % 4 < 2) -42 else 32).dp.toPx() - lift
                val colour = if (hot) lerp(Color.White, accent, (progress * 4f - part.fraction).coerceIn(0f, 1f)) else if (now >= part.word.end) Color.White else Color.White.copy(alpha = 0.62f)
                part.bounds.forEach { b ->
                    withTransform({
                        translate(dx, dy)
                        val factor = 1f + wave * 0.035f
                        scale(factor, factor, Offset(b.center.x, b.center.y + pad))
                    }) {
                        clipRect(b.left - 0.5f, b.top + pad - 1f, b.right + 0.5f, b.bottom + pad + 1f) {
                            drawText(layout, color = colour.copy(alpha = colour.alpha * (0.15f + arrival * 0.85f)), topLeft = Offset(0f, pad))
                        }
                    }
                }
            }
        }
    }
}
