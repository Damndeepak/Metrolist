// ShareChoreography.kt
//
// The lyrics share's opening, all on one clock: the held line zooms in and lifts out of the lyrics,
// flies to its row while the sheet rises, the lines above it drop in from the top down, then the
// lines below rise in from the bottom up, so everything closes in on the line you held. Closing
// runs the same clock backwards, so swiping the sheet away plays all of it in reverse.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.ui.component.smoothstep
import kotlinx.coroutines.flow.first

// Times on the clock, in milliseconds of the opening.
private const val LiftEnd = 170f
private const val TravelStart = 110f
private const val TravelEnd = 640f
private const val AboveStart = 80f
private const val BelowStart = 520f
private const val RowStep = 45f
private const val RowDuration = 380f

/** Closing plays the opening back this much faster, because leaving should never feel slow. */
private const val ReverseSpeed = 2f

/** How far the held line zooms in as it lifts off the lyrics. */
internal const val ShareLiftZoom = 1.07f

/** Rows above the held one sit this many rows down from the top when the picker opens. */
private const val RowsAboveHeld = 3

private val RowTravel = 40.dp

@Stable
internal class ShareChoreography(private val held: Int, val hasFlight: Boolean, lineCount: Int) {
    /** The picker's list, opened with the held line a few rows down so lines can arrive from both sides. */
    val listState = LazyListState(firstVisibleItemIndex = (held - RowsAboveHeld).coerceIn(0, (lineCount - 1).coerceAtLeast(0)))

    private val clock = Animatable(0f)

    /** The rows on screen when the clock started, so the cascade knows who goes first. Null until laid out. */
    private var cascadeRows by mutableStateOf<IntRange?>(null)

    var reversing by mutableStateOf(false)
        private set

    /** The flying copy has been drawn, so the lyrics page can hide its own. */
    var pillShown by mutableStateOf(false)

    /** 0 at rest in the lyrics, 1 zoomed in and lit. */
    fun lift(): Float = easeInOut(span(clock.value, 0f, LiftEnd))

    /** 0 over the lyrics, 1 landed on its row; not eased, the flight eases each axis its own way. */
    fun travel(): Float = span(clock.value, TravelStart, TravelEnd)

    /** The flying copy shows until it lands, and again from the moment it leaves on the way back. */
    fun pillVisible(): Boolean = hasFlight && travel() < 1f

    fun pillAlpha(): Float = if (reversing) lift() else 1f

    fun sourceAlpha(): Float = when {
        !hasFlight || !pillShown -> 1f
        // the copy settles back into the line and hands over to it as it un-zooms
        reversing -> 1f - lift()
        else -> 0f
    }

    /** How far one picker row has arrived, 0 to 1. */
    fun rowProgress(index: Int): Float {
        if (index == held && hasFlight) return 1f
        val w = cascadeRows ?: return 0f
        if (index !in w) return 1f
        val start = when {
            index < held -> AboveStart + (index - w.first) * RowStep
            index > held -> BelowStart + (w.last - index) * RowStep
            // no flight to land: the held row arrives when the flight would have
            else -> TravelStart
        }
        return span(clock.value, start, start + RowDuration)
    }

    /**
     * Moves a picker row along its arrival: rows above come down from the top, rows below come up
     * from the bottom, each growing in with a small overshoot. Hidden rows stay hidden.
     */
    fun GraphicsLayerScope.applyRowArrival(index: Int) {
        if (index == held && pillVisible()) {
            alpha = 0f
            return
        }
        val p = rowProgress(index)
        if (p >= 1f) return
        val e = easeOutBack(p)
        val fromAbove = index < held
        translationY += (1f - e) * RowTravel.toPx() * if (fromAbove) -1f else 1f
        val s = 0.9f + 0.1f * e
        scaleX *= s
        scaleY *= s
        alpha *= smoothstep(0f, 0.55f, p)
    }

    /** Waits for the picker to be laid out, then plays the opening. */
    suspend fun playForward() {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
        if (reversing) return
        cascadeRows = visibleRows()
        val end = endTime()
        clock.animateTo(end, tween((end - clock.value).toInt().coerceAtLeast(0), easing = LinearEasing))
    }

    /** Plays the opening backwards, from wherever it got to. Returns once everything is home. */
    suspend fun playBackward() {
        reversing = true
        // Finished opening: the list may have been scrolled since, so cascade out whatever shows
        // now. Every row is fully in at the end time, so moving the clock there changes nothing.
        if (cascadeRows != null && clock.value >= endTime()) {
            cascadeRows = visibleRows() ?: cascadeRows
            clock.snapTo(endTime())
        }
        clock.animateTo(0f, tween((clock.value / ReverseSpeed).toInt(), easing = LinearEasing))
    }

    private fun visibleRows(): IntRange? {
        val rows = listState.layoutInfo.visibleItemsInfo
        if (rows.isEmpty()) return null
        return rows.first().index..rows.last().index
    }

    private fun endTime(): Float {
        val w = cascadeRows ?: return TravelEnd
        val above = (held - w.first).coerceIn(0, w.count())
        val below = (w.last - held).coerceIn(0, w.count())
        return maxOf(
            TravelEnd,
            AboveStart + (above - 1).coerceAtLeast(0) * RowStep + RowDuration,
            BelowStart + (below - 1).coerceAtLeast(0) * RowStep + RowDuration,
        )
    }
}

/** 0 before [from], 1 after [to], straight in between. */
private fun span(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Slow at both ends, so it reads the same played forwards or backwards. */
internal fun easeInOut(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

/** Arrives a touch past its place and settles back. */
private fun easeOutBack(t: Float): Float {
    val c = 1.4f
    val u = t - 1f
    return 1f + (c + 1f) * u * u * u + c * u * u
}
