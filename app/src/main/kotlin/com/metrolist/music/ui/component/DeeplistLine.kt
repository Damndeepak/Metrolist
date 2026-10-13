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
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun DeeplistLine(
    entry: LyricsEntry,
    position: LongState,
    offset: Long,
    fontSize: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = remember(fontSize) {
        TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.25f).sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val layout = remember(entry.text, style, width) {
            measurer.measure(entry.text, style, constraints = Constraints(maxWidth = width, minWidth = width))
        }
        // Keep joined scripts intact; Latin letters can fly independently.
        val separateLetters = remember(entry.text) { entry.text.all { it.code <= 0x024F } }
        val letters = remember(entry.text, style) {
            if (separateLetters) entry.text.map { measurer.measure(it.toString(), style.copy(textAlign = TextAlign.Left)) } else emptyList()
        }
        val bounds = remember(layout) { entry.text.indices.map { layout.getBoundingBox(it) } }
        val timings = remember(entry) {
            val starts = LongArray(entry.text.length) { entry.time + it * 18L }
            val ends = LongArray(entry.text.length) { starts[it] + 260L }
            var cursor = 0
            entry.words?.forEach { word ->
                val index = entry.text.indexOf(word.text, cursor)
                if (index >= 0) {
                    val start = (word.startTime * 1000).toLong()
                    val duration = ((word.endTime - word.startTime) * 1000).toLong().coerceAtLeast(80L)
                    word.text.indices.forEach { i ->
                        starts[index + i] = start + duration * i / word.text.length
                        ends[index + i] = starts[index + i] + (duration / word.text.length).coerceAtLeast(100L)
                    }
                    cursor = index + word.text.length
                }
            }
            starts to ends
        }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() } + 24.dp)) {
            val now = position.longValue + offset
            val padding = 12.dp.toPx()
            if (!separateLetters) {
                drawText(layout, color = accent.copy(alpha = 0.6f), topLeft = Offset(0f, padding))
                entry.text.indices.forEach { i ->
                    val progress = deeplistProgress(now, timings.first[i], timings.second[i])
                    if (progress > 0f && !entry.text[i].isWhitespace()) {
                        val b = bounds[i]
                        clipRect(b.left, b.top + padding, b.right, b.bottom + padding) {
                            drawText(layout, color = lerp(accent, Color.White, progress), topLeft = Offset(0f, padding))
                        }
                    }
                }
            } else {
                for (i in letters.indices) {
                    if (entry.text[i].isWhitespace()) continue
                    val progress = deeplistProgress(now, timings.first[i], timings.second[i])
                    val lift = sin(progress * PI).toFloat()
                    val entrance = deeplistProgress(now, entry.time + i * 9L, entry.time + i * 9L + 420L)
                    val remaining = (1f - entrance) * (1f - entrance)
                    val b = bounds[i]
                    val x = b.left + (if (i % 2 == 0) -1f else 1f) * remaining * 16.dp.toPx()
                    val y = b.top + padding - lift * 5.dp.toPx() - remaining * 14.dp.toPx()
                    withTransform({
                        translate(x, y)
                        scale(1f + lift * 0.035f, 1f + lift * 0.065f, Offset(b.width / 2, b.height / 2))
                    }) {
                        drawText(letters[i], color = lerp(accent.copy(alpha = 0.65f), Color.White, progress))
                    }
                }
            }
        }
    }
}
