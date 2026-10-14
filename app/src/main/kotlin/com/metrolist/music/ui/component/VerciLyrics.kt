package com.metrolist.music.ui.component

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
import com.metrolist.music.constants.VerciRecordingKey
import com.metrolist.music.constants.VerciDoubleTapKey
import com.metrolist.music.constants.VerciSwipeKey
import com.metrolist.music.constants.VerciFontKey
import com.metrolist.music.constants.VerciFont
import com.metrolist.music.constants.VerciColorKey
import com.metrolist.music.constants.VerciSpacingKey
import com.metrolist.music.constants.VerciLyricsKey
import com.metrolist.music.constants.VerciEmojiKey
import com.metrolist.music.constants.VerciNeonKey
import com.metrolist.music.constants.VerciStyle
import com.metrolist.music.constants.VerciStyleKey
import com.metrolist.music.constants.VerciTextSizeKey
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.VerciPlaybackClock
import com.metrolist.music.lyrics.verciDampPosition
import com.metrolist.music.lyrics.VerciWord
import com.metrolist.music.lyrics.buildVerciWords
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.lyrics.verciScrollPosition
import com.metrolist.music.lyrics.verciWordIndex
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.delay
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
    val recording by rememberPreference(VerciRecordingKey, false)
    val doubleTap by rememberPreference(VerciDoubleTapKey, true)
    val swipe by rememberPreference(VerciSwipeKey, true)
    val manager = com.metrolist.music.LocalListenTogetherManager.current
    val canControl = manager?.isInRoom != true || manager.isHost
    var controls by remember(immersive) { mutableStateOf(!immersive) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    val latestSlider by rememberUpdatedState(sliderPositionProvider)
    val previewProvider = remember { { seekPreview ?: latestSlider() } }
    val view = LocalView.current
    DisposableEffect(view, immersive) {
        val before = view.keepScreenOn
        if (immersive) view.keepScreenOn = true
        onDispose { view.keepScreenOn = before }
    }
    LaunchedEffect(controls, settings, seekPreview, immersive) {
        if (immersive && controls && !settings && seekPreview == null) { delay(4000); controls = false }
    }
    val dragThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val gestures = Modifier.pointerInput(immersive, doubleTap, canControl) {
        detectTapGestures(
            onTap = { if (immersive) controls = !controls },
            onDoubleTap = if (doubleTap && canControl) ({ _: androidx.compose.ui.geometry.Offset -> if (connection.player.isPlaying) connection.player.pause() else connection.player.play() }) else null,
        )
    }.pointerInput(swipe, canControl) {
        var drag = 0f
        detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onHorizontalDrag = { change, amount -> if (swipe && canControl) { change.consume(); drag += amount } },
            onDragEnd = { if (swipe && canControl && kotlin.math.abs(drag) > dragThreshold) { if (drag < 0) connection.seekToNext() else connection.seekToPrevious() } },
        )
    }
    val source = entity?.takeIf { it.id == metadata?.id }?.lyrics
    if (!showLyrics) return

    if (!lyricsTextLooksSynced(source)) {
        WordstormLyrics(sliderPositionProvider, true, onOriginal, modifier, immersive, onFullscreen, onExitFullscreen, useVerci = false)
        return
    }
    Box(modifier.fillMaxSize().then(gestures)) {
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
                VerciWordCanvas(timeline, previewProvider, Modifier, !recording && (!immersive || controls))
            }
        }
        if (!immersive || controls) Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { verciEnabled = false }) { Text(stringResource(R.string.verci_original)) }
            TextButton(onClick = { if (immersive) onExitFullscreen() else onFullscreen() }) {
                Text(stringResource(if (immersive) R.string.deeplist_exit_fullscreen else R.string.deeplist_fullscreen))
            }
            TextButton(onClick = { settings = true }) { Text(stringResource(R.string.wordstorm_settings)) }
        }
        if (immersive && controls) VerciTransport(seekPreview, { seekPreview = it }, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        VerciEdgeNeon(playing, immersive)

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
private fun VerciWordCanvas(words: List<VerciWord>, sliderPositionProvider: () -> Long?, modifier: Modifier, showTiming: Boolean) {
    val connection = LocalPlayerConnection.current ?: return
    val player = connection.player
    val owner = LocalLifecycleOwner.current
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val offset by rememberUpdatedState(song?.song?.lyricsOffset ?: 0)
    val slider by rememberUpdatedState(sliderPositionProvider)
    val style by rememberEnumPreference(VerciStyleKey, VerciStyle.FISHEYE)
    val emojis by rememberPreference(VerciEmojiKey, true)
    val advanced by rememberPreference(com.metrolist.music.constants.VerciAdvancedEmojiKey, false)
    val particles by rememberPreference(com.metrolist.music.constants.VerciParticlesKey, false)
    val clockVisible by rememberPreference(com.metrolist.music.constants.VerciClockKey, false)
    val wallTime by produceState(System.currentTimeMillis(), clockVisible) {
        if (clockVisible) while (isActive) { value = System.currentTimeMillis(); delay(1000) }
    }
    val textSize by rememberPreference(VerciTextSizeKey, 52f)
    val font by rememberEnumPreference(VerciFontKey, VerciFont.BOLD)
    val spacing by rememberPreference(VerciSpacingKey, 1f)
    val colorIndex by rememberPreference(VerciColorKey, 0)
    val density = LocalDensity.current
    val fontPx = with(density) { textSize.coerceIn(28f, 80f).sp.toPx() }
    val accent = verciColors[colorIndex.coerceIn(0, 4)]
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val background = MaterialTheme.colorScheme.surface.toArgb()
    var time by remember { mutableLongStateOf(player.currentPosition + offset) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    val clock = remember(player) { VerciPlaybackClock() }
    var scroll by remember(words) { mutableFloatStateOf(verciScrollPosition(words, time)) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                    clock.reset()
                    time = (slider() ?: player.currentPosition) + offset
                    scroll = verciScrollPosition(words, time)
                } else if (!playing) {
                    time = (slider() ?: player.currentPosition) + offset
                    scroll = verciScrollPosition(words, time)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, owner, playing) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            clock.reset()
            time = (slider() ?: player.currentPosition) + offset
            scroll = verciScrollPosition(words, time)
            if (playing) {
                var previousFrame = 0L
                while (isActive) {
                    withFrameNanos { frame ->
                        val preview = slider()
                        time = if (preview != null) {
                            clock.reset()
                            preview + offset
                        } else clock.position(player.currentPosition, frame, player.playbackParameters.speed) + offset
                        val target = verciScrollPosition(words, time)
                        scroll = if (preview != null || previousFrame == 0L || kotlin.math.abs(target - scroll) > 2f) target
                            else verciDampPosition(scroll, target, frame - previousFrame)
                        previousFrame = frame
                    }
                }
            } else {
                snapshotFlow { Pair(slider(), offset) }.collect { (preview, adjustment) ->
                    time = (preview ?: player.currentPosition) + adjustment
                    scroll = verciScrollPosition(words, time)
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
                    typeface = Typeface.create(when (font) {
                        VerciFont.BOLD -> "sans-serif"
                        VerciFont.ROUNDED -> "sans-serif-rounded"
                        VerciFont.SERIF -> "serif"
                        VerciFont.MONO -> "monospace"
                    }, Typeface.BOLD)
                    color = foreground
                }
                val widths = words.map { paint.measureText(it.text).coerceAtLeast(1f) }
                val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.textSize = fontPx * .58f }
                val icons = words.map { if (advanced) com.metrolist.music.lyrics.verciEmoji(it.text, true, emojis) else if (emojis) it.emoji else "" }
                val displayIcons = icons.map { icon ->
                    if (icon == "🫪" && !emojiPaint.hasGlyph(icon)) "😳"
                    else if (icon == "🫯" && !emojiPaint.hasGlyph(icon)) "💥" else icon
                }
                val emojiWidths = displayIcons.map { emojiPaint.measureText(it) }
                val atmosphere = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(size.width * .18f, size.height * .25f,
                        size.height * .75f, intArrayOf((accent and 0x00ffffff) or 0x28000000, 0x00000000), null, Shader.TileMode.CLAMP)
                }
                val spark = Paint(Paint.ANTI_ALIAS_FLAG)
                val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; alpha = 170; this.textSize = 28.dp.toPx(); textAlign = Paint.Align.CENTER }
                var clockMinute = -1L
                var clockText = ""
                val metrics = paint.fontMetrics
                val baseline = -(metrics.ascent + metrics.descent) / 2f
                val emojiMetrics = emojiPaint.fontMetrics
                val emojiBaseline = -(emojiMetrics.ascent + emojiMetrics.descent) / 2f
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
                val inset = 12.dp.toPx()
                val fadeHeight = min(size.height * .15f, 72.dp.toPx())
                val fade = Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, fadeHeight,
                        intArrayOf(background, background and 0x00ffffff), null, Shader.TileMode.CLAMP)
                }
                onDrawBehind {
                    val now = time
                    val position = scroll
                    val active = verciWordIndex(words, now)
                    val center = position.toInt()
                    val c = drawContext.canvas.nativeCanvas
                    if (style == VerciStyle.VISUAL || style == VerciStyle.FISHEYE_VISUAL) {
                        c.save(); c.translate(sin(now / 8000f) * size.width * .08f, 0f)
                        c.drawRect(-size.width * .1f, 0f, size.width * 1.1f, size.height, atmosphere); c.restore()
                    }
                    if (particles) {
                        spark.color = accent
                        for (j in 0..17) {
                            spark.alpha = (25 + 35 * abs(sin(now / 1900f + j))).toInt()
                            val x = (sin(j * 43.7f) * .46f + .5f) * size.width
                            val y = ((j * .137f + now / 90000f) % 1f) * size.height
                            c.drawCircle(x, y, (1 + j % 3).dp.toPx(), spark)
                        }
                    }
                    if (clockVisible) {
                        val minute = wallTime / 60000
                        if (minute != clockMinute) { clockMinute = minute; clockText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(wallTime)) }
                        c.drawText(clockText, size.width / 2, size.height * .12f, clockPaint)
                    }
                    for (i in (center - 4).coerceAtLeast(0)..(center + 5).coerceAtMost(words.lastIndex)) {
                        val word = words[i]
                        val u = i - position
                        val distance = abs(u)
                        val fisheye = style == VerciStyle.FISHEYE || style == VerciStyle.FISHEYE_VISUAL
                        val scale = if (fisheye) 1f / (1f + .8f * distance)
                            else (1f - .16f * distance).coerceIn(.35f, 1f)
                        val y = if (fisheye) tanh(u * .62f) * size.height * .43f * spacing else u * size.height * .14f * spacing
                        val drift = if (style == VerciStyle.SHIP) sin(i * 127.1f) * size.width * .22f * min(1f, distance) else 0f
                        val iconWidth = if (displayIcons[i].isNotEmpty()) emojiWidths[i] + fontPx * .18f else 0f
                        val fit = min(1f, (size.width * .84f - abs(drift) * 2).coerceAtLeast(size.width * .35f) / (widths[i] + iconWidth))
                        val progress = ((now - word.start).toFloat() / (word.end - word.start)).coerceIn(0f, 1f)
                        val pop = if (i == active) sin(progress * Math.PI).toFloat() * .07f else 0f
                        val alpha = ((1 - .22f * distance).coerceIn(0f, 1f) * 255).toInt()
                        c.save()
                        c.translate(size.width / 2 + drift, size.height / 2 + y)
                        c.scale(scale * fit * (1 + pop), scale * fit * (1 + pop))
                        if (style == VerciStyle.SHIP) c.rotate(sin(now / 1700f + i) * min(5f, distance * 3))
                        val x = -(widths[i] + iconWidth) / 2
                        paint.color = if (i > active) accent else foreground
                        paint.alpha = (alpha * if (i == active) .38f else if (i > active) .45f else .72f).toInt()
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
                            c.drawText(displayIcons[i], x + widths[i] + fontPx * .18f, emojiBaseline, emojiPaint)
                        }
                        c.restore()
                    }
                    // Soft edge fades and layered strokes avoid a full-screen blur or offscreen layer.
                    c.drawRect(0f, 0f, size.width, fadeHeight, fade)
                    c.save()
                    c.rotate(180f, size.width / 2, size.height / 2)
                    c.drawRect(0f, 0f, size.width, fadeHeight, fade)
                    c.restore()

                }
            },
        ) {}
        if (showTiming && words.any { it.estimated }) Text(
            stringResource(R.string.verci_timing_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 68.dp),
        )
    }
}
