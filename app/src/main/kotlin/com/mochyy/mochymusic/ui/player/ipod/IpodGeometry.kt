package com.mochyy.mochymusic.ui.player.ipod

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

// where everything sits, in pixels: the body, the chevron, the screen's outer edge and the wheel
@Immutable
class IpodGeometry(
    val body: Rect,
    val corner: Float,
    val unit: Float,
    // null on the tall screen, which goes right up under the status bar like the reference
    val chevron: Offset?,
    val screen: Rect,
    val wheel: Rect,
) {
    val bezel: Float get() = screen.width * BezelShare
    val screenCorner: Float get() = screen.width * ScreenCornerShare
}

// a phone: the body is the whole screen and the wheel takes whatever height is left under the glass
fun phoneGeometry(w: Float, h: Float, top: Float, bottom: Float, tall: Boolean): IpodGeometry {
    val chevron = Offset(w / 2f, top + w * 0.064f)
    val floor = h - bottom
    if (tall) tallPhone(w, h, top, floor)?.let { return it }
    val screenTop = chevron.y + w * 0.091f
    val screen = Rect(w * ScreenLeft, screenTop, w * (ScreenLeft + ScreenWidth), screenTop + w * ScreenWidth * ScreenAspect)
    val diameter = minOf(w * WheelShare, (floor - screen.bottom) * 0.84f)
    // the mockup's wheel sits a little past halfway down what's left under the glass
    val cy = (screen.bottom + (h - screen.bottom) * 0.548f).coerceAtMost(floor - diameter / 2f - w * 0.04f)
    return IpodGeometry(Rect(0f, 0f, w, h), 0f, w, chevron, screen, Rect(Offset(w / 2f, cy), diameter / 2f))
}

// the tall screen: thin edges, a big wheel low down and the glass filling everything above it, null when there's no room for that
private fun tallPhone(w: Float, h: Float, top: Float, floor: Float): IpodGeometry? {
    val edge = w * TallEdge
    val diameter = w * TallWheel
    val wheelBottom = floor - w * 0.08f
    val cy = wheelBottom - diameter / 2f
    val screenTop = top + w * 0.04f
    val screenBottom = cy - diameter / 2f - w * 0.09f
    if (screenBottom - screenTop < (w - edge * 2f) * 0.75f) return null
    val screen = Rect(edge, screenTop, w - edge, screenBottom)
    return IpodGeometry(Rect(0f, 0f, w, h), 0f, w, null, screen, Rect(Offset(w / 2f, cy), diameter / 2f))
}

// a big screen: the whole ipod at the mockup's proportions, as large as fits, on black
fun deviceGeometry(w: Float, h: Float, top: Float, bottom: Float, tall: Boolean): IpodGeometry {
    val room = h - top - bottom
    val bodyW = minOf(w * 0.88f, room * 0.94f / BodyAspect)
    val bodyH = bodyW * BodyAspect
    val body = Rect(Offset((w - bodyW) / 2f, top + (room - bodyH) / 2f), Size(bodyW, bodyH))
    val screenTop = body.top + bodyW * 0.1976f
    val screenLeft = body.left + bodyW * ScreenLeft
    val wheel = Rect(Offset(body.center.x, body.top + bodyW * 1.5752f), bodyW * WheelShare / 2f)
    val screenBottom = if (tall) wheel.top - bodyW * 0.08f else screenTop + bodyW * ScreenWidth * ScreenAspect
    return IpodGeometry(
        body = body,
        corner = bodyW * 0.1224f,
        unit = bodyW,
        chevron = Offset(body.center.x, body.top + bodyW * 0.1066f),
        screen = Rect(screenLeft, screenTop, screenLeft + bodyW * ScreenWidth, screenBottom),
        wheel = wheel,
    )
}

// the shell's brushes, built once per size and look rather than on every draw
class IpodBodyPaint(geometry: IpodGeometry, look: IpodLook) {
    private val body = geometry.body
    private val corner = CornerRadius(geometry.corner)
    private val fill = Brush.verticalGradient(listOf(look.body.top, look.body.bottom), startY = body.top, endY = body.bottom)
    private val shadeDown = if (look.shadow) Brush.verticalGradient(
        0f to Color(0x0D000000), 0.03f to Color.Transparent, 0.86f to Color.Transparent, 1f to Color(0x3A000000),
        startY = body.top, endY = body.bottom,
    ) else null
    private val shadeSides = if (look.shadow) Brush.horizontalGradient(
        0f to Color(0x1C000000), 0.045f to Color.Transparent, 0.955f to Color.Transparent, 1f to Color(0x1C000000),
        startX = body.left, endX = body.right,
    ) else null

    // the shell, darker along its edges and toward the bottom unless the shadow is turned off
    fun DrawScope.draw(onBlack: Boolean) {
        if (onBlack) drawRect(Color.Black)
        drawRoundRect(fill, body.topLeft, body.size, corner)
        shadeDown?.let { drawRoundRect(it, body.topLeft, body.size, corner) }
        shadeSides?.let { drawRoundRect(it, body.topLeft, body.size, corner) }
    }
}

fun Modifier.place(rect: Rect, density: Density): Modifier = with(density) {
    offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .size(rect.width.toDp(), rect.height.toDp())
}

// the mockup's body is 572 px wide, its screen 486 x 367 starting 43 px in, its wheel 375 px across
private const val ScreenLeft = 43f / 572f
private const val ScreenWidth = 486f / 572f
private const val ScreenAspect = 367f / 486f
private const val WheelShare = 375f / 572f
private const val BodyAspect = 1248f / 572f
private const val BezelShare = 6f / 486f
// the tall screen's reference: about 5% of the width at each edge, a wheel almost three quarters as wide
private const val TallEdge = 0.049f
private const val TallWheel = 0.727f
// a little rounding on the glass, softer than the mockup's square corners
private const val ScreenCornerShare = 0.045f
