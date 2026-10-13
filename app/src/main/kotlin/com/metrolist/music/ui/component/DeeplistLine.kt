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

private data class DeeplistWord(val start: Long, val end: Long, val bounds: List<Rect>)

@Composable
internal fun DeeplistLine(entry: LyricsEntry, position: LongState, offset: Long,
    fontSize: Float, accent: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = remember(fontSize) { TextStyle(fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.25f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val layout = remember(entry.text, style, constraints.maxWidth) {
            measurer.measure(entry.text, style, constraints = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth))
        }
        val words = remember(entry, layout) {
            var cursor = 0
            entry.words.orEmpty().mapNotNull { word ->
                val index = entry.text.indexOf(word.text, cursor)
                if (index < 0 || word.text.isEmpty()) null else {
                    cursor = index + word.text.length
                    val rows = (index until cursor).groupBy { layout.getLineForOffset(it) }
                    val bounds = rows.values.map { chars ->
                        val rects = chars.map { layout.getBoundingBox(it) }
                        Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
                    }
                    DeeplistWord((word.startTime * 1000).toLong(), (word.endTime * 1000).toLong(), bounds)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() } + 16.dp)) {
            val now = position.longValue + offset
            val pad = 8.dp.toPx()
            // Draw the paragraph once, and only overlay the word currently being sung.
            drawText(layout, color = Color.White, topLeft = Offset(0f, pad))
            val active = words.firstOrNull { now in it.start..it.end }
            active?.let { word ->
                val progress = deeplistProgress(now, word.start, word.end)
                val lift = kotlin.math.sin(progress * kotlin.math.PI).toFloat() * 3.dp.toPx()
                word.bounds.forEach { b ->
                    clipRect(b.left, b.top + pad - lift, b.right, b.bottom + pad) {
                        withTransform({ translate(top = -lift) }) {
                            drawText(layout, color = accent, topLeft = Offset(0f, pad))
                        }
                    }
                }
            }
        }
    }
}
