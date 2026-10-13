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
import kotlin.math.cos

private data class MotionPart(val word: DeeplistMotionWord, val bounds: List<Rect>, val order: Int, val fraction: Float)
private data class MotionGroup(val word: DeeplistMotionWord, val parts: List<MotionPart>, val bounds: List<Rect>)

@Composable
internal fun DeeplistLine(entry: LyricsEntry, position: LongState, offset: Long,
    fontSize: Float, accent: Color, wordstorm: Boolean, playing: Boolean,
    nextTime: Long?, modifier: Modifier = Modifier, speed: Float = 1f,
    performance: Boolean = false, heightBudget: Dp = 400.dp, liftAmount: Float = 7f, flight: Float = 0.43f, sparks: Float = 0.65f) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val clock = rememberDeeplistClock(playing, smooth = !performance)
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
        val groups = remember(entry, nextTime, layout, performance) {
            val ranges = deeplistLetterRanges(entry.text)
            // Long verses and complex joining scripts use word motion to limit draw work and preserve shaping.
            val letters = !performance && ranges.size <= 96 && entry.text.none { it in '\u0600'..'\u0eff' }
            var order = 0
            deeplistMotionWords(entry, nextTime).map { word ->
                val groups = if (letters) ranges.filter { it.first >= word.startChar && it.last < word.endChar } else listOf(word.startChar until word.endChar)
                val parts = groups.mapIndexed { i, chars ->
                    val rows = chars.groupBy { layout.getLineForOffset(it) }
                    val bounds = rows.values.map { row ->
                        val rects = row.map { layout.getBoundingBox(it) }
                        Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
                    }
                    MotionPart(word, bounds, order++, i.toFloat() / groups.size.coerceAtLeast(1))
                }
                val wordBounds = parts.flatMap { it.bounds }.groupBy { it.top }.values.map { row ->
                    Rect(row.minOf { it.left }, row.minOf { it.top }, row.maxOf { it.right }, row.maxOf { it.bottom })
                }
                MotionGroup(word, parts, wordBounds)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() } + 40.dp)) {
            val elapsed = clock.longValue
            val actual = position.longValue
            if (samples[0] != actual) { samples[0] = actual; samples[1] = elapsed }
            val now = actual + offset + if (playing) (elapsed - samples[1]).coerceIn(0L, 100L) else 0L
            val pad = 20.dp.toPx()
            groups.forEach { group ->
                if (now < group.word.start) return@forEach
                val hot = now >= group.word.start && now < group.word.end
                val settled = now >= group.word.end || !playing
                if (settled) {
                    group.bounds.forEach { b ->
                        clipRect(b.left - 0.5f, b.top + pad - 1f, b.right + 0.5f, b.bottom + pad + 1f) {
                            drawText(layout, color = if (hot) accent else Color.White.copy(alpha = 0.85f), topLeft = Offset(0f, pad))
                        }
                    }
                } else group.parts.forEach { part ->
                    val arrival = deeplistCueArrival(now, part.word.start, part.word.end, part.fraction, speed, playing)
                    val remaining = 1f - arrival
                    val progress = deeplistProgress(now, part.word.start, part.word.end)
                    val wave = if (hot) sin(((progress - part.fraction * 0.2f).coerceIn(0f, 1f)) * 3.14159f) * arrival else 0f
                    val lift = wave * liftAmount.coerceIn(0f, 14f).dp.toPx()
                    val dx = if (wordstorm) remaining * (if (part.order % 2 == 0) -1 else 1) * size.width * flight.coerceIn(0.2f, 0.65f) else 0f
                    val dy = remaining * (if (wordstorm && part.order % 4 < 2) -95 else 80).dp.toPx() - lift
                    val colour = if (hot) lerp(Color.White, accent, progress) else Color.White
                    part.bounds.forEach { b ->
                        withTransform({
                            translate(dx, dy)
                            val factor = 1f + wave * 0.04f
                            scale(factor, factor, Offset(b.center.x, b.center.y + pad))
                        }) {
                            clipRect(b.left - 0.5f, b.top + pad - 1f, b.right + 0.5f, b.bottom + pad + 1f) {
                                drawText(layout, color = colour.copy(alpha = arrival), topLeft = Offset(0f, pad))
                            }
                        }
                    }
                }
                if (hot && playing && !performance && sparks > 0f) {
                    val age = (now - group.word.start - 250L) / 1000f
                    if (age in 0f..1.2f) {
                        val bounds = group.bounds.firstOrNull() ?: return@forEach
                        val origin = Offset(bounds.center.x, bounds.center.y + pad)
                        repeat(12) { i ->
                            val angle = i * 6.28318f / 12
                            val radius = age * 55.dp.toPx()
                            val point = origin + Offset(cos(angle) * radius, sin(angle) * radius - age * 12.dp.toPx())
                            drawCircle(accent.copy(alpha = (1f - age / 1.2f) * sparks.coerceIn(0f, 1f) * 0.65f), 1.4.dp.toPx(), point)
                        }
                    }
                }
            }
        }
    }
}
