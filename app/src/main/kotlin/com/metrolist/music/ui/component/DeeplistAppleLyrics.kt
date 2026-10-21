package com.metrolist.music.ui.component

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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
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
import kotlin.math.exp
import kotlin.math.sin

@Composable
internal fun DeeplistAppleLyrics(source: String, slider: () -> Long?, locked: Boolean, onSave: (String) -> Unit, onCorrect: (String) -> Unit, onToggleControls: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    val textSize by rememberPreference(DeeplistAppleSizeKey, 32f)
    val blur by rememberPreference(DeeplistSoftBlurKey, .35f)
    val intensity by rememberPreference(DeeplistAnimationKey, 1f)
    val artwork by rememberPreference(DeeplistArtworkBackgroundKey, true)
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
    val latestTimes by rememberUpdatedState(times)
    val active by remember(times) { derivedStateOf { wordstormLineIndex(times, time) } }
    val activeText by remember(displayLines) { derivedStateOf { displayLines.getOrNull(active)?.text.orEmpty() } }
    val manager = LocalListenTogetherManager.current
    val tapSeek by rememberPreference(DeeplistTapSeekKey, false)
    val recording by rememberPreference(VerciRecordingKey, false)
    val canSeek = tapSeek && !recording && !locked && (manager?.isInRoom != true || manager.isHost)
    val hitLines = remember { mutableListOf<Triple<Int, Float, Float>>() }
    LaunchedEffect(connection, source, playing, owner, economy, intensity) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val clock = VerciPlaybackClock()
            // Read the current timeline after asynchronous parsing, never the initial empty list.
            val motion = AppleLyricsMotion(scroll) { latestTimes }
            while (isActive) {
            withFrameNanos { frame ->
                val preview = latestSlider()
                time = (preview ?: if (playing) clock.position(connection.player.currentPosition, frame, connection.player.playbackParameters.speed) else connection.player.currentPosition) + offset
                if (preview != null) clock.reset()
                scroll = motion.advance(time, frame, preview != null, intensity)
            }
            if (!playing) delay(80)
            }
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Canvas(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 24.dp)
            .semantics { contentDescription = activeText }
            .pointerInput(displayLines, canSeek, offset) {
                detectTapGestures(onTap = { point ->
                    val hit = hitLines.firstOrNull { point.y in it.second..it.third }
                    if (canSeek && hit != null) connection.seekTo((lines[hit.first].time - offset).coerceAtLeast(0))
                    else onToggleControls()
                }, onLongPress = { point ->
                    if (!locked) hitLines.firstOrNull { point.y in it.second..it.third }?.let { onSave(lines[it.first].text) }
                }, onDoubleTap = { point ->
                    if (!locked) hitLines.firstOrNull { point.y in it.second..it.third }?.let { onCorrect(lines[it.first].text) }
                })
            }.drawWithCache {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    this.textSize = textSize.coerceIn(24f, 64f).sp.toPx()
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                val layouts = displayLines.mapIndexed { i, line -> StaticLayout.Builder.obtain(line.text, 0, line.text.length, paint, (size.width * if (duet) .86f else 1f).toInt().coerceAtLeast(1))
                    .setAlignment(if (duet && deeplistSinger(duetTags, metadata?.id.orEmpty(), lines[i].time) == 1) Layout.Alignment.ALIGN_OPPOSITE else if (duet && deeplistSinger(duetTags, metadata?.id.orEmpty(), lines[i].time) == 2) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0f, 1.12f).build() }
                val starts = FloatArray(layouts.size + 1)
                layouts.forEachIndexed { i, layout -> starts[i + 1] = starts[i] + layout.height + 30.dp.toPx() }
                val nodes = if (Build.VERSION.SDK_INT >= 31 && blur > 0 && !economy) layouts.mapIndexed { i, layout ->
                    RenderNode("lyric-$i").apply {
                        setPosition(-12, -12, layout.width + 12, layout.height + 12)
                        val canvas = beginRecording(layout.width + 24, layout.height + 24)
                        canvas.translate(12f, 12f); layout.draw(canvas); endRecording()
                        setRenderEffect(RenderEffect.createBlurEffect((blur * 5.dp.toPx()).coerceAtLeast(.1f), (blur * 5.dp.toPx()).coerceAtLeast(.1f), Shader.TileMode.DECAL))
                    }
                } else emptyList()
                val timed = lines.mapIndexed { i, line -> if (line.words.isNullOrEmpty() || roman) emptyList() else deeplistMotionWords(line, lines.getOrNull(i + 1)?.time) }
                val moods = lines.map { deeplistLyricMood(it.text) }
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.textSize = 30.dp.toPx()
                    if (iconPack > 0) colorFilter = android.graphics.PorterDuffColorFilter(if (iconPack == 1) android.graphics.Color.WHITE else verciColors[colorIndex.coerceIn(0, 4)], android.graphics.PorterDuff.Mode.SRC_IN)
                }
                onDrawBehind {
                    if (layouts.isEmpty()) return@onDrawBehind
                    val position = scroll.coerceIn(0f, layouts.lastIndex.toFloat())
                    val index = position.toInt()
                    val fraction = position - index
                    val base = starts[index] + (starts[minOf(index + 1, layouts.lastIndex)] - starts[index]) * fraction
                    val current = active
                    val rowHeight = layouts[index].height + (layouts[minOf(index + 1, layouts.lastIndex)].height - layouts[index].height) * fraction
                    val canvas = drawContext.canvas.nativeCanvas
                    hitLines.clear()
                    for (i in (index - 3).coerceAtLeast(0)..(index + 4).coerceAtMost(layouts.lastIndex)) {
                        val layout = layouts[i]
                        val y = size.height * .40f - rowHeight * .5f + starts[i] - base
                        if (y + layout.height < 0 || y > size.height) continue
                        hitLines.add(Triple(i, y, y + layout.height))
                        val focused = i == current
                        val scale = if (focused && chorusBoost && lines[i].time in chorusTimes && intensity > 0f) 1.02f else if (focused) 1f else .96f
                        val singer = if (duet) deeplistSinger(duetTags, metadata?.id.orEmpty(), lines[i].time) else 0
                        canvas.save(); canvas.translate(if (duet && singer == 1) size.width * .14f else if (duet && singer == 2) size.width * .07f else 0f, y); canvas.scale(scale, scale)
                        paint.alpha = if (focused) 255 else (135 - abs(i - position) * 18).toInt().coerceIn(55, 135)
                        if (!focused && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated && nodes.isNotEmpty()) {
                            nodes[i].setAlpha(paint.alpha / 255f)
                            canvas.drawRenderNode(nodes[i])
                        } else {
                            paint.color = android.graphics.Color.WHITE
                            paint.alpha = if (focused && timed[i].isNotEmpty()) 110 else paint.alpha
                            layout.draw(canvas)
                        }
                        if (focused && timed[i].isNotEmpty()) {
                            timed[i].forEach { word ->
                                val progress = ((time - word.start).toFloat() / (word.end - word.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                                if (progress > 0) {
                                    val startLine = layout.getLineForOffset(word.startChar)
                                    val endLine = layout.getLineForOffset((word.endChar - 1).coerceAtLeast(word.startChar))
                                    for (line in startLine..endLine) {
                                        val start = maxOf(word.startChar, layout.getLineStart(line))
                                        val end = minOf(word.endChar, layout.getLineEnd(line))
                                        val left = layout.getPrimaryHorizontal(start)
                                        val right = if (end >= layout.getLineEnd(line)) layout.getLineRight(line) else layout.getPrimaryHorizontal(end)
                                        canvas.save()
                                        canvas.clipRect(minOf(left, right), layout.getLineTop(line).toFloat(), minOf(left, right) + abs(right - left) * progress, layout.getLineBottom(line).toFloat())
                                        paint.alpha = 255; layout.draw(canvas); canvas.restore()
                                    }
                                }
                            }
                        }
                        canvas.restore()
                    }
                    val wordIndex = verciWordIndex(words, time)
                    val currentWord = words.getOrNull(wordIndex)
                    if (currentWord != null && time in currentWord.start..currentWord.end) {
                        val icon = icons.getOrNull(wordIndex).orEmpty().joinToString("")
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
