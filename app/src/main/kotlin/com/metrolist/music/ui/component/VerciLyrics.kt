package com.metrolist.music.ui.component

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.metrolist.music.constants.*
import com.metrolist.music.lyrics.*
import com.metrolist.music.utils.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import org.json.JSONObject
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
import com.metrolist.music.lyrics.buildVerciGaps
import com.metrolist.music.lyrics.VerciGap
import com.metrolist.music.lyrics.VerciHeartbeat
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
    var locked by rememberPreference(DeeplistTouchLockKey, false)
    val renderer by rememberEnumPreference(VerciStyleKey, VerciStyle.FISHEYE)
    var saved by rememberPreference(DeeplistSavedLyricsKey, "[]")
    var correctPhrase by remember { mutableStateOf<String?>(null) }
    var actions by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun saveLine(text: String) {
        val track = metadata ?: return
        saved = deeplistSaveLyric(saved, DeeplistSavedLyric(text, track.title, track.artists.joinToString { it.name }))
    }
    LaunchedEffect(metadata?.id) {
        val id = metadata?.id ?: return@LaunchedEffect
        val prefs = context.dataStore.data.first()
        val json = runCatching { JSONObject(prefs[DeeplistSongStylesKey] ?: "{}").optString(id) }.getOrDefault("")
        if (json.isNotBlank()) runCatching { DeeplistSetup.decode(json) }.onSuccess { values ->
            context.dataStore.edit { DeeplistSetup.apply(it, values, false) }
        }
    }
    val doubleTap by rememberPreference(VerciDoubleTapKey, true)
    val swipe by rememberPreference(VerciSwipeKey, true)
    val manager = com.metrolist.music.LocalListenTogetherManager.current
    val canControl = manager?.isInRoom != true || manager.isHost
    var controls by remember(immersive, recording) { mutableStateOf(!immersive && !recording) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    val latestSlider by rememberUpdatedState(sliderPositionProvider)
    val previewProvider = remember { { seekPreview ?: latestSlider() } }
    val view = LocalView.current
    DisposableEffect(view, immersive) {
        val before = view.keepScreenOn
        if (immersive) view.keepScreenOn = true
        onDispose { view.keepScreenOn = before }
    }
    LaunchedEffect(controls, settings, seekPreview, immersive, recording) {
        if ((immersive || recording) && controls && !settings && seekPreview == null) { delay(4000); controls = false }
    }
    val dragThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val gestures = Modifier.pointerInput(immersive, recording, doubleTap, canControl, locked) {
        detectTapGestures(
            onTap = { if (immersive || recording || locked) controls = !controls },
            onLongPress = { if (!locked) actions = true },
            onDoubleTap = if (doubleTap && canControl && !locked) ({ _: androidx.compose.ui.geometry.Offset -> if (connection.player.isPlaying) connection.player.pause() else connection.player.play() }) else null,
        )
    }.pointerInput(swipe, canControl, locked) {
        var drag = 0f
        detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onHorizontalDrag = { change, amount -> if (swipe && canControl && !locked) { change.consume(); drag += amount } },
            onDragEnd = { if (swipe && canControl && !locked && kotlin.math.abs(drag) > dragThreshold) { if (drag < 0) connection.seekToNext() else connection.seekToPrevious() } },
        )
    }
    val source = entity?.takeIf { it.id == metadata?.id }?.lyrics
    if (!showLyrics) return

    if (!lyricsTextLooksSynced(source)) {
        WordstormLyrics(sliderPositionProvider, true, onOriginal, modifier, immersive, onFullscreen, onExitFullscreen, useVerci = false)
        return
    }
    Box(modifier.fillMaxSize().background(Color.Black).then(gestures)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val stage = if (recording) Modifier.fillMaxWidth().aspectRatio(9f / 16f, matchHeightConstraintsFirst = maxWidth / maxHeight > 9f / 16f) else Modifier.fillMaxSize()
        Box(stage) {
        if (renderer == VerciStyle.APPLE) {
            key(metadata?.id, source) {
                DeeplistAppleLyrics(source.orEmpty(), previewProvider, locked, { saveLine(it) }, { correctPhrase = it }, { controls = !controls })
            }
        } else
        key(metadata?.id, source) {
            val words by produceState<List<VerciWord>?>(null, source) {
                value = withContext(Dispatchers.Default) { buildVerciWords(LyricsUtils.parseLyrics(source.orEmpty())) }
            }
            val gaps by produceState(emptyList<VerciGap>(), source) {
                value = withContext(Dispatchers.Default) { buildVerciGaps(LyricsUtils.parseLyrics(source.orEmpty())) }
            }
            val timeline = words
            if (timeline.isNullOrEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (timeline != null) Text(stringResource(R.string.lyrics_not_found))
                }
            } else {
                VerciWordCanvas(timeline, gaps, previewProvider, Modifier, !recording && (!immersive || controls))
            }
        }
        }
        }
        if (controls || (!immersive && !recording)) Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { verciEnabled = false }, enabled = !locked) { Text(stringResource(R.string.verci_original)) }
            TextButton(onClick = { if (immersive) onExitFullscreen() else onFullscreen() }) {
                Text(stringResource(if (immersive) R.string.deeplist_exit_fullscreen else R.string.deeplist_fullscreen))
            }
            TextButton(onClick = { settings = true }) { Text(stringResource(R.string.wordstorm_settings)) }
        }
        if (locked && (controls || !immersive)) TextButton(onClick = { locked = false }, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding()) { Text(stringResource(R.string.deeplist_unlock)) }
        if (immersive && controls && !locked) VerciTransport(seekPreview, { seekPreview = it }, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        VerciEdgeNeon(playing, immersive)

    }
    correctPhrase?.let { phrase -> DeeplistEmojiEditor(metadata?.id, phrase) { correctPhrase = null } }
    if (actions) {
        val currentTime = connection.player.currentPosition
        val entries = remember(source) { LyricsUtils.parseLyrics(source.orEmpty()).filter { !it.isBackground && it.text.isNotBlank() }.sortedBy { it.time } }
        val song = connection.currentSong.collectAsStateWithLifecycle(initialValue = null).value
        val now = currentTime + (song?.song?.lyricsOffset ?: 0)
        val line = entries.lastOrNull { it.time <= now }
        val word = remember(source, now) { buildVerciWords(entries).let { it.getOrNull(verciWordIndex(it, now))?.text.orEmpty() } }
        AlertDialog(onDismissRequest = { actions = false }, title = { Text(line?.text.orEmpty()) }, text = {
            androidx.compose.foundation.layout.Column {
                TextButton(onClick = { line?.let { saveLine(it.text) }; actions = false }, enabled = line != null) { Text(stringResource(R.string.deeplist_save_lyric)) }
                TextButton(onClick = { correctPhrase = word; actions = false }, enabled = word.isNotBlank()) { Text(stringResource(R.string.deeplist_emoji_corrections)) }
            }
        }, confirmButton = { TextButton(onClick = { actions = false }) { Text(stringResource(R.string.wordstorm_done)) } })
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
private fun VerciWordCanvas(words: List<VerciWord>, gaps: List<VerciGap>, sliderPositionProvider: () -> Long?, modifier: Modifier, showTiming: Boolean) {
    val connection = LocalPlayerConnection.current ?: return
    val player = connection.player
    val owner = LocalLifecycleOwner.current
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val offset by rememberUpdatedState(song?.song?.lyricsOffset ?: 0)
    val slider by rememberUpdatedState(sliderPositionProvider)
    val style by rememberEnumPreference(VerciStyleKey, VerciStyle.FISHEYE)
    val emojis by rememberPreference(VerciEmojiKey, true)
    val advanced by rememberPreference(com.metrolist.music.constants.VerciAdvancedEmojiKey, false)
    val beatLift by rememberPreference(com.metrolist.music.constants.VerciBeatLiftKey, true)
    val mega by rememberPreference(com.metrolist.music.constants.VerciMegaEmojiKey, true)
    val correctionsRaw by rememberPreference(DeeplistEmojiCorrectionsKey, "[]")
    val iconDensity by rememberPreference(DeeplistEmojiDensityKey, 1)
    val iconPack by rememberPreference(DeeplistEmojiPackKey, 0)
    val animation by rememberPreference(DeeplistAnimationKey, 1f)
    val reduce by rememberPreference(DeeplistPerformanceKey, false)
    val autoPerformance by rememberPreference(DeeplistAutoPerformanceKey, true)
    val economy = rememberDeeplistEconomy(connection.player.isPlaying, autoPerformance, song?.song?.id) || reduce
    val corrections = remember(correctionsRaw) { runCatching { decodeEmojiCorrections(correctionsRaw) }.getOrDefault(emptyList()) }
    val preparedIcons = remember(words, corrections, song?.song?.id, advanced, emojis, mega, iconDensity) {
        deeplistEmojiTimeline(words, song?.song?.id.orEmpty(), corrections, advanced, emojis, mega, iconDensity)
    }
    val chorusBoost by rememberPreference(DeeplistChorusBoostKey, true)
    val marksRaw by rememberPreference(DeeplistChorusMarksKey, "{}")
    val lyricsEntity by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val chorusFlags = remember(words, lyricsEntity?.lyrics, marksRaw, song?.song?.id) {
        val lines = LyricsUtils.parseLyrics(lyricsEntity?.takeIf { it.id == song?.song?.id }?.lyrics.orEmpty()).filter { !it.isBackground }.sortedBy { it.time }
        val chorus = deeplistChorusLines(lines, deeplistChorusMarks(marksRaw, song?.song?.id.orEmpty()))
        val times = lines.map { it.time }
        words.map { word -> lines.getOrNull(wordstormLineIndex(times, word.start))?.time?.let { it in chorus } == true }
    }
    val moods = remember(words) { words.indices.map { i -> deeplistLyricMood(words.subList((i - 4).coerceAtLeast(0), (i + 5).coerceAtMost(words.size)).joinToString(" ") { it.text }) } }
    val heartbeat = remember { VerciHeartbeat() }
    var pulse by remember { mutableFloatStateOf(0f) }
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
    val fontPx = with(density) { textSize.coerceIn(28f, 160f).sp.toPx() }
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
                    heartbeat.reset()
                    pulse = 0f
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
    LaunchedEffect(player, owner, playing, beatLift, economy, animation) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            clock.reset()
            heartbeat.reset()
            pulse = 0f
            time = (slider() ?: player.currentPosition) + offset
            scroll = verciScrollPosition(words, time)
            if (playing) {
                var previousFrame = 0L
                while (isActive) {
                    withFrameNanos { frame ->
                        val preview = slider()
                        if (preview != null) heartbeat.reset()
                        val frameDt = if (previousFrame == 0L) 0f else ((frame - previousFrame) / 1e9f).coerceIn(0f, .05f)
                        pulse = if (beatLift && preview == null && animation > 0f) heartbeat.sample(connection.service.lyricsAudioEnergy, frame / 1000000, frameDt) else 0f
                        time = if (preview != null) {
                            clock.reset()
                            preview + offset
                        } else clock.position(player.currentPosition, frame, player.playbackParameters.speed) + offset
                        val target = if (animation == 0f) verciWordIndex(words, time).toFloat() else verciScrollPosition(words, time)
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
                val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.textSize = min(fontPx * .58f, 40.dp.toPx())
                    if (iconPack > 0) colorFilter = android.graphics.PorterDuffColorFilter(if (iconPack == 1) foreground else accent, android.graphics.PorterDuff.Mode.SRC_IN)
                }
                val displayIcons = preparedIcons.map { icons ->
                    icons.map { icon ->
                        if (icon == "🫪" && !emojiPaint.hasGlyph(icon)) "😳"
                        else if (icon == "🫯" && !emojiPaint.hasGlyph(icon)) "💥" else icon
                    }
                }
                val emojiWidths = displayIcons.map { icons -> icons.maxOfOrNull { emojiPaint.measureText(it) } ?: 0f }
                val gapLayouts = gaps.map { gap ->
                    val textPaint = android.text.TextPaint(paint).apply { this.textSize = fontPx * .68f; color = accent; alpha = 140 }
                    android.text.StaticLayout.Builder.obtain(gap.echo, 0, gap.echo.length, textPaint, (size.width * .82f).toInt().coerceAtLeast(1))
                        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setMaxLines(5).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
                }
                val atmosphere = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(size.width * .18f, size.height * .25f,
                        size.height * .75f, intArrayOf((accent and 0x00ffffff) or 0x28000000, 0x00000000), null, Shader.TileMode.CLAMP)
                }
                val spark = Paint(Paint.ANTI_ALIAS_FLAG)
                val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; alpha = 170; this.textSize = 28.dp.toPx(); textAlign = Paint.Align.CENTER }
                var clockMinute = -1L
                var clockText = ""
                val camera = android.graphics.Camera()
                val perspective = android.graphics.Matrix()
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
                    if (particles && !economy && animation > 0f) {
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
                    val gapIndex = gaps.indexOfFirst { now >= it.start && now < it.end }
                    val lift = animation * pulse * min(24.dp.toPx(), size.height * .029f) * .75f
                    c.save()
                    c.translate(0f, -lift)
                    c.rotate(pulse * sin(now / 1000f * 2.8f) * .6f, size.width / 2, size.height / 2)
                    if (gapIndex >= 0) {
                        val layout = gapLayouts[gapIndex]
                        spark.color = accent; spark.alpha = (25 + 30 * pulse.coerceAtLeast(0f)).toInt()
                        c.drawCircle(size.width / 2, size.height / 2, size.width * (.27f + pulse * .009f), spark)
                        c.save(); c.translate((size.width - layout.width) / 2, (size.height - layout.height) / 2)
                        layout.draw(c); c.restore()
                    }
                    if (gapIndex < 0) for (i in (center - 4).coerceAtLeast(0)..(center + 5).coerceAtMost(words.lastIndex)) {
                        val word = words[i]
                        val u = i - position
                        val distance = abs(u)
                        val fisheye = style == VerciStyle.FISHEYE || style == VerciStyle.FISHEYE_VISUAL
                        val scale = if (fisheye) 1f / (1f + .8f * distance)
                            else (1f - .16f * distance).coerceIn(.35f, 1f)
                        val y = if (fisheye) tanh(u * .62f) * size.height * .43f * spacing else u * size.height * .14f * spacing
                        val drift = if (style == VerciStyle.SHIP) sin(i * 127.1f) * size.width * .22f * min(1f, distance) else 0f
                        val giant = textSize > 80f
                        val hasIcons = displayIcons[i].isNotEmpty()
                        val iconWidth = if (hasIcons && !giant) emojiWidths[i] + fontPx * .18f else 0f
                        val fit = min(1f, (size.width * (if (giant) .90f else .84f) - abs(drift) * 2).coerceAtLeast(size.width * .35f) / (widths[i] + iconWidth))
                        val progress = ((now - word.start).toFloat() / (word.end - word.start)).coerceIn(0f, 1f)
                        val pop = if (i == active && animation > 0f) sin(progress * Math.PI).toFloat() * (if (chorusBoost && chorusFlags[i]) .1f else .07f) * animation else 0f
                        val alpha = ((1 - .22f * distance).coerceIn(0f, 1f) * 255).toInt()
                        c.save()
                        c.translate(size.width / 2 + drift, size.height / 2 + y)
                        c.scale(scale * fit * (1 + pop), scale * fit * (1 + pop))
                        if (style == VerciStyle.SHIP && animation > 0f && !economy) {
                            camera.save()
                            camera.rotateX(kotlin.math.cos(now / 2100f + i * .7f) * 5f)
                            camera.rotateY(sin(now / 1700f + i) * 9f)
                            camera.getMatrix(perspective)
                            camera.restore()
                            c.concat(perspective)
                        }
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
                        if (hasIcons) {
                            emojiPaint.alpha = if (i == active) alpha else (alpha * .3f).toInt()
                            val mood = moods[i]
                            val effect = if (i == active && !economy) animation * sin(progress * Math.PI).toFloat() * if (mood < 0) .35f else if (mood > 0) 1.2f else 1f else 0f
                            val icons = displayIcons[i]
                            icons.forEachIndexed { j, icon ->
                                val iconX = if (giant) (j - icons.size / 2f) * emojiWidths[i] else x + widths[i] + fontPx * .18f
                                val iconY = if (giant) baseline + metrics.descent + emojiPaint.textSize else emojiBaseline + (j - (icons.size - 1) / 2f) * fontPx * .52f
                                val motion = emojiMotion(icon)
                                val dy = when (motion) { 1 -> effect * 10.dp.toPx(); 2 -> -effect * 16.dp.toPx(); else -> 0f }
                                c.save(); c.translate(0f, dy)
                                if (motion == 3) c.rotate(effect * 9f, iconX, iconY)
                                c.drawText(icon, iconX, iconY, emojiPaint); c.restore()
                            }
                        }
                        c.restore()
                    }
                    c.restore()
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
