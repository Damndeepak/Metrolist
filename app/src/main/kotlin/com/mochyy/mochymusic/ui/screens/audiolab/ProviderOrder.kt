// the provider order card on audio quality: drag a row by its handle and the others slide out of the way, a tap on a row is the page's to handle

package com.mochyy.mochymusic.ui.screens.audiolab

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.roundToInt

private val ArrowGap = 26.dp

@Composable
fun ProviderOrder(
    order: List<String>,
    name: (String) -> String,
    logo: (String) -> Int?,
    usable: (String) -> Boolean,
    onReorder: (List<String>) -> Unit,
    /** the small line under a provider's name */
    status: (String) -> String? = { null },
    onTap: (String) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // rows sit on fixed slots so they can slide; a big system font makes the slots taller
    val rowHeight = 68.dp + 26.dp * (density.fontScale - 1f).coerceAtLeast(0f)
    val slotPx = with(density) { (rowHeight + ArrowGap).toPx() }
    var items by remember { mutableStateOf(order) }
    var dragging by remember { mutableStateOf<String?>(null) }
    // a saved order coming back in mustn't yank the list while a row is held
    LaunchedEffect(order) { if (dragging == null) items = order }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    val currentOnReorder by rememberUpdatedState(onReorder)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF1A1A1C))
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(rowHeight * items.size + ArrowGap * (items.size - 1).coerceAtLeast(0))) {
            // the arrows stay put between the slots; the rows move over them
            for (i in 0 until items.size - 1) {
                Canvas(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = rowHeight * (i + 1) + ArrowGap * i)
                        .size(width = 24.dp, height = ArrowGap),
                ) {
                    // the mock's chevron: this one hands on to the next
                    val c = Color.White.copy(alpha = 0.3f)
                    val stroke = 2.dp.toPx()
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val half = 7.dp.toPx()
                    drawLine(c, Offset(cx - half, cy - half / 2), Offset(cx, cy + half / 2), stroke, StrokeCap.Round)
                    drawLine(c, Offset(cx + half, cy - half / 2), Offset(cx, cy + half / 2), stroke, StrokeCap.Round)
                }
            }
            items.forEachIndexed { index, id ->
                key(id) {
                    val isDragged = dragging == id
                    // one position per row: follows the finger while held, springs to its slot after
                    val y = remember { Animatable(index * slotPx) }
                    val held = remember { floatArrayOf(index * slotPx) }
                    LaunchedEffect(isDragged, index) {
                        if (!isDragged) {
                            y.snapTo(held[0])
                            y.animateTo(index * slotPx, spring(dampingRatio = 0.8f, stiffness = 500f))
                        }
                    }
                    val lift by animateFloatAsState(if (isDragged) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "lift")
                    val on = usable(id)
                    Row(
                        modifier = Modifier
                            .zIndex(if (isDragged) 1f else 0f)
                            .offset {
                                if (isDragged) held[0] = index * slotPx + dragOffset
                                IntOffset(0, (if (isDragged) held[0] else y.value).roundToInt())
                            }
                            .graphicsLayer {
                                scaleX = 1f + 0.03f * lift
                                scaleY = 1f + 0.03f * lift
                            }
                            .fillMaxWidth()
                            .height(rowHeight)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isDragged) Color(0xFF242428) else Color(0xFF111113))
                            .clickable { onTap(id) }
                            .padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.graphicsLayer { alpha = if (on) 1f else 0.45f }) {
                            ProviderLogo(logo(id), size = 38, name = name(id))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f).graphicsLayer { alpha = if (on) 1f else 0.5f }) {
                            Text(name(id), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 1)
                            status(id)?.let {
                                Text(it, fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary, maxLines = 1)
                            }
                        }
                        // the handle: dragging it moves the row, a tap on it does nothing rather than reaching the row and switching the provider
                        Canvas(
                            Modifier
                                .size(48.dp)
                                .pointerInput(Unit) { detectTapGestures {} }
                                .pointerInput(id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragging = id
                                            dragOffset = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val list = currentItems
                                            val from = list.indexOf(id)
                                            val to = (from + (dragOffset / slotPx).roundToInt()).coerceIn(0, list.size - 1)
                                            if (to != from) {
                                                items = list.toMutableList().apply { add(to, removeAt(from)) }
                                                dragOffset -= (to - from) * slotPx
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        onDragEnd = {
                                            dragging = null
                                            dragOffset = 0f
                                            currentOnReorder(currentItems)
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffset = 0f
                                            currentOnReorder(currentItems)
                                        },
                                    )
                                },
                        ) {
                            val w = 26.dp.toPx()
                            val x0 = (size.width - w) / 2
                            val c = Color.White
                            drawLine(c, Offset(x0, size.height / 2 - 4.dp.toPx()), Offset(x0 + w, size.height / 2 - 4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                            drawLine(c, Offset(x0, size.height / 2 + 4.dp.toPx()), Offset(x0 + w, size.height / 2 + 4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}
