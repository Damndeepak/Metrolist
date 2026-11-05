package com.mochyy.mochymusic.ui.player.ipod

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import com.mochyy.mochymusic.ui.player.styles.LocalStyleInteractive
import com.mochyy.mochymusic.ui.player.styles.whileStyleInteractive
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2

// what the wheel asks of whatever screen is showing
@Stable
interface IpodWheelActions {
    // one notch each way, true when something actually moved so the notch is felt
    fun rotate(steps: Int): Boolean

    // menus speed up the longer you keep spinning, volume and scrubbing stay one notch at a time
    val accelerates: Boolean

    fun menu()
    fun menuHeld()
    fun center()
    fun next()
    fun previous()
    fun playPause()

    // called over and over while a side button is held, like scanning on the real thing
    fun scan(forward: Boolean)
}

// the click wheel: spin the ring to scroll, press menu, the side buttons, play or the middle button
@Composable
fun IpodClickWheel(
    actions: IpodWheelActions,
    haptics: IpodHaptics,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(actions)
    val interactive = LocalStyleInteractive.current
    val colors = LocalIpodLook.current.wheel
    val scope = rememberCoroutineScope()
    // the press glow: which button is lit and how bright, fading out after the finger lifts
    val glow = remember { Animatable(0f) }
    var glowZone by remember { mutableStateOf<RingZone?>(null) }
    val measurer = rememberTextMeasurer()
    val light: (RingZone?) -> Unit = { zone ->
        glowZone = zone
        scope.launch { glow.snapTo(1f) }
    }
    val dim: () -> Unit = { scope.launch { glow.animateTo(0f, GlowFade) } }
    Box(
        modifier = modifier
            // its own layer, so the press glow repaints only the wheel and not the body under it
            .graphicsLayer()
            .pointerInput(enabled, interactive, haptics) {
                if (!enabled) return@pointerInput
                val spinner = Spinner()
                whileStyleInteractive(interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val middle = Offset(size.width / 2f, size.height / 2f)
                        val outer = minOf(size.width, size.height) / 2f
                        val from = down.position - middle
                        // the corners of the square aren't the wheel, they're left for the player's own swipe
                        if (from.getDistance() > outer) return@awaitEachGesture
                        down.consume()

                        if (from.getDistance() < outer * CenterRatio) {
                            light(null)
                            haptics.press()
                            val lifted = awaitLift(down.id)
                            dim()
                            if (lifted) {
                                haptics.release()
                                current.center()
                            }
                            return@awaitEachGesture
                        }

                        val zone = ringZone(from)
                        light(zone)
                        val slop = viewConfiguration.touchSlop
                        val notch: (Int, Long) -> Unit = { direction, at ->
                            val steps = spinner.steps(direction, at, current.accelerates)
                            if (current.rotate(steps)) haptics.tick()
                        }
                        val spinning: () -> Unit = { dim() }
                        try {
                            val start = withTimeoutOrNull(HoldMs) { awaitRingStart(down.id, down.position, slop) }
                            when {
                                start == RingStart.Tap -> tap(zone, haptics, current)
                                start == RingStart.Spin -> {
                                    spinning()
                                    spin(down.id, middle, outer, down.position, notch)
                                }
                                start == RingStart.Cancel -> Unit
                                zone == RingZone.NEXT || zone == RingZone.PREVIOUS -> {
                                    haptics.press()
                                    scanWhileHeld(down.id) {
                                        current.scan(forward = zone == RingZone.NEXT)
                                        haptics.tick()
                                    }
                                    haptics.release()
                                }
                                // menu needs a proper hold to jump to the top, a slow press is still just back
                                zone == RingZone.MENU -> when (withTimeoutOrNull(MenuHoldMoreMs) { awaitRingStart(down.id, down.position, slop) }) {
                                    RingStart.Tap -> tap(zone, haptics, current)
                                    RingStart.Spin -> {
                                        spinning()
                                        spin(down.id, middle, outer, down.position, notch)
                                    }
                                    RingStart.Cancel -> Unit
                                    null -> {
                                        haptics.press()
                                        current.menuHeld()
                                        awaitLift(down.id)
                                    }
                                }
                                // a slow press on play is still a press, and can still turn into a spin
                                else -> when (awaitRingStart(down.id, down.position, slop)) {
                                    RingStart.Tap -> tap(zone, haptics, current)
                                    RingStart.Spin -> {
                                        spinning()
                                        spin(down.id, middle, outer, down.position, notch)
                                    }
                                    RingStart.Cancel -> Unit
                                }
                            }
                        } finally {
                            dim()
                        }
                    }
                }
            }
            .drawWithCache {
                val radius = size.minDimension / 2f
                val middle = Offset(size.width / 2f, size.height / 2f)
                val label = measurer.measure(
                    "MENU",
                    TextStyle(
                        color = colors.ink,
                        fontSize = (radius * 0.088f).toSp(),
                        fontWeight = FontWeight.Black,
                        fontFamily = InterFontFamily,
                        letterSpacing = 0.04.em,
                    ),
                )
                val labelAt = Offset(middle.x - label.size.width / 2f, middle.y - radius * 0.82f - label.size.height / 2f)
                val iconHeight = radius * 0.085f
                val next = skipIcon(Offset(middle.x + radius * 0.80f, middle.y), iconHeight, forward = true)
                val previous = skipIcon(Offset(middle.x - radius * 0.80f, middle.y), iconHeight, forward = false)
                val play = playPauseIcon(Offset(middle.x, middle.y + radius * 0.80f), iconHeight * 1.05f)
                val hairline = Stroke(width = 1.2f)
                val sectors = RingZone.entries.associateWith { ringSector(it, middle, radius, radius * CenterRatio) }
                onDrawBehind {
                    drawCircle(colors.face, radius, middle)
                    drawCircle(colors.edge, radius - 0.6f, middle, style = hairline)
                    val lit = glow.value
                    val zone = glowZone
                    if (lit > 0.001f && zone != null) drawPath(sectors.getValue(zone), colors.pressGlow.copy(alpha = colors.pressGlow.alpha * lit))
                    drawText(label, topLeft = labelAt)
                    drawPath(next, colors.ink)
                    drawPath(previous, colors.ink)
                    drawPath(play, colors.ink)
                    val centerLit = if (zone == null) lit else 0f
                    drawCircle(lerp(colors.center, colors.centerPressed, centerLit), radius * CenterRatio, middle)
                    drawCircle(colors.edge, radius * CenterRatio - 0.6f, middle, style = hairline)
                }
            },
    )
}

