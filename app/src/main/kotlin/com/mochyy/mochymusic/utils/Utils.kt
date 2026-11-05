// Utils.kt

package com.mochyy.mochymusic.utils

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.graphics.Shape
fun reportException(throwable: Throwable) {
    throwable.printStackTrace()
}

fun listItemShape(index: Int, count: Int, radius: Dp = 24.dp): Shape {
    val smoothness = 60
    return when {
        count == 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = radius, smoothnessAsPercentTL = smoothness,
            cornerRadiusTR = radius, smoothnessAsPercentTR = smoothness,
            cornerRadiusBL = radius, smoothnessAsPercentBL = smoothness,
            cornerRadiusBR = radius, smoothnessAsPercentBR = smoothness
        )
        index == 0 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = radius, smoothnessAsPercentTL = smoothness,
            cornerRadiusTR = radius, smoothnessAsPercentTR = smoothness,
            cornerRadiusBL = 0.dp, smoothnessAsPercentBL = 0,
            cornerRadiusBR = 0.dp, smoothnessAsPercentBR = 0
        )
        index == count - 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = 0.dp, smoothnessAsPercentTL = 0,
            cornerRadiusTR = 0.dp, smoothnessAsPercentTR = 0,
            cornerRadiusBL = radius, smoothnessAsPercentBL = smoothness,
            cornerRadiusBR = radius, smoothnessAsPercentBR = smoothness
        )
        else -> RectangleShape
    }
}
