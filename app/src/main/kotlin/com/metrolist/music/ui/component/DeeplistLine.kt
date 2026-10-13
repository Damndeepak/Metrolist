package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.lyrics.LyricsEntry
import kotlin.math.sin

private data class MotionPart(val word: DeeplistMotionWord, val bounds: List<Rect>)

@Composable
internal fun DeeplistLine(entry: LyricsEntry, position: LongState, offset: Long,
    fontSize: Float, accent: Color, wordstorm: Boolean, playing: Boolean,
    nextTime: Long?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val clock = rememberDeeplistClock(playing)
    val style = remember(fontSize) { TextStyle(fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.25f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
    val samples = remember(entry) { longArrayOf(Long.MIN_VALUE, 0L) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val layout = remember(entry.text, style, constraints.maxWidth) {
            measurer.measure(entry.text, style, constraints = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth))
        }
        val parts = remember(entry, nextTime, layout) {
            deeplistMotionWords(entry, nextTime).map { word ->
                val rows = (word.startChar until word.endChar).groupBy { layout.getLineForOffset(it) }
                val bounds = rows.values.map { chars ->
                    val rects = chars.map { layout.getBoundingBox(it) }
                    Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
                }
                MotionPart(word, bounds)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() } + 32.dp)) {
            val elapsed = clock.longValue
            val actual = position.longValue
            if (samples[0] != actual) { samples[0] = actual; samples[1] = elapsed }
            val now = actual + offset + if (playing) (elapsed - samples[1]).coerceIn(0L, 100L) else 0L
            val pad = 16.dp.toPx()
            parts.forEachIndexed { index, part ->
                val arrival = if (playing) deeplistArrival(elapsed, index, wordstorm) else 1f
                val remaining = 1f - arrival
                val progress = deeplistProgress(now, part.word.start, part.word.end)
                val hot = now in part.word.start..part.word.end
                val lift = if (hot) sin(progress * 3.14159f) * 6.dp.toPx() else 0f
                val dx = if (wordstorm) remaining * (if (index % 2 == 0) -1 else 1) * size.width * 0.35f else 0f
                val dy = remaining * (if (wordstorm && index % 4 < 2) -32 else 22).dp.toPx() - lift
                val colour = if (hot) accent else lerp(Color.White.copy(alpha = 0.42f), Color.White, progress)
                withTransform({ translate(dx, dy) }) {
                    part.bounds.forEach { b ->
                        clipRect(b.left - 1f, b.top + pad, b.right + 1f, b.bottom + pad) {
                            drawText(layout, color = colour.copy(alpha = colour.alpha * (0.2f + arrival * 0.8f)), topLeft = androidx.compose.ui.geometry.Offset(0f, pad))
                        }
                    }
                }
            }
        }
    }
}
