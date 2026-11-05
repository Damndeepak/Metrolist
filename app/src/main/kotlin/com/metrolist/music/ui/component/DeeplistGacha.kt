package com.metrolist.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
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
internal fun DeeplistGacha(pool: List<MediaMetadata>, onWinner: (MediaMetadata) -> Unit) {
    val available = remember(pool) { pool.distinctBy { it.id } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestOnWinner by rememberUpdatedState(onWinner)
    var candidates by remember { mutableStateOf(emptyList<MediaMetadata>()) }
    var winner by remember { mutableStateOf<MediaMetadata?>(null) }
    var rolling by remember { mutableStateOf(false) }
    val reel = remember { Animatable(0f) }
    val covers = candidates.ifEmpty { available.take(9) }
    if (covers.size < 4) return

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.deeplist_gacha_title), style = MaterialTheme.typography.titleLarge)
            BoxWithConstraints(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp))) {
                val step = minOf(maxWidth / 3, 86.dp)
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
                        modifier = Modifier.align(Alignment.Center).offset(x = step * distance)
                            .size(step - 8.dp).graphicsLayer {
                                alpha = (1f - kotlin.math.abs(distance) * .25f).coerceIn(.3f, 1f)
                                val scale = (1f - kotlin.math.abs(distance) * .12f).coerceIn(.72f, 1f)
                                scaleX = scale
                                scaleY = scale
                            }.clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
            Text(
                winner?.title ?: stringResource(R.string.deeplist_gacha_hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                enabled = !rolling && available.size >= 4,
                onClick = {
                    if (!rolling) {
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
                                    tween(3200, easing = CubicBezierEasing(.12f, .65f, .12f, 1f)),
                                )
                                winner = picks[index]
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestOnWinner(picks[index])
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
