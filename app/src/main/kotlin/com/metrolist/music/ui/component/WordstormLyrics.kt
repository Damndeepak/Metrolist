package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.WordstormAccentKey
import com.metrolist.music.constants.WordstormCoverKey
import com.metrolist.music.constants.WordstormFontSizeKey
import com.metrolist.music.constants.WordstormMotionKey
import com.metrolist.music.constants.WordstormPresetKey
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.ui.screens.settings.LyricsPosition
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
internal fun WordstormLyrics(
    sliderPositionProvider: () -> Long?,
    showLyrics: Boolean,
    onOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!showLyrics) return
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val entity by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    val latestSlider by rememberUpdatedState(sliderPositionProvider)
    var preset by rememberPreference(WordstormPresetKey, 0)
    var fontSize by rememberPreference(WordstormFontSizeKey, 36f)
    var motion by rememberPreference(WordstormMotionKey, true)
    var cover by rememberPreference(WordstormCoverKey, false)
    var accentIndex by rememberPreference(WordstormAccentKey, 0)
    var settings by remember { mutableStateOf(false) }
    val palette = listOf(Color(0xFFC2A1FF), Color(0xFF7EDAE3), Color(0xFFFFB296))
    val accent = palette[accentIndex.coerceIn(0, 2)]
    val validText = wordstormLyricsForTrack(metadata?.id, entity?.id, entity?.lyrics)
    val synced = remember(validText) { lyricsTextLooksSynced(validText) }
    var entries by remember(metadata?.id) { mutableStateOf<List<LyricsEntry>>(emptyList()) }
    var position by remember(metadata?.id) { mutableLongStateOf(0L) }
    var phase by remember(metadata?.id) { mutableFloatStateOf(0f) }
    val offset = if (song?.song?.id == metadata?.id) song?.song?.lyricsOffset ?: 0L else 0L
    val manager = LocalListenTogetherManager.current
    val canSeek = manager?.isInRoom != true || manager.isHost

    LaunchedEffect(metadata?.id, validText) {
        entries = emptyList()
        if (synced && validText != null) {
            entries = withContext(Dispatchers.Default) {
                LyricsUtils.parseLyrics(validText).filter { it.text.isNotBlank() && !it.isBackground }.sortedBy { it.time }
            }
        }
    }
    LaunchedEffect(metadata?.id, playing, motion, preset) {
        while (isActive) {
            position = latestSlider() ?: connection.player.currentPosition
            if (playing && motion && preset != 2) {
                val entry = entries.lastOrNull { it.time <= position + offset }
                val wordDuration = entry?.words?.takeIf { it.isNotEmpty() }?.let { (it.last().endTime - it.first().startTime) / it.size }
                val speed = wordDuration?.takeIf { it > 0.0 }?.let { (0.65 / it).toFloat().coerceIn(0.4f, 2.3f) } ?: 0.65f
                phase = (phase + 0.004f * speed) % 1f
            }
            delay(if (playing) 33L else 150L)
        }
    }
    val times = remember(entries) { entries.map { it.time } }
    val activeIndex = wordstormLineIndex(times, position + offset)
    val active = entries.getOrNull(activeIndex)
    val entrance by animateFloatAsState(
        targetValue = if (active != null) 1f else 0f,
        animationSpec = tween(if (motion && preset != 2) 450 else 0),
        label = "wordstormEntrance",
    )
    Box(modifier.fillMaxSize().background(Color(0xFF08070D))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.08f), Color.Transparent), center, size.minDimension * 0.65f))
            drawOval(accent.copy(alpha = 0.08f), Offset(-size.width * 0.1f, size.height * 0.28f), Size(size.width * 1.2f, size.height * 0.38f), style = Stroke(1.dp.toPx()))
            drawOval(accent.copy(alpha = 0.05f), Offset(-size.width * 0.2f, size.height * 0.22f), Size(size.width * 1.4f, size.height * 0.52f), style = Stroke(1.dp.toPx()))
            repeat(24) { index ->
                drawCircle(accent.copy(alpha = 0.17f), 1.dp.toPx(), Offset(((index * 37) % 100) * size.width / 100, ((index * 19) % 100) * size.height / 100))
            }
            if (motion && preset != 2) {
                val x = phase * size.width
                val y = (1f - phase) * size.height
                val stroke = 2.dp.toPx()
                drawLine(accent.copy(alpha = 0.6f), Offset(x, 0f), Offset((x + size.width * 0.18f).coerceAtMost(size.width), 0f), stroke)
                drawLine(accent.copy(alpha = 0.45f), Offset(size.width - x, size.height), Offset((size.width - x - size.width * 0.18f).coerceAtLeast(0f), size.height), stroke)
                drawLine(accent.copy(alpha = 0.45f), Offset(0f, y), Offset(0f, (y - size.height * 0.18f).coerceAtLeast(0f)), stroke)
                drawLine(accent.copy(alpha = 0.6f), Offset(size.width, y), Offset(size.width, (y - size.height * 0.18f).coerceAtLeast(0f)), stroke)
            }
        }
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(vertical = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (preset == 1) {
                entries.getOrNull(activeIndex - 1)?.let {
                    Text(it.text, color = accent.copy(alpha = 0.25f), fontSize = fontSize.coerceIn(26f, 48f).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            if (active != null) {
                key(metadata?.id, active.time, active.text) {
                    if (preset == 2 || !motion) {
                        Text(active.text, color = Color.White, fontSize = fontSize.coerceIn(26f, 48f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp))
                    } else {
                        LyricsLine(
                            index = activeIndex,
                            item = active,
                            isSynced = synced,
                            isActiveLine = true,
                            bgVisible = false,
                            isSelected = false,
                            isSelectionModeActive = false,
                            currentPositionState = position,
                            lyricsOffset = offset,
                            playerConnection = connection,
                            lyricsTextSize = fontSize.coerceIn(26f, 48f),
                            lyricsLineSpacing = 1.25f,
                            expressiveAccent = accent,
                            lyricsTextPosition = LyricsPosition.CENTER,
                            respectAgentPositioning = false,
                            isAutoScrollEnabled = true,
                            displayedCurrentLineIndex = activeIndex,
                            romanizeAsMain = false,
                            enabledLanguages = emptyList(),
                            romanizeLyrics = false,
                            onSizeChanged = {},
                            onClick = { if (canSeek) connection.seekTo((active.time - offset).coerceAtLeast(0L)) },
                            onLongClick = { settings = true },
                            modifier = Modifier.graphicsLayer { translationY = (1f - entrance) * 16.dp.toPx() },
                        )
                    }
                }
            } else if (!synced && !validText.isNullOrBlank() && validText != "LYRICS_NOT_FOUND") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                    Text(validText, color = Color.White, fontSize = fontSize.coerceIn(26f, 48f).sp, textAlign = TextAlign.Center)
                }
            } else {
                Text(metadata?.title.orEmpty(), color = accent.copy(alpha = 0.55f), fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            }
            if (preset == 1) {
                entries.getOrNull(activeIndex + 1)?.let {
                    Text(it.text, color = accent.copy(alpha = 0.35f), fontSize = fontSize.coerceIn(26f, 48f).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { settings = true }) { Text(stringResource(R.string.wordstorm_settings), color = accent) }
            TextButton(onClick = onOriginal) { Text(stringResource(R.string.wordstorm_original), color = accent) }
        }
        if (cover) {
            AsyncImage(model = metadata?.thumbnailUrl, contentDescription = stringResource(R.string.wordstorm_cover), modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(58.dp))
        }
        Text(metadata?.title.orEmpty(), color = accent.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp))
    }
    if (settings) {
        val modeNames = listOf(R.string.wordstorm_cinematic, R.string.wordstorm_floating, R.string.wordstorm_karaoke)
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(stringResource(R.string.wordstorm_settings)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    modeNames.forEachIndexed { index, label ->
                        FilterChip(selected = preset == index, onClick = { preset = index }, label = { Text(stringResource(label)) })
                    }
                    Text(stringResource(R.string.wordstorm_size))
                    Slider(value = fontSize.coerceIn(26f, 48f), onValueChange = { fontSize = it }, valueRange = 26f..48f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        palette.forEachIndexed { index, colour ->
                            val colourLabel = stringResource(R.string.wordstorm_accent, index + 1)
                            TextButton(onClick = { accentIndex = index }, modifier = Modifier.semantics { contentDescription = colourLabel }) { Text("●", color = colour, fontSize = 24.sp) }
                        }
                    }
                    TextButton(onClick = { motion = !motion }) { Text(stringResource(if (motion) R.string.wordstorm_motion_on else R.string.wordstorm_motion_off)) }
                    TextButton(onClick = { cover = !cover }) { Text(stringResource(if (cover) R.string.wordstorm_hide_cover else R.string.wordstorm_show_cover)) }
                    TextButton(onClick = { preset = 0; fontSize = 36f; motion = true; cover = false; accentIndex = 0 }) { Text(stringResource(R.string.wordstorm_reset)) }
                }
            },
            confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.wordstorm_done)) } },
        )
    }
}
