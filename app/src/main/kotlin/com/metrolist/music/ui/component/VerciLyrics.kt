package com.metrolist.music.ui.component

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.VerciLyricsKey
import com.metrolist.music.constants.VerciEmojiKey
import com.metrolist.music.constants.VerciNeonKey
import com.metrolist.music.constants.VerciStyle
import com.metrolist.music.constants.VerciStyleKey
import com.metrolist.music.constants.VerciTextSizeKey
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.VerciWord
import com.metrolist.music.lyrics.buildVerciWords
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.lyrics.verciScrollPosition
import com.metrolist.music.lyrics.verciWordIndex
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun VerciLyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier,
    showLyrics: Boolean,
    onOriginal: () -> Unit,
    immersive: Boolean,
    onFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
) {
    val connection = LocalPlayerConnection.current ?: return
    val entity by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    var verciEnabled by rememberPreference(VerciLyricsKey, false)
    var settings by remember { mutableStateOf(false) }
    val settingsItems = verciLyricsSettingsItems()
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    val source = entity?.takeIf { it.id == metadata?.id }?.lyrics
    if (!showLyrics) return

    if (!lyricsTextLooksSynced(source)) {
        WordstormLyrics(sliderPositionProvider, true, onOriginal, modifier, immersive, onFullscreen, onExitFullscreen, useVerci = false)
        return
    }
    Box(modifier.fillMaxSize()) {
        key(metadata?.id, source) {
            val words by produceState<List<VerciWord>?>(null, source) {
                value = withContext(Dispatchers.Default) { buildVerciWords(LyricsUtils.parseLyrics(source.orEmpty())) }
            }
            val timeline = words
            if (timeline.isNullOrEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (timeline != null) Text(stringResource(R.string.lyrics_not_found))
                }
            } else {
                VerciWordCanvas(timeline, sliderPositionProvider, Modifier)
            }
        }
        Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { verciEnabled = false }) { Text(stringResource(R.string.verci_original)) }
            TextButton(onClick = { if (immersive) onExitFullscreen() else onFullscreen() }) {
                Text(stringResource(if (immersive) R.string.deeplist_exit_fullscreen else R.string.deeplist_fullscreen))
            }
            TextButton(onClick = { settings = true }) { Text(stringResource(R.string.wordstorm_settings)) }
        }
        if (immersive) Row(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 8.dp)) {
            IconButton(onClick = { connection.seekToPrevious() }) {
                Icon(painterResource(R.drawable.skip_previous), stringResource(R.string.deeplist_previous))
            }
            IconButton(onClick = { if (playing) connection.player.pause() else connection.player.play() }) {
                Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), stringResource(R.string.deeplist_play_pause))
            }
            IconButton(onClick = { connection.seekToNext() }) {
                Icon(painterResource(R.drawable.skip_next), stringResource(R.string.deeplist_next))
            }
        }
    }
    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(stringResource(R.string.verci_lyrics)) },
            text = { Box(Modifier.verticalScroll(rememberScrollState())) { Material3SettingsGroup(items = settingsItems) } },
            confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.wordstorm_done)) } },
        )
    }
}

