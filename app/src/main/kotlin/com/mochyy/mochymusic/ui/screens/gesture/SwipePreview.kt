// SwipePreview.kt
//
// The little demo on each Gesture card: a song row slides out and the real swipe pill grows into
// the gap, then turns the action's colour and says what it will do, then the row slides back.
// The pill is the one the lists use (SwipePill), so what you see here is what you get there.

package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.ui.component.HighlightColor
import com.mochyy.mochymusic.ui.component.PillEdgeInset
import com.mochyy.mochymusic.ui.component.PillGap
import com.mochyy.mochymusic.ui.component.SlabInset
import com.mochyy.mochymusic.ui.component.SongSwipeAction
import com.mochyy.mochymusic.ui.component.SwipeAction
import com.mochyy.mochymusic.ui.component.SwipePill
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.screens.audiolab.rememberLabTime
import com.mochyy.mochymusic.ui.screens.audiolab.rememberOnScreen
import com.mochyy.mochymusic.ui.screens.audiolab.rememberPicturesHeld
import com.mochyy.mochymusic.ui.screens.audiolab.trackOnScreen

private val RowHeight = 60.dp

// one round of the demo, in seconds
private const val LoopSeconds = 4f
private const val RestSeconds = 0.5f
private const val PullSeconds = 0.9f
private const val HoldUntil = 3f
private const val ReturnSeconds = 0.7f

// a freshly picked action starts pulling this soon, so the change is seen right away
private const val PickLeadIn = 0.3f

/** how far out the row is, 0 at rest to 1 when the pill has fired its colour, at [seconds] into the demo */
private fun pullAt(seconds: Float): Float {
    val t = seconds.mod(LoopSeconds)
    return when {
        t < RestSeconds -> 0f
        t < RestSeconds + PullSeconds -> FastOutSlowInEasing.transform((t - RestSeconds) / PullSeconds)
        t < HoldUntil -> 1f
        t < HoldUntil + ReturnSeconds -> 1f - FastOutSlowInEasing.transform((t - HoldUntil) / ReturnSeconds)
        else -> 0f
    }
}

/**
 * Draws a song row sliding [toRight] or to the left, with [action]'s pill opening behind it. It
 * stops moving while it's off screen or the page is in the middle of a transition.
 */
@Composable
fun SwipePreview(action: SongSwipeAction, toRight: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val onScreen = rememberOnScreen()
    val time = rememberLabTime(rememberPicturesHeld(onScreen))
    val origin = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(action) { origin.floatValue = time.value - (RestSeconds - PickLeadIn) }

    var width by remember { mutableIntStateOf(0) }
    var pillNeed by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { RowHeight.roundToPx() }
    val pillRoom = with(density) { (PillEdgeInset + PillGap - SlabInset).toPx() }
    // the same line a real swipe has: a third of the row, or further out if the label needs it
    val line = maxOf(
        (width * 0.3f).coerceIn(with(density) { 100.dp.toPx() }, with(density) { 150.dp.toPx() }),
        pillNeed + pillRoom,
    )
    val pull = { pullAt(time.value - origin.floatValue) }
    val out = { pull() * line }
    val armed by remember { derivedStateOf { pull() >= 1f } }

    val swipeAction = SwipeAction(action.icon, action.color, stringResource(action.pillLabel)) {}

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .onSizeChanged { width = it.width }
            .trackOnScreen(onScreen)
            .damageBoundary(),
    ) {
        SwipePill(
            action = swipeAction,
            atStart = toRight,
            room = { (out() - pillRoom).coerceAtLeast(0f) },
            rowHeight = rowHeightPx,
            armed = armed,
            fired = false,
            onNeed = { pillNeed = it },
        )
        SampleRow(Modifier.graphicsLayer { translationX = if (toRight) out() else -out() })
    }
}

/** a stand-in song row: cover, two lines of text and the round button at the end, on its slab */
@Composable
private fun SampleRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val inset = SlabInset.toPx()
                drawRoundRect(
                    color = HighlightColor,
                    topLeft = Offset(inset, 0f),
                    size = Size(size.width - inset * 2f, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .padding(horizontal = SlabInset + 10.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.14f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
            Box(Modifier.fillMaxWidth(0.32f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
    }
}
