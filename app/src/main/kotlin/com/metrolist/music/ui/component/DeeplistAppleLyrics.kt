package com.metrolist.music.ui.component

import android.graphics.Path
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.*
import com.metrolist.music.lyrics.*
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin

@Composable
internal fun DeeplistAppleLyrics(source: String, slider: () -> Long?, locked: Boolean, onSave: (String) -> Unit, onCorrect: (String) -> Unit, onToggleControls: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    val requestedSize by rememberPreference(DeeplistAppleSizeKey, 32f)
    val wordZoom by rememberPreference(DeeplistWordZoomKey, .18f)
    val wordSpacing by rememberPreference(DeeplistWordSpacingKey, 1f)
    val lineSpacing by rememberPreference(VerciSpacingKey, 1f)
    val wordGlow by rememberPreference(DeeplistWordGlowKey, true)
    val beatLink by rememberPreference(DeeplistAppleBeatKey, false)
    val blur by rememberPreference(DeeplistSoftBlurKey, .35f)
    val intensity by rememberPreference(DeeplistAnimationKey, 1f)
    val reduce by rememberPreference(DeeplistPerformanceKey, false)
    val auto by rememberPreference(DeeplistAutoPerformanceKey, true)
    val correctionsRaw by rememberPreference(DeeplistEmojiCorrectionsKey, "[]")
    val basic by rememberPreference(VerciEmojiKey, true)
    val advanced by rememberPreference(VerciAdvancedEmojiKey, false)
    val mega by rememberPreference(VerciMegaEmojiKey, true)
    val density by rememberPreference(DeeplistEmojiDensityKey, 1)
    val iconPack by rememberPreference(DeeplistEmojiPackKey, 0)
    val colorIndex by rememberPreference(VerciColorKey, 0)
    val roman by rememberPreference(DeeplistRomanHindiKey, false)
    val chorusBoost by rememberPreference(DeeplistChorusBoostKey, true)
    val duet by rememberPreference(DeeplistDuetEnabledKey, false)
    val duetTags by rememberPreference(DeeplistDuetTagsKey, "{}")
    val marksRaw by rememberPreference(DeeplistChorusMarksKey, "{}")
    val economy = rememberDeeplistEconomy(playing, auto, metadata?.id) || reduce
    val lines by produceState(emptyList<LyricsEntry>(), source) {
        value = withContext(Dispatchers.Default) { LyricsUtils.parseLyrics(source).filter { !it.isBackground && it.text.isNotBlank() }.sortedBy { it.time } }
    }
    val displayLines = remember(lines, roman) { if (roman) lines.map { it.copy(text = deeplistRomanHindi(it.text), words = null) } else lines }
    val chorusTimes = remember(lines, marksRaw, metadata?.id) { deeplistChorusLines(lines, deeplistChorusMarks(marksRaw, metadata?.id.orEmpty())) }
    val words = remember(lines) { buildVerciWords(lines) }
    val singers = remember(lines, duetTags, metadata?.id, duet) {
        IntArray(lines.size) { i -> if (duet) deeplistSinger(duetTags, metadata?.id.orEmpty(), lines[i].time) else 0 }
    }
    val corrections = remember(correctionsRaw) { runCatching { decodeEmojiCorrections(correctionsRaw) }.getOrDefault(emptyList()) }
    val icons = remember(words, corrections, metadata?.id, basic, advanced, mega, density) {
        deeplistEmojiTimeline(words, metadata?.id.orEmpty(), corrections, advanced, basic, mega, density)
    }
    val owner = LocalLifecycleOwner.current
    val latestSlider by rememberUpdatedState(slider)
    val offset by rememberUpdatedState(song?.song?.lyricsOffset ?: 0)
    var time by remember(source) { mutableLongStateOf(connection.player.currentPosition + offset) }
    var scroll by remember(source) { mutableFloatStateOf(0f) }
    val times = remember(lines) { lines.map { it.time } }
    val active by remember(times) { derivedStateOf { wordstormLineIndex(times, time) } }
    val activeText by remember(displayLines) { derivedStateOf { displayLines.getOrNull(active)?.text.orEmpty() } }
    val manager = LocalListenTogetherManager.current
    val tapSeek by rememberPreference(DeeplistTapSeekKey, false)
    val recording by rememberPreference(VerciRecordingKey, false)
    val canSeek = tapSeek && !recording && !locked && (manager?.isInRoom != true || manager.isHost)
    val hits = remember { AppleLyricHits() }
    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(connection, source, times, playing, owner, economy, intensity, beatLink) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val clock = VerciPlaybackClock()
            val spring = DeeplistLyricSpring().apply { reset(scroll) }
            val heartbeat = VerciHeartbeat()
            var previous = 0L
            var previousCenters: FloatArray? = hits.centers
            var snap = false
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo,
                    newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) { snap = true; clock.reset(); heartbeat.reset() }
                override fun onPlaybackParametersChanged(parameters: androidx.media3.common.PlaybackParameters) { clock.reset() }
            }
            connection.player.addListener(listener)
            try {
                while (isActive) {
                    withFrameNanos { frame ->
                        val preview = latestSlider()
                        time = (preview ?: if (playing) clock.position(connection.player.currentPosition, frame,
                            connection.player.playbackParameters.speed) else connection.player.currentPosition) + offset
                        if (preview != null) { clock.reset(); heartbeat.reset() }
                        val centers = hits.centers
                        val target = centers.getOrElse(wordstormLineIndex(times, time).coerceAtLeast(0)) { 0f }
                        val dt = if (previous == 0L) 0f else ((frame - previous) / 1e9f).coerceIn(0f, .1f)
                        if (snap || preview != null || intensity == 0f || previousCenters !== centers) {
                            spring.reset(target); snap = false
                        }
                        scroll = spring.advance(target, dt, 9f / intensity.coerceIn(.5f, 1.5f))
                        previousCenters = centers
                        hits.ready = true
                        pulse = if (beatLink && playing && !economy && preview == null && intensity > 0f)
                            heartbeat.sample(connection.service.lyricsAudioEnergy, frame / 1_000_000, dt) else 0f
                        previous = frame
                    }
                    if (!playing) delay(40)
                }
            } finally { connection.player.removeListener(listener) }
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Canvas(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 24.dp)
            .semantics { contentDescription = activeText }
            .pointerInput(displayLines, canSeek, offset) {
                detectTapGestures(onTap = { point ->
                    val hit = hits.find(point.y)
                    if (canSeek && hit >= 0) connection.seekTo((lines[hit].time - offset).coerceAtLeast(0))
                    else onToggleControls()
                }, onLongPress = { point ->
                    if (!locked) hits.find(point.y).takeIf { it >= 0 }?.let { onSave(lines[it].text) }
                }, onDoubleTap = { point ->
                    if (!locked) hits.find(point.y).takeIf { it >= 0 }?.let { onCorrect(lines[it].text) }
                })
            }.drawWithCache {
                val paint = deeplistApplePaint(requestedSize.coerceIn(24f, 96f).sp.toPx())
                val layouts = displayLines.mapIndexed { i, line ->
                    val text = deeplistSpacedText(line.text, wordSpacing)
                    StaticLayout.Builder.obtain(text, 0, text.length, paint,
                        (size.width * if (duet) .86f else 1f).toInt().coerceAtLeast(1))
                        .setAlignment(if (duet && singers[i] == 1) Layout.Alignment.ALIGN_OPPOSITE
                            else if (duet && singers[i] == 2) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false).setLineSpacing(0f, 1.12f).build()
                }
                val starts = FloatArray(layouts.size + 1)
                layouts.forEachIndexed { i, layout -> starts[i + 1] = starts[i] + layout.height + 30.dp.toPx() * lineSpacing.coerceIn(.45f, 1.35f) }
                val centers = FloatArray(layouts.size) { starts[it] + layouts[it].height * .5f }
                hits.centers = centers
                hits.ready = false
                val nodes = if (Build.VERSION.SDK_INT >= 31 && blur > 0 && !economy) layouts.mapIndexed { i, layout ->
                    RenderNode("lyric-$i").apply {
                        setPosition(0, 0, layout.width, layout.height)
                        val canvas = beginRecording(layout.width, layout.height)
                        layout.draw(canvas); endRecording()
                        val radius = (blur * 5.dp.toPx()).coerceAtLeast(.1f)
                        setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
                    }
                } else emptyList()
                val timed = displayLines.mapIndexed { i, line -> deeplistMotionWords(line, displayLines.getOrNull(i + 1)?.time) }
                val bounds = timed.mapIndexed { i, row -> row.map { deeplistWordBounds(layouts[i], it) } }
                val emphasis = timed.map { FloatArray(it.size) }
                val excluded = Path()
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = verciColors[colorIndex.coerceIn(0, 4)] }
                val iconText = icons.map { it.joinToString("") }
                val moods = lines.map { deeplistLyricMood(it.text) }
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.textSize = 30.dp.toPx()
                    if (iconPack > 0) colorFilter = android.graphics.PorterDuffColorFilter(if (iconPack == 1) android.graphics.Color.WHITE else verciColors[colorIndex.coerceIn(0, 4)], android.graphics.PorterDuff.Mode.SRC_IN)
                }
                onDrawBehind {
                    if (layouts.isEmpty()) return@onDrawBehind
                    val visualScroll = if (hits.ready) scroll else centers.getOrElse(active.coerceAtLeast(0)) { 0f }
                    val index = deeplistNearestCenter(centers, visualScroll)
                    val canvas = drawContext.canvas.nativeCanvas
                    hits.count = 0
                    for (i in (index - 3).coerceAtLeast(0)..(index + 4).coerceAtMost(layouts.lastIndex)) {
                        val layout = layouts[i]
                        val y = size.height * .40f - layout.height * .5f + centers[i] - visualScroll
                        if (y + layout.height < -20.dp.toPx() || y > size.height + 20.dp.toPx()) continue
                        val neighbour = if (visualScroll >= centers[i]) (i + 1).coerceAtMost(centers.lastIndex) else (i - 1).coerceAtLeast(0)
                        val span = abs(centers[neighbour] - centers[i]).coerceAtLeast(layout.height + 1f)
                        val focus = deeplistLineFocus((centers[i] - visualScroll) / span)
                        val scale = .96f + .04f * focus + (if (chorusBoost && lines[i].time in chorusTimes) .02f * focus else 0f) * intensity
                        val lineAlpha = (.27f + .73f * focus).coerceIn(0f, 1f)
                        val singer = singers[i]
                        hits.add(i, y, y + layout.height * scale)
                        canvas.save()
                        canvas.translate(if (duet && singer == 1) size.width * .14f else if (duet && singer == 2) size.width * .07f else 0f, y)
                        canvas.scale(scale, scale, 0f, layout.height * .5f)
                        excluded.rewind()
                        for (j in timed[i].indices) {
                            val word = timed[i][j]
                            val amount = if (intensity > 0f) deeplistWordEmphasis(time, word.start, word.end) else 0f
                            emphasis[i][j] = amount
                            if (amount > 0f) for (box in bounds[i][j]) excluded.addRect(box, Path.Direction.CW)
                        }
                        canvas.save()
                        canvas.clipOutPath(excluded)
                        val blurMix = if (nodes.isNotEmpty() && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) (1f - focus) * blur.coerceIn(0f, 1f) else 0f
                        paint.color = android.graphics.Color.WHITE
                        paint.alpha = (255 * lineAlpha * (1f - blurMix)).toInt()
                        layout.draw(canvas)
                        if (blurMix > 0f && Build.VERSION.SDK_INT >= 31) {
                            nodes[i].setAlpha(lineAlpha * blurMix)
                            canvas.drawRenderNode(nodes[i])
                        }
                        canvas.restore()
                        for (j in timed[i].indices) {
                            val amount = emphasis[i][j]
                            if (amount <= 0f) continue
                            val extra = if (economy) 0f else pulse.coerceIn(0f, 1f) * .025f
                            val zoom = 1f + amount * (wordZoom.coerceIn(0f, .35f) + extra) * intensity
                            val lift = amount * (7.dp.toPx() + extra * 90.dp.toPx()) * intensity
                            for (box in bounds[i][j]) {
                                canvas.save()
                                canvas.translate(0f, -lift)
                                canvas.scale(zoom, zoom, box.centerX(), box.centerY())
                                if (wordGlow && !economy) {
                                    for (layer in 3 downTo 1) {
                                        val spread = layer * 2.dp.toPx()
                                        glowPaint.alpha = (amount * lineAlpha * (12 - layer * 2)).toInt()
                                        canvas.drawRoundRect(box.left - spread, box.top - spread, box.right + spread, box.bottom + spread, 10.dp.toPx(), 10.dp.toPx(), glowPaint)
                                    }
                                }
                                canvas.clipRect(box)
                                paint.alpha = (255 * lineAlpha).toInt()
                                layout.draw(canvas)
                                canvas.restore()
                            }
                        }
                        canvas.restore()
                    }
                    val wordIndex = verciWordIndex(words, time)
                    val currentWord = words.getOrNull(wordIndex)
                    if (currentWord != null && time in currentWord.start..currentWord.end) {
                        val icon = iconText.getOrElse(wordIndex) { "" }
                        if (icon.isNotEmpty()) {
                            val mood = moods.getOrElse(active) { 0 }
                            iconPaint.alpha = if (mood < 0) 150 else 255
                            val progress = ((time - currentWord.start).toFloat() / (currentWord.end - currentWord.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                            val bounce = if (mood > 0 && !economy) sin(progress * Math.PI).toFloat() * 8.dp.toPx() * intensity else 0f
                            canvas.drawText(icon, 0f, size.height - 8.dp.toPx() - bounce, iconPaint)
                        }
                    }
                }
            }) {}
    }
}

private class AppleLyricHits {
    var centers = FloatArray(0)
    var ready = false
    private val indices = IntArray(8)
    private val top = FloatArray(8)
    private val bottom = FloatArray(8)
    var count = 0
    fun add(index: Int, y1: Float, y2: Float) {
        if (count >= indices.size) return
        indices[count] = index; top[count] = y1; bottom[count] = y2; count++
    }
    fun find(y: Float): Int {
        for (i in 0 until count) if (y >= top[i] && y <= bottom[i]) return indices[i]
        return -1
    }
}
