// LibraryProgressCard.kt
//
// The small "Download progress  87%" card the Library shows while songs download or files come in
// from the phone, and the ring each song gets on the progress sheet.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.models.TransferBatch
import com.mochyy.mochymusic.models.TransferState
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import kotlinx.coroutines.delay

/** The green of a song that is coming in or is done, from the mock. */
internal val TransferGreen = Color(0xFF79E57A)

/** How long a finished batch's card stays up at 100% before it goes. */
private const val FinishedLingerMs = 2_500L

/**
 * Whether a batch's card should be on screen: while it runs, and for a moment after, so the bar
 * visibly reaches the end instead of vanishing at 96%. A batch that was already finished when the
 * Library opened never shows.
 */
@Composable
private fun rememberCardVisible(batch: TransferBatch): Boolean {
    var lingering by remember { mutableStateOf(false) }
    LaunchedEffect(batch.isRunning) {
        if (batch.isRunning) {
            lingering = true
        } else {
            delay(FinishedLingerMs)
            lingering = false
        }
    }
    return batch.isRunning || (lingering && !batch.isEmpty)
}

/** The batch's card, sliding in and out with it. */
@Composable
fun LibraryProgressCard(
    title: String,
    batch: TransferBatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = rememberCardVisible(batch),
        enter = fadeIn(tween(220)) + expandVertically(tween(280)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(260)),
        modifier = modifier,
    ) {
        val fraction = animateFloatAsState(
            targetValue = batch.fraction,
            animationSpec = tween(350),
            label = "libraryProgressFraction",
        )
        Column(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SearchColors.Tile)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = SearchColors.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(batch.fraction * 100).toInt()}%",
                    color = SearchColors.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            ProgressBar(fraction)
        }
    }
}

/** A white bar on a dim track, drawn from [fraction] in the draw phase so it moves without recomposing. */
@Composable
internal fun ProgressBar(fraction: State<Float>, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.16f))
            .drawBehind {
                drawRoundRect(
                    color = Color.White,
                    size = Size(size.width * fraction.value.coerceIn(0.02f, 1f), size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            },
    )
}

/**
 * A song's state as a small ring: grey while it waits, a green arc while it comes in (spinning
 * when the job can't say how far along it is), a full green ring with a tick once it's done, and
 * a dim ring with a cross if it failed.
 */
@Composable
internal fun TransferRing(state: TransferState, progress: Float, modifier: Modifier = Modifier) {
    val spinning = state == TransferState.Running && progress <= 0f
    // only a spinning ring runs a clock; the rest are still
    val spin = if (spinning) {
        rememberInfiniteTransition(label = "transferRing").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
            label = "transferRingSpin",
        )
    } else {
        null
    }
    val sweep = animateFloatAsState(
        targetValue = when (state) {
            TransferState.Running -> if (spinning) 0.28f else progress.coerceIn(0.06f, 1f)
            TransferState.Done -> 1f
            else -> 0f
        },
        animationSpec = tween(300),
        label = "transferRingSweep",
    )
    Canvas(modifier = modifier.size(22.dp)) {
        val stroke = 2.6.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val track = if (state == TransferState.Failed) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.2f)
        drawCircle(color = track, radius = size.minDimension / 2f - inset, style = Stroke(stroke))
        if (sweep.value > 0f) {
            rotate(spin?.value ?: 0f) {
                drawArc(
                    color = TransferGreen,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        val mark = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        when (state) {
            TransferState.Done -> drawPath(
                Path().apply {
                    moveTo(w * 0.31f, w * 0.52f)
                    lineTo(w * 0.45f, w * 0.65f)
                    lineTo(w * 0.70f, w * 0.38f)
                },
                color = TransferGreen,
                style = mark,
            )
            TransferState.Failed -> {
                val color = SearchColors.Secondary
                drawLine(color, Offset(w * 0.36f, w * 0.36f), Offset(w * 0.64f, w * 0.64f), mark.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, w * 0.36f), Offset(w * 0.36f, w * 0.64f), mark.width, StrokeCap.Round)
            }
            else -> Unit
        }
    }
}