@Composable
private fun VerciWordCanvas(words: List<VerciWord>, sliderPositionProvider: () -> Long?, modifier: Modifier) {
    val connection = LocalPlayerConnection.current ?: return
    val player = connection.player
    val owner = LocalLifecycleOwner.current
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val offset by rememberUpdatedState(song?.song?.lyricsOffset ?: 0)
    val slider by rememberUpdatedState(sliderPositionProvider)
    val style by rememberEnumPreference(VerciStyleKey, VerciStyle.FISHEYE)
    val neon by rememberPreference(VerciNeonKey, true)
    val emojis by rememberPreference(VerciEmojiKey, true)
    val textSize by rememberPreference(VerciTextSizeKey, 52f)
    val density = LocalDensity.current
    val fontPx = with(density) { textSize.coerceIn(28f, 80f).sp.toPx() }
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val secondary = MaterialTheme.colorScheme.tertiary.toArgb()
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val background = MaterialTheme.colorScheme.surface.toArgb()
    var time by remember { mutableLongStateOf(player.currentPosition + offset) }
    var playing by remember { mutableStateOf(player.isPlaying) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                time = (slider() ?: player.currentPosition) + offset
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, owner, playing) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            time = (slider() ?: player.currentPosition) + offset
            if (playing) {
                while (isActive) {
                    withFrameNanos { time = (slider() ?: player.currentPosition) + offset }
                }
            } else {
                snapshotFlow { Pair(slider(), offset) }.collect { (preview, adjustment) ->
                    time = (preview ?: player.currentPosition) + adjustment
                }
            }
        }
    }
    val activeText by remember(words) {
        derivedStateOf { words.getOrNull(verciWordIndex(words, time))?.text.orEmpty() }
    }
    // Only draw reads the frame clock: text measurement and UI composition stay off the frame path.
    Box(modifier.fillMaxSize().clipToBounds()) {
        Canvas(
            Modifier.fillMaxSize().semantics { contentDescription = activeText }.drawWithCache {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.textSize = fontPx
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    color = foreground
                }
                val widths = words.map { paint.measureText(it.text).coerceAtLeast(1f) }
                val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.textSize = fontPx * .58f }
                val emojiWidths = words.map { emojiPaint.measureText(it.emoji) }
                val metrics = paint.fontMetrics
                val baseline = -(metrics.ascent + metrics.descent) / 2f
                val emojiMetrics = emojiPaint.fontMetrics
                val emojiBaseline = -(emojiMetrics.ascent + emojiMetrics.descent) / 2f
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
                val inset = 12.dp.toPx()
                val radius = 24.dp.toPx()
                val borderShader = SweepGradient(size.width / 2, size.height / 2,
                    intArrayOf(accent, secondary, accent, secondary, accent), null)
                val matrix = Matrix()
                val fadeHeight = min(size.height * .15f, 72.dp.toPx())
                val fade = Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, fadeHeight,
                        intArrayOf(background, background and 0x00ffffff), null, Shader.TileMode.CLAMP)
                }
                onDrawBehind {
                    val now = time
                    val position = verciScrollPosition(words, now)
                    val active = verciWordIndex(words, now)
                    val center = position.toInt()
                    val c = drawContext.canvas.nativeCanvas
                    for (i in (center - 4).coerceAtLeast(0)..(center + 5).coerceAtMost(words.lastIndex)) {
                        val word = words[i]
                        val u = i - position
                        val distance = abs(u)
                        val fisheye = style == VerciStyle.FISHEYE || style == VerciStyle.FISHEYE_VISUAL
                        val scale = if (fisheye) 1f / (1f + .8f * distance)
                            else (1f - .16f * distance).coerceIn(.35f, 1f)
                        val y = if (fisheye) tanh(u * .62f) * size.height * .43f else u * size.height * .14f
                        val drift = if (style == VerciStyle.SHIP) sin(i * 127.1f) * size.width * .22f * min(1f, distance) else 0f
                        val iconWidth = if (emojis && word.emoji.isNotEmpty()) emojiWidths[i] + fontPx * .18f else 0f
                        val fit = min(1f, (size.width * .84f - abs(drift) * 2).coerceAtLeast(size.width * .35f) / (widths[i] + iconWidth))
                        val progress = ((now - word.start).toFloat() / (word.end - word.start)).coerceIn(0f, 1f)
                        val pop = if (i == active) sin(progress * Math.PI).toFloat() * .07f else 0f
                        val alpha = ((1 - .22f * distance).coerceIn(0f, 1f) * 255).toInt()
                        c.save()
                        c.translate(size.width / 2 + drift, size.height / 2 + y)
                        c.scale(scale * fit * (1 + pop), scale * fit * (1 + pop))
                        if (style == VerciStyle.SHIP) c.rotate(sin(now / 1700f + i) * min(5f, distance * 3))
                        val x = -(widths[i] + iconWidth) / 2
                        paint.color = foreground
                        paint.alpha = (alpha * if (i == active) .38f else .62f).toInt()
                        c.drawText(word.text, x, baseline, paint)
                        if (i == active) {
                            c.save()
                            c.clipRect(x, baseline + metrics.top, x + widths[i] * progress, baseline + metrics.bottom)
                            paint.color = accent
                            paint.alpha = alpha
                            c.drawText(word.text, x, baseline, paint)
                            c.restore()
                        }
                        if (iconWidth > 0) {
                            emojiPaint.alpha = alpha
                            c.drawText(word.emoji, x + widths[i] + fontPx * .18f, emojiBaseline, emojiPaint)
                        }
                        c.restore()
                    }
                    // Soft edge fades and layered strokes avoid a full-screen blur or offscreen layer.
                    c.drawRect(0f, 0f, size.width, fadeHeight, fade)
                    c.save()
                    c.rotate(180f, size.width / 2, size.height / 2)
                    c.drawRect(0f, 0f, size.width, fadeHeight, fade)
                    c.restore()
                    if (neon) {
                        matrix.setRotate((now % 8000L) * 360f / 8000, size.width / 2, size.height / 2)
                        borderShader.setLocalMatrix(matrix)
                        border.shader = borderShader
                        for (layer in 3 downTo 1) {
                            border.strokeWidth = (layer * layer * 1.5f).dp.toPx()
                            border.alpha = if (layer == 1) 230 else 24
                            c.drawRoundRect(inset, inset, size.width - inset, size.height - inset, radius, radius, border)
                        }
                    }
                    if (style == VerciStyle.VISUAL || style == VerciStyle.FISHEYE_VISUAL) {
                        border.shader = null
                        border.color = accent
                        border.alpha = 100
                        border.strokeWidth = 3.dp.toPx()
                        for (bar in 0..10) {
                            val h = (6 + 14 * abs(sin(now / 220f + bar * .8f))).dp.toPx()
                            val x = size.width / 2 + (bar - 5) * 9.dp.toPx()
                            c.drawLine(x, size.height - inset * 2, x, size.height - inset * 2 - h, border)
                        }
                    }
                }
            },
        ) {}
        if (words.any { it.estimated }) Text(
            stringResource(R.string.verci_timing_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 68.dp),
        )
    }
}
