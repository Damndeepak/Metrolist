package com.metrolist.music.ui.component

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.roundToInt
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.random.Random

// Cover reel and randomized landing adapted from Mochy Music's HeroCarousel (GPL-3.0).
@Composable
internal fun DeeplistGacha(pool: List<MediaMetadata>, enabled: Boolean, onWinner: (MediaMetadata) -> Unit) {
    val available = remember(pool) { pool.distinctBy { it.id } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestOnWinner by rememberUpdatedState(onWinner)
    var candidates by remember { mutableStateOf(emptyList<MediaMetadata>()) }
    var rolling by remember { mutableStateOf(false) }
    val reel = remember { Animatable(0f) }
    val pointer = remember { Animatable(0f) }
    val landingPop = remember { Animatable(1f) }
    val context = LocalContext.current.applicationContext
    var sounds by remember { mutableStateOf<GachaSounds?>(null) }
    DisposableEffect(Unit) {
        onDispose { sounds?.close() }
    }
    LaunchedEffect(reel) {
        snapshotFlow { reel.value.roundToInt() }.collect {
            if (rolling && it > 0) {
                sounds?.tick()
                launch {
                    pointer.snapTo(1f)
                    pointer.animateTo(0f, spring(dampingRatio = .4f, stiffness = 900f))
                }
            }
        }
    }
    val covers = candidates.ifEmpty { available.take(9) }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(393f / 527f).clip(RoundedCornerShape(28.dp))) {
            val unit = maxHeight / 527f
            val focus = unit * 221f
            val center = reel.value.roundToInt()
            val focusSong = covers.getOrNull(center.mod(covers.size.coerceAtLeast(1)))
            AsyncImage(focusSong?.thumbnailUrl, null, Modifier.matchParentSize().blur(40.dp), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent, Color.Black))))
            if (covers.isNotEmpty()) {
                for (offset in -2..2) {
                    val song = covers[(center + offset).mod(covers.size)]
                    val distance = center + offset - reel.value
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.align(Alignment.TopEnd)
                            .offset(x = unit * 28f, y = unit * 230.5f - focus / 2)
                            .size(focus).graphicsLayer {
                                val near = (1f - kotlin.math.abs(distance)).coerceIn(0f, 1f)
                                val scale = (187f + (221f - 187f) * near) / 221f
                                scaleX = scale * if (near > .5f) landingPop.value else 1f
                                scaleY = scaleX
                                transformOrigin = TransformOrigin(1f, .5f)
                                translationY = (207f * distance + 17f * distance.coerceIn(-1f, 1f)) * unit.toPx()
                                rotationX = (-distance * 18f).coerceIn(-50f, 50f)
                                cameraDistance = 14f * density
                                alpha = (2.7f - kotlin.math.abs(distance)).coerceIn(0f, 1f)
                                shape = RoundedCornerShape(unit * 25f)
                                clip = true
                            },
                    )
                }
                Icon(painterResource(R.drawable.deeplist_roll_pointer), null, tint = Color(0xFFF0908D),
                    modifier = Modifier.offset(x = unit * 86f, y = unit * 198.5f)
                        .size(width = unit * 68f, height = unit * 75f).graphicsLayer {
                            translationX = -pointer.value * 7.dp.toPx()
                            rotationZ = -pointer.value * 9f
                        })
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.deeplist_gacha_title), color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(when {
                        !enabled -> R.string.deeplist_gacha_guest
                        available.size < 4 -> R.string.deeplist_gacha_wait
                        else -> R.string.deeplist_gacha_hint
                    }), color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Button(
                    enabled = enabled && !rolling && available.size >= 4,
                    onClick = {
                        if (!rolling) {
                            if (sounds == null) sounds = GachaSounds(context)
                            rolling = true
                            scope.launch {
                                try {
                                    val picks = available.shuffled().take(10)
                                    reel.snapTo(0f)
                                    landingPop.snapTo(1f)
                                    candidates = picks
                                    val index = Random.nextInt(picks.size)
                                    // The recording has 2.67s from its first audible tick to the win; retain the 2.8s roll.
                                    val duration = 2800
                                    val rotations = 2 + duration / 2500
                                    reel.animateTo((picks.size * rotations + index).toFloat(),
                                        tween(duration, easing = CubicBezierEasing(.15f, .45f, .2f, 1f)))
                                    sounds?.win()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    latestOnWinner(picks[index])
                                    launch {
                                        landingPop.animateTo(1.1f, tween(120))
                                        landingPop.animateTo(1f, spring(dampingRatio = .35f, stiffness = 320f))
                                    }
                                } finally { rolling = false }
                            }
                        }
                    },
                ) { Text(stringResource(if (rolling) R.string.deeplist_gacha_rolling else R.string.deeplist_gacha_roll)) }
            }
        }
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
