// ShareTimeline.kt

package com.mochyy.mochymusic.ui.player.share.song

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.share.ShareMuted
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal val ShareYellow = Color(0xFFF7E35A)

private val TrackColor = Color(0xFF2C2C2E)
private val StripHeight = 44.dp
private val WindowHeight = 58.dp
private val WindowCorner = 16.dp

// at rest the window takes a bit under half the width, so the song shows on both sides of it
private const val WindowWidthFraction = 0.46f

// a press this close to the window's edge grabs its arrow and resizes; anywhere else slides the song
private val EdgeGrab = 30.dp

private enum class Grab { Strip, Start, End }

// the song, or a video, sliding under a window: the part inside the window is what plays, and the arrows at its ends make it longer or shorter
@Composable
internal fun ShareTimeline(
    totalMs: Long,
    startMs: Long,
    lengthMs: Long,
    onSettled: (startMs: Long, lengthMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    shortestMs: Long = ShortestWindowMs,
    longestMs: Long = LongestWindowMs,
    // the moment playing right now, drawn as a line inside the window
    playheadMs: () -> Long? = { null },
    // times worth a little tick under the finger, like where each lyric line starts
    marks: List<Long> = emptyList(),
    drawStrip: DrawScope.(timeToX: (Long) -> Float) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val longest = minOf(longestMs, totalMs).coerceAtLeast(1L).toFloat()
    val shortest = minOf(shortestMs.toFloat(), longest)

    var start by remember { mutableFloatStateOf(startMs.toFloat()) }
    var length by remember { mutableFloatStateOf(lengthMs.toFloat()) }
    var touching by remember { mutableStateOf(false) }
    LaunchedEffect(startMs, lengthMs) {
        if (!touching) {
            start = startMs.toFloat()
            length = lengthMs.toFloat()
        }
    }
    // how the strip is drawn: pixels per millisecond, and where the window's start sits; they rest so the window is centred, and hold still while an arrow is dragged
    var scale by remember { mutableFloatStateOf(0f) }
    var left by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val currentMarks by rememberUpdatedState(marks)
    val currentSettled by rememberUpdatedState(onSettled)

    // eases the window back to the middle at its resting width, so a longer part shows smaller and a shorter one bigger
    fun settle(animated: Boolean) {
        settling?.cancel()
        if (width <= 0f || length <= 0f) return
        val toScale = width * WindowWidthFraction / length
        val toLeft = width * (1f - WindowWidthFraction) / 2f
        if (!animated || scale <= 0f) {
            scale = toScale
            left = toLeft
            return
        }
        val fromScale = scale
        val fromLeft = left
        settling = scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)) { t, _ ->
                scale = lerp(fromScale, toScale, t)
                left = lerp(fromLeft, toLeft, t)
            }
        }
    }
    LaunchedEffect(width, touching, length) { if (!touching) settle(animated = true) }

    // a tick each time the window's edge passes a lyric line, or a whole second while resizing
    fun tickBetween(from: Float, to: Float, seconds: Boolean) {
        val low = minOf(from, to)
        val high = maxOf(from, to)
        if (low == high) return
        val crossed = if (seconds) (low / 1000f).toInt() != (high / 1000f).toInt() else currentMarks.any { it.toFloat() in low..high }
        if (crossed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun slide(dx: Float): Boolean {
        if (scale <= 0f) return false
        val next = (start - dx / scale).coerceIn(0f, (totalMs - length).coerceAtLeast(0f))
        tickBetween(start, next, seconds = false)
        val moved = next != start
        start = next
        return moved
    }

    fun resize(grab: Grab, dx: Float) {
        if (scale <= 0f) return
        if (grab == Grab.End) {
            val next = (length + dx / scale).coerceIn(shortest, minOf(longest, totalMs - start).coerceAtLeast(shortest))
            tickBetween(length, next, seconds = true)
            length = next
        } else {
            val end = start + length
            val nextStart = (start + dx / scale).coerceIn(maxOf(0f, end - longest), (end - shortest).coerceAtLeast(0f))
            tickBetween(length, end - nextStart, seconds = true)
            // the start moves under the finger while the end stays put
            left += (nextStart - start) * scale
            start = nextStart
            length = end - nextStart
        }
    }

    Column(modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(WindowHeight)
                .onSizeChanged { width = it.width.toFloat() }
                .pointerInput(totalMs, longest, shortest) {
                    val grabPx = EdgeGrab.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startX = left
                        val endX = left + length * scale
                        val grab = when {
                            abs(down.position.x - endX) < grabPx -> Grab.End
                            abs(down.position.x - startX) < grabPx -> Grab.Start
                            else -> Grab.Strip
                        }
                        settling?.cancel()
                        touching = true
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        horizontalDrag(down.id) { change ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = change.positionChange().x
                            if (grab == Grab.Strip) slide(dx) else resize(grab, dx)
                            change.consume()
                        }
                        val velocity = tracker.calculateVelocity().x
                        scope.launch {
                            // a flung song keeps sliding and slows down on its own
                            if (grab == Grab.Strip && abs(velocity) > 50f) {
                                var travelled = 0f
                                AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(decay) {
                                    val dx = value - travelled
                                    travelled = value
                                    if (!slide(dx)) cancelAnimation()
                                }
                            }
                            touching = false
                            currentSettled(start.roundToLong(), length.roundToLong())
                        }
                    }
                }
                .drawBehind {
                    val px = scale
                    if (px <= 0f) return@drawBehind
                    val timeToX = { t: Long -> left + (t - start) * px }
                    val windowLeft = left
                    val windowRight = left + length * px
                    drawTrack(timeToX(0L), timeToX(totalMs))
                    drawStrip(timeToX)
                    drawWindow(windowLeft, windowRight, playheadMs()?.let(timeToX))
                },
        )
        Spacer(Modifier.height(6.dp))
        val seconds = stringResource(R.string.song_share_seconds, (length / 1000f).roundToInt())
        // the start under the window's left end, its length under the right end, following both as they move
        Layout(
            content = {
                TimeLabel(formatShareTime(start.toLong()))
                TimeLabel(seconds)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val height = placeables.maxOf { it.height }
            layout(constraints.maxWidth, height) {
                val startX = left.roundToInt()
                val endX = (left + length * scale).roundToInt()
                placeables[0].place(startX.coerceIn(0, (constraints.maxWidth - placeables[0].width).coerceAtLeast(0)), 0)
                placeables[1].place((endX - placeables[1].width).coerceIn(0, (constraints.maxWidth - placeables[1].width).coerceAtLeast(0)), 0)
            }
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = ShareMuted)
}

internal fun formatShareTime(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun DrawScope.drawTrack(fromX: Float, toX: Float) {
    val height = StripHeight.toPx()
    val top = (size.height - height) / 2f
    val left = fromX.coerceAtLeast(-height)
    val right = toX.coerceAtMost(size.width + height)
    if (right <= left) return
    drawRoundRect(TrackColor, Offset(left, top), Size(right - left, height), CornerRadius(8.dp.toPx()))
}

private fun DrawScope.drawWindow(left: Float, right: Float, playheadX: Float?) {
    val stroke = 3.dp.toPx()
    val corner = CornerRadius(WindowCorner.toPx())
    val stripHeight = StripHeight.toPx()
    val stripTop = (size.height - stripHeight) / 2f
    // the song outside the window sits back on the track itself, so the part that plays reads as picked
    if (left > 0f) drawRect(Color.Black.copy(alpha = 0.4f), Offset(0f, stripTop), Size(left, stripHeight))
    if (right < size.width) drawRect(Color.Black.copy(alpha = 0.4f), Offset(right, stripTop), Size(size.width - right, stripHeight))
    drawRoundRect(ShareYellow.copy(alpha = 0.2f), Offset(left, 0f), Size(right - left, size.height), corner)
    if (playheadX != null && playheadX in left..right) {
        drawLine(Color.White, Offset(playheadX, stroke * 2), Offset(playheadX, size.height - stroke * 2), strokeWidth = 2.dp.toPx())
    }
    drawRoundRect(ShareYellow, Offset(left + stroke / 2f, stroke / 2f), Size(right - left - stroke, size.height - stroke), corner, style = Stroke(stroke))
    // the arrows just outside each end, the handles that make the part longer or shorter
    drawArrow(left - 14.dp.toPx(), pointsLeft = true)
    drawArrow(right + 14.dp.toPx(), pointsLeft = false)
}

private fun DrawScope.drawArrow(x: Float, pointsLeft: Boolean) {
    val half = 9.dp.toPx()
    val reach = 6.dp.toPx() * if (pointsLeft) -1f else 1f
    val mid = size.height / 2f
    val arrow = Path().apply {
        moveTo(x - reach / 2f, mid - half)
        lineTo(x + reach / 2f, mid)
        lineTo(x - reach / 2f, mid + half)
    }
    drawPath(arrow, ShareYellow, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// the song on the strip: a soft block for each lyric line, or a tick every second when there are no lyrics
internal fun DrawScope.drawSongStrip(lineStarts: List<Long>, totalMs: Long, timeToX: (Long) -> Float) {
    val mid = size.height / 2f
    if (lineStarts.isEmpty()) {
        val tick = 1.dp.toPx()
        var t = 0L
        while (t <= totalMs) {
            val x = timeToX(t)
            if (x in 0f..size.width) {
                val tall = (t / 1000L) % 5L == 0L
                val half = (if (tall) 12.dp else 6.dp).toPx()
                drawLine(Color.White.copy(alpha = if (tall) 0.35f else 0.18f), Offset(x, mid - half), Offset(x, mid + half), strokeWidth = tick)
            }
            t += 1000L
        }
        return
    }
    val height = 16.dp.toPx()
    val gap = 3.dp.toPx()
    val radius = CornerRadius(height / 2f)
    for (i in lineStarts.indices) {
        val from = timeToX(lineStarts[i])
        val to = timeToX(lineStarts.getOrNull(i + 1) ?: (lineStarts[i] + 4_000L).coerceAtMost(totalMs)) - gap
        if (to < 0f || from > size.width || to - from < 2f) continue
        drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(from, mid - height / 2f), Size(to - from, height), radius)
    }
}
