package com.metrolist.music.ui.component

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.random.Random

// Cover reel and randomized landing adapted from Mochy Music's HeroCarousel (GPL-3.0).
@Composable
internal fun DeeplistGacha(pool: List<MediaMetadata>, enabled: Boolean, onWinner: (MediaMetadata) -> Unit) {
    val available = remember(pool) { pool.distinctBy { it.id } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestOnWinner by rememberUpdatedState(onWinner)
    var candidates by remember { mutableStateOf(emptyList<MediaMetadata>()) }
    var winner by remember { mutableStateOf<MediaMetadata?>(null) }
    var rolling by remember { mutableStateOf(false) }
    val reel = remember { Animatable(0f) }
    val context = LocalContext.current.applicationContext
    var sounds by remember { mutableStateOf<GachaSounds?>(null) }
    DisposableEffect(Unit) {
        onDispose { sounds?.close() }
    }
    LaunchedEffect(reel) {
        snapshotFlow { floor(reel.value).toInt() }.collect {
            if (rolling && it > 0) sounds?.tick()
        }
    }
    val covers = candidates.ifEmpty { available.take(9) }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)),
    ) {
        Box {
            AsyncImage(covers.firstOrNull()?.thumbnailUrl, null, Modifier.matchParentSize().blur(40.dp), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .7f)))
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.deeplist_gacha_title), style = MaterialTheme.typography.titleLarge)
                if (covers.isNotEmpty()) Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))) {
                    val step = 72.dp
                    val position = reel.value
                    val center = floor(position).toInt()
                    val fraction = position - center
                    for (offset in -2..2) {
                        val song = covers[(center + offset).mod(covers.size)]
                        val distance = offset - fraction
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.align(Alignment.Center).offset(y = step * distance)
                                .size(step - 8.dp).graphicsLayer {
                                    alpha = (1f - kotlin.math.abs(distance) * .25f).coerceIn(.3f, 1f)
                                    val scale = (1f - kotlin.math.abs(distance) * .12f).coerceIn(.72f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                }.clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
                val prize = winner
                if (prize != null) {
                    ScratchPrize(prize) {
                        winner = null
                        sounds?.win()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestOnWinner(prize)
                    }
                } else Text(
                    stringResource(when {
                        !enabled -> R.string.deeplist_gacha_guest
                        available.size < 4 -> R.string.deeplist_gacha_wait
                        else -> R.string.deeplist_gacha_hint
                    }),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    enabled = enabled && !rolling && winner == null && available.size >= 4,
                    onClick = {
                        if (!rolling) {
                            if (sounds == null) sounds = GachaSounds(context)
                            rolling = true
                            scope.launch {
                                try {
                                    val picks = available.shuffled().take(9)
                                    reel.snapTo(0f)
                                    candidates = picks
                                    winner = null
                                    val index = Random.nextInt(picks.size)
                                    reel.animateTo(
                                        (picks.size * 3 + index).toFloat(),
                                        tween(2800, easing = CubicBezierEasing(.12f, .65f, .12f, 1f)),
                                    )
                                    winner = picks[index]
                                } finally {
                                    rolling = false
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(if (rolling) R.string.deeplist_gacha_rolling else R.string.deeplist_gacha_roll))
                }
            }
        }
    }
}

@Composable
private fun ScratchPrize(prize: MediaMetadata, onReveal: () -> Unit) {
    val marks = remember(prize.id) { mutableStateListOf<Pair<Offset, Offset>>() }
    val cells = remember(prize.id) { mutableSetOf<Int>() }
    val reveal by rememberUpdatedState(onReveal)
    var revealed by remember(prize.id) { mutableStateOf(false) }
    val revealOnce = { if (!revealed) { revealed = true; onReveal() } }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp))) {
        Text(
            prize.title,
            Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Canvas(
            Modifier.matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(prize.id) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val point = change.position
                        if (point.x !in 0f..size.width.toFloat() || point.y !in 0f..size.height.toFloat()) return@detectDragGestures
                        marks.add(change.previousPosition to point)
                        val column = (point.x / size.width * 8).toInt().coerceIn(0, 7)
                        val row = (point.y / size.height * 3).toInt().coerceIn(0, 2)
                        cells.add(row * 8 + column)
                        if (cells.size >= 9 && !revealed) { revealed = true; reveal() }
                    }
                },
        ) {
            drawRect(Brush.linearGradient(listOf(colors.surfaceVariant, colors.outline, colors.surfaceVariant)))
            marks.forEach { (start, end) ->
                drawLine(colors.surface, start, end, 32.dp.toPx(), StrokeCap.Round, blendMode = BlendMode.Clear)
            }
        }
    }
    androidx.compose.material3.TextButton(onClick = revealOnce) {
        Text(stringResource(R.string.deeplist_gacha_reveal))
    }
}

// Sound effects from Mochy Music (GPL-3.0).
private class GachaSounds(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val tick = pool.load(context, R.raw.gacha_tick, 1)
    private val win = pool.load(context, R.raw.gacha_win, 1)
    private var lastTick = 0L

    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 28L) return
        lastTick = now
        pool.play(tick, .5f, .5f, 1, 0, .94f + Random.nextFloat() * .12f)
    }

    fun win() { pool.play(win, .75f, .75f, 2, 0, 1f) }
    fun close() = pool.release()
}
