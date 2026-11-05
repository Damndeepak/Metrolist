// the pieces the storage page is built from: usage bars, legends, the cache limit slider and the action buttons

package com.mochyy.mochymusic.ui.screens.settings.storage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.roundToInt

internal val StorageCard = Color(0xFF1C1C1E)
internal val StorageTrack = Color(0xFF3A3A3C)
internal val StorageMuted = Color(0xFF8E8E93)
internal val StorageDanger = Color(0xFFF07A70)

internal val MochyGreen = Color(0xFF86EF7F)
internal val OthersGrey = Color(0xFF6E6E73)
internal val CacheOrange = Color(0xFFF5B26B)
internal val DatabaseRed = Color(0xFFEE6E5E)
internal val AppDataBlue = Color(0xFF6878F0)

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun storageText(
    size: Float,
    weight: FontWeight = FontWeight.Bold,
    color: Color = Color.White,
    tracking: Float = -0.5f,
) = TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

/** bytes the way android's own storage screen writes them: powers of 1000, one decimal past 10 */
internal fun formatSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    val number = if (value >= 10 || unit == 0) value.roundToInt().toString() else String.format("%.1f", value)
    return "$number ${units[unit]}"
}

@Composable
internal fun StorageCardColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(StorageCard)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

internal class BarPart(val color: Color, val bytes: Long)

/** a rounded bar split into parts over total bytes, a part too small to see still gets a sliver so nothing on the legend is missing */
@Composable
internal fun UsageBar(parts: List<BarPart>, total: Long, modifier: Modifier = Modifier) {
    val grow = remember { Animatable(0f) }
    val ready = total > 0
    LaunchedEffect(ready) { if (ready) grow.animateTo(1f, tween(1100, easing = Emphasized)) }
    // each edge eases to where the latest numbers put it
    val fractions = parts.map { part ->
        animateFloatAsState(
            targetValue = if (total > 0) (part.bytes.toFloat() / total).coerceIn(0f, 1f) else 0f,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 120f),
            label = "usageBarPart",
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(StorageTrack)
            .drawBehind {
                val minSliver = 3.dp.toPx()
                var x = 0f
                parts.forEachIndexed { i, part ->
                    if (part.bytes <= 0L) return@forEachIndexed
                    val width = (fractions[i].value * size.width).coerceAtLeast(minSliver) * grow.value
                    // the box's own round clip shapes the two ends
                    drawRect(
                        color = part.color,
                        topLeft = Offset(x, 0f),
                        size = Size(width.coerceAtMost(size.width - x), size.height),
                    )
                    x += width
                }
            },
    )
}

@Composable
internal fun LegendSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    )
}

/** a legend line under a bar: the colour, what it is, and how big it is on the right */
@Composable
internal fun LegendRow(color: Color, label: String, size: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        LegendSwatch(color)
        Spacer(Modifier.width(10.dp))
        Text(label, style = storageText(17f, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        if (size != null) {
            AnimatedContent(
                targetState = size,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "legendSize",
            ) { shown ->
                Text(shown, style = storageText(12f, FontWeight.Medium, StorageMuted, 0f))
            }
        }
    }
}

/** snapping slider for the cache limit with a tick of haptics on every step, value is matched to the nearest step */
@Composable
internal fun StepSlider(
    steps: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val index = steps.indexOf(value).takeIf { it >= 0 } ?: steps.indices.minBy { kotlin.math.abs(steps[it] - value) }
    val last = (steps.size - 1).toFloat()
    // follows the finger exactly while dragging, then springs onto the step it lands on
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val settled by animateFloatAsState(index / last, spring(dampingRatio = 0.7f, stiffness = 500f), label = "sliderKnob")
    val currentIndex by rememberUpdatedState(index)
    val currentOnChange by rememberUpdatedState(onValueChange)

    fun pick(fraction: Float) {
        val i = (fraction.coerceIn(0f, 1f) * last).roundToInt()
        if (i != currentIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnChange(steps[i])
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(steps) {
                detectTapGestures { pick(it.x / size.width) }
            }
            .pointerInput(steps) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = it.x / size.width },
                    onDragEnd = { dragFraction = -1f },
                    onDragCancel = { dragFraction = -1f },
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    pick(dragFraction)
                }
            }
            .drawBehind {
                val knob = 9.dp.toPx()
                val track = 3.dp.toPx()
                val y = size.height / 2f
                val f = if (dragFraction >= 0f) dragFraction else settled
                val x = knob + f * (size.width - knob * 2)
                drawRoundRect(StorageTrack, Offset(0f, y - track / 2), Size(size.width, track), CornerRadius(track))
                drawRoundRect(Color.White, Offset(0f, y - track / 2), Size(x, track), CornerRadius(track))
                // small marks for each step, so it reads as stops rather than a smooth range
                for (i in steps.indices) {
                    val sx = knob + i / last * (size.width - knob * 2)
                    drawCircle(Color.White.copy(alpha = if (sx <= x) 0.9f else 0.3f), radius = 1.5.dp.toPx(), center = Offset(sx, y))
                }
                drawCircle(Color.White, radius = knob, center = Offset(x, y))
            },
    )
}

/** a full-width pill with its label in the middle, danger ones are written in red */
@Composable
internal fun StorageActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape)
            .background(StorageCard)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = storageText(15f, FontWeight.SemiBold, if (danger) StorageDanger else Color.White, -0.4f),
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(detail, style = storageText(11f, FontWeight.Medium, StorageMuted, 0f), textAlign = TextAlign.Center)
        }
    }
}