// ipod.js's fast forward: two triangles and a bar, flipped round for rewind
private fun skipIcon(center: Offset, height: Float, forward: Boolean): Path {
    val unit = height / 16f
    val width = 33f * unit
    val left = center.x - width / 2f
    val top = center.y - height / 2f
    fun x(v: Float) = if (forward) left + v * unit else left + width - v * unit
    fun y(v: Float) = top + v * unit
    return Path().apply {
        moveTo(x(4f), y(1.07f)); lineTo(x(16f), y(8f)); lineTo(x(4f), y(14.93f)); close()
        moveTo(x(17f), y(1.07f)); lineTo(x(29f), y(8f)); lineTo(x(17f), y(14.93f)); close()
        moveTo(x(29f), y(0.5f)); lineTo(x(33f), y(0.5f)); lineTo(x(33f), y(15.5f)); lineTo(x(29f), y(15.5f)); close()
    }
}

// ipod.js's play and pause, one triangle and two bars
private fun playPauseIcon(center: Offset, height: Float): Path {
    val unit = height / 18f
    val left = center.x - 39f * unit / 2f
    val top = center.y - height / 2f
    fun x(v: Float) = left + v * unit
    fun y(v: Float) = top + v * unit
    return Path().apply {
        moveTo(x(4.5f), y(1.2f)); lineTo(x(18f), y(9f)); lineTo(x(4.5f), y(16.8f)); close()
        moveTo(x(26f), y(1f)); lineTo(x(30f), y(1f)); lineTo(x(30f), y(17f)); lineTo(x(26f), y(17f)); close()
        moveTo(x(35f), y(1f)); lineTo(x(39f), y(1f)); lineTo(x(39f), y(17f)); lineTo(x(35f), y(17f)); close()
    }
}

// the quarter of the ring a button covers, lit while it's pressed
private fun ringSector(zone: RingZone, middle: Offset, outer: Float, inner: Float): Path {
    val start = when (zone) {
        RingZone.MENU -> -135f
        RingZone.NEXT -> -45f
        RingZone.PLAY -> 45f
        RingZone.PREVIOUS -> 135f
    }
    val outerRect = Rect(middle, outer)
    val innerRect = Rect(middle, inner)
    return Path().apply {
        arcTo(outerRect, start, 90f, forceMoveTo = true)
        arcTo(innerRect, start + 90f, -90f, forceMoveTo = false)
        close()
    }
}

