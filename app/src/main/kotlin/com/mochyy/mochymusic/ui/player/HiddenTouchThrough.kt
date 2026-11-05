// HiddenTouchThrough.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Fainter than this, a part can't be seen, so cutting it out of the picture changes nothing. */
private const val TouchThroughAlpha = 0.01f

/** A clip no finger can land in: one pixel just off the layer's top-left corner. */
private val NoTouchShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(-2f, -2f, -1f, -1f))
}

/**
 * Lets touches go straight through this layer, and everything in it, once its alpha has faded it
 * out. Compose gives a touch to the top thing under the finger whether it can be seen or not, so a
 * faded-out row of buttons kept taking taps meant for the lyrics or the controls under it. Clipped
 * to a shape no touch can land in, the layer is skipped and the touch reaches what's below.
 *
 * Call it last in a `graphicsLayer` block, after alpha is set. It only clips while the layer is
 * invisible, so no fade gets cut short.
 */
internal fun GraphicsLayerScope.letTouchesThroughWhenHidden() {
    if (alpha < TouchThroughAlpha) {
        shape = NoTouchShape
        clip = true
    }
}
