package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.*
import com.metrolist.music.lyrics.*
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage

@Composable
internal fun rememberDeeplistJourney(track: String?, base: VerciStyle): VerciStyle {
    val raw by rememberPreference(DeeplistJourneysKey, "{}")
    val cues = remember(raw, track) { deeplistCues(raw, track.orEmpty()) }
    val connection = LocalPlayerConnection.current
    var selected by remember(track, base) { mutableStateOf(base) }
    LaunchedEffect(track, cues, base) {
        while (true) {
            val now = connection?.player?.currentPosition ?: 0L
            selected = cues.lastOrNull { it.time <= now }?.style ?: base
            delay(80)
        }
    }
    return selected
}

@Composable
internal fun DeeplistStageEffects(source: String, slider: () -> Long?, playing: Boolean, style: VerciStyle) {
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val chorus by rememberPreference(DeeplistChorusBoostKey, true)
    val instrumental by rememberPreference(DeeplistInstrumentalKey, true)
    val duet by rememberPreference(DeeplistDuetEnabledKey, false)
    val tags by rememberPreference(DeeplistDuetTagsKey, "{}")
    val marks by rememberPreference(DeeplistChorusMarksKey, "{}")
    val lines = remember(source) { LyricsUtils.parseLyrics(source).filter { !it.isBackground && it.text.isNotBlank() }.sortedBy { it.time } }
    val gaps = remember(source) { buildVerciGaps(LyricsUtils.parseLyrics(source)) }
    val lineTimes = remember(lines) { lines.map { it.time } }
    val gapTimes = remember(gaps) { gaps.map { it.start } }
    val singers = remember(lines, tags, metadata?.id) { IntArray(lines.size) { deeplistSinger(tags, metadata?.id.orEmpty(), lines[it].time) } }
    val choruses = remember(lines, marks, metadata?.id) { deeplistChorusLines(lines, deeplistChorusMarks(marks, metadata?.id.orEmpty())) }
    var now by remember(source) { mutableLongStateOf(0) }
    val latestSlider by rememberUpdatedState(slider)
    val offset by rememberUpdatedState(song?.song?.lyricsOffset ?: 0)
    LaunchedEffect(source, playing) { while (true) { now = (latestSlider() ?: connection.player.currentPosition) + offset; delay(if (playing) 50 else 150) } }
    val lineIndex = wordstormLineIndex(lineTimes, now)
    val line = lines.getOrNull(lineIndex)
    val hot = chorus && line?.time in choruses
    val amount by animateFloatAsState(if (hot && playing) .16f else 0f, tween(700), label = "chorus light")
    val gapIndex = wordstormLineIndex(gapTimes, now)
    val gap = instrumental && (gaps.getOrNull(gapIndex)?.let { now <= it.end } == true || lines.firstOrNull()?.let { now < it.time && it.time > 4000 } == true)
    val opacity by animateFloatAsState(if (gap) 1f else 0f, tween(700), label = "instrumental scene")
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawWithCache {
            val light = Brush.radialGradient(listOf(Color(0xFFD1B0FF), Color.Transparent), radius = size.maxDimension * .6f)
            onDrawBehind { drawRect(light, alpha = amount) }
        })
        if (opacity > .01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = opacity * .92f)), contentAlignment = Alignment.Center) {
            AsyncImage(metadata?.thumbnailUrl, null, modifier = Modifier.size(200.dp).graphicsLayer { alpha = opacity; scaleX = .94f + opacity * .06f; scaleY = scaleX })
        }
        if (duet && style != VerciStyle.APPLE && line != null) {
            val singer = singers[lineIndex]
            val label = when (singer) { 1 -> "B"; 2 -> "A + B"; else -> "A" }
            Text(label, color = if (singer == 1) Color(0xFFFFADD7) else Color(0xFFC4B4FF), modifier = Modifier.align(if (singer == 1) Alignment.TopEnd else Alignment.TopStart).safeDrawingPadding().padding(24.dp))
        }
    }
}