private enum class RingZone { MENU, NEXT, PLAY, PREVIOUS }

private enum class RingStart { Tap, Spin, Cancel }

// which quarter of the ring a touch is in, measured clockwise from the top
private fun ringZone(from: Offset): RingZone {
    val degrees = ((Math.toDegrees(atan2(from.y, from.x).toDouble()) + 90.0 + 360.0) % 360.0)
    return when {
        degrees < 45.0 || degrees >= 315.0 -> RingZone.MENU
        degrees < 135.0 -> RingZone.NEXT
        degrees < 225.0 -> RingZone.PLAY
        else -> RingZone.PREVIOUS
    }
}

private fun tap(zone: RingZone, haptics: IpodHaptics, actions: IpodWheelActions) {
    haptics.click()
    when (zone) {
        RingZone.MENU -> actions.menu()
        RingZone.NEXT -> actions.next()
        RingZone.PLAY -> actions.playPause()
        RingZone.PREVIOUS -> actions.previous()
    }
}

// waits to see whether a touch on the ring is a tap or the start of a spin, eating every event on the way
private suspend fun AwaitPointerEventScope.awaitRingStart(id: PointerId, from: Offset, slop: Float): RingStart {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return RingStart.Cancel
        event.changes.forEach { it.consume() }
        if (!change.pressed) return RingStart.Tap
        if ((change.position - from).getDistance() > slop) return RingStart.Spin
    }
}

// true when the finger came up, false when the touch was taken away
private suspend fun AwaitPointerEventScope.awaitLift(id: PointerId): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        event.changes.forEach { it.consume() }
        if (!change.pressed) return true
    }
}

private suspend fun AwaitPointerEventScope.scanWhileHeld(id: PointerId, onStep: () -> Unit) {
    while (true) {
        onStep()
        if (withTimeoutOrNull(ScanRepeatMs) { awaitLift(id) } != null) return
    }
}

// follows the finger round the ring, one notch every so many degrees, either way round
private suspend fun AwaitPointerEventScope.spin(
    id: PointerId,
    middle: Offset,
    outer: Float,
    start: Offset,
    onNotch: (direction: Int, at: Long) -> Unit,
) {
    // starting from where the finger came down, so the travel inside the slop still counts
    var last: Float? = (start - middle).let { atan2(it.y, it.x) }
    var travelled = 0f
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return
        event.changes.forEach { it.consume() }
        if (!change.pressed) return
        val p = change.position - middle
        // right over the middle the angle jumps around wildly, so it's ignored until the finger comes back out
        if (p.getDistance() < outer * DeadZone) {
            last = null
            continue
        }
        val angle = atan2(p.y, p.x)
        val before = last
        last = angle
        if (before == null) continue
        travelled += wrapAngle(angle - before)
        while (travelled >= Notch) {
            travelled -= Notch
            onNotch(1, change.uptimeMillis)
        }
        while (travelled <= -Notch) {
            travelled += Notch
            onNotch(-1, change.uptimeMillis)
        }
    }
}

private fun wrapAngle(radians: Float): Float {
    var a = radians
    while (a > PI) a -= (2 * PI).toFloat()
    while (a < -PI) a += (2 * PI).toFloat()
    return a
}

// keep spinning the same way and the list starts flying, like the classic does after a second or so
private class Spinner {
    private var lastAt = 0L
    private var lastDirection = 0
    private var streakStart = 0L

    fun steps(direction: Int, at: Long, accelerates: Boolean): Int {
        if (direction != lastDirection || at - lastAt > StreakGapMs) streakStart = at
        lastAt = at
        lastDirection = direction
        if (!accelerates) return direction
        val seconds = ((at - streakStart) / 1000L).toInt().coerceIn(0, 4)
        return direction * (1 shl seconds)
    }
}

// the middle button's share of the wheel, as on the mockup
const val CenterRatio = 0.467f
private const val DeadZone = 0.22f
// 24 notches a turn, a little finer than retro's 20 so a phone-sized wheel doesn't feel sticky
private const val Notch = (2 * PI / 24).toFloat()
private const val HoldMs = 450L
private const val MenuHoldMoreMs = 450L
private const val ScanRepeatMs = 180L
private const val StreakGapMs = 160L

private val GlowFade = tween<Float>(260)
