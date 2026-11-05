// CardExtraLines.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How far the extra lines tuck up under the card's own row: its line is centred in the row, so
 * without this a gap opens between the line and what goes with it. The same room is kept free at
 * the card's bottom, which is why a line shows fully exactly when the card has grown past its
 * bottom.
 */
internal val ExtraLinesTuck = 10.dp

/** A line fades in over this much of the card's growth. */
private val ExtraLineFade = 14.dp

/**
 * More lines under the lyric card's own row, for when the card is pulled taller in the player
 * editor: the line's reading and translation, then the lines after it. Each line fades in as the
 * card grows enough to show all of it, so none is ever cut in half. Put it right under the card's row.
 *
 * [growthProvider] (how much taller than usual the card is, px) is read only while drawing, and
 * so is [startProvider]: how far below the row these lines start (px), when other extra lines
 * sit above them.
 */
@Composable
internal fun CardExtraLines(
    growthProvider: () -> Float,
    modifier: Modifier = Modifier,
    startProvider: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.offset(y = -ExtraLinesTuck)) { measurables, constraints ->
        val fadePx = ExtraLineFade.toPx()
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
        }
        val height = placeables.sumOf { it.height }
        layout(constraints.maxWidth, height.coerceAtMost(constraints.maxHeight)) {
            var y = 0
            placeables.forEach { placeable ->
                val bottom = (y + placeable.height).toFloat()
                placeable.placeWithLayer(0, y) {
                    alpha = ((growthProvider() - startProvider() - bottom) / fadePx + 1f).coerceIn(0f, 1f)
                }
                y += placeable.height
            }
        }
    }
}

/** One of the extra lines: smaller than the card's own, single line, cut with an ellipsis. */
@Composable
internal fun CardExtraLine(
    text: String,
    alpha: Float,
    bold: Boolean = false,
    italic: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        color = Color.White.copy(alpha = alpha),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
