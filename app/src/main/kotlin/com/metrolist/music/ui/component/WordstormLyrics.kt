package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedTextField
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import com.metrolist.music.constants.DeeplistWebsiteKey
import com.metrolist.music.constants.WordstormAccentKey
import com.metrolist.music.constants.WordstormCoverKey
import com.metrolist.music.constants.WordstormFontSizeKey
import com.metrolist.music.constants.WordstormMotionKey
import com.metrolist.music.constants.WordstormPresetKey
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.lyricsTextLooksSynced
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
    val position = remember(metadata?.id) { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var website by rememberPreference(DeeplistWebsiteKey, "")
    var websiteDraft by remember { mutableStateOf(website) }
    var websiteError by remember { mutableStateOf(false) }
    val offset: Long = if (song?.song?.id == metadata?.id) song?.song?.lyricsOffset?.toLong() ?: 0L else 0L
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
    LaunchedEffect(metadata?.id, playing, motion, preset, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                position.longValue = latestSlider() ?: connection.player.currentPosition
                delay(if (playing && motion && preset != 2) 33L else 120L)
            }
        }
    }
    val times = remember(entries) { entries.map { it.time } }
    val activeIndex by remember(times, position, offset) {
        derivedStateOf { wordstormLineIndex(times, position.longValue + offset) }
    }
    val active = entries.getOrNull(activeIndex)
    val pace = remember(active) {
        active?.words?.takeIf { it.isNotEmpty() }?.let {
            val duration = (it.last().endTime - it.first().startTime) / it.size
            if (duration > 0) (0.65 / duration).toFloat().coerceIn(0.4f, 2.3f) else 0.65f
        } ?: 0.65f
    }
    Box(modifier.fillMaxSize().background(Color(0xFF08070D))) {
        DeeplistBackground(accent, playing && motion && preset != 2, pace)
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
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
                        DeeplistLine(
                            entry = active, position = position, offset = offset,
                            fontSize = fontSize.coerceIn(26f, 48f), accent = accent,
                            modifier = Modifier.padding(horizontal = 6.dp).clickable(enabled = canSeek) {
                                connection.seekTo((active.time - offset).coerceAtLeast(0L))
                            },
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
        TextButton(onClick = { websiteDraft = website; websiteError = false; settings = true }, modifier = Modifier.align(Alignment.TopEnd)) {
            Text(stringResource(R.string.wordstorm_settings), color = accent)
        }
        if (cover) {
            AsyncImage(model = metadata?.thumbnailUrl, contentDescription = stringResource(R.string.wordstorm_cover), modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(58.dp))
        }
        Text(metadata?.title.orEmpty(), color = accent.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp))
        }
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
                    TextButton(onClick = { settings = false; onOriginal() }) { Text(stringResource(R.string.wordstorm_original)) }
                    Text(stringResource(R.string.deeplist_inside_deep))
                    OutlinedTextField(
                        value = websiteDraft,
                        onValueChange = { websiteDraft = it; websiteError = false },
                        label = { Text(stringResource(R.string.deeplist_website_url)) },
                        singleLine = true, isError = websiteError,
                    )
                    if (websiteError) Text(stringResource(R.string.deeplist_invalid_url))
                    Row {
                        TextButton(onClick = {
                            val url = deeplistWebsiteUrl(websiteDraft)
                            if (url == null) websiteError = true else {
                                website = url
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    .onFailure { websiteError = true }
                            }
                        }) { Text(stringResource(R.string.deeplist_open_website)) }
                        TextButton(onClick = {
                            val track = metadata ?: return@TextButton
                            val text = "${track.title} — ${track.artists.joinToString { it.name }}\nhttps://music.youtube.com/watch?v=${track.id}"
                            runCatching {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                                }, null))
                            }
                        }) { Text(stringResource(R.string.deeplist_share_song)) }
                    }
                    TextButton(onClick = { preset = 0; fontSize = 36f; motion = true; cover = false; accentIndex = 0 }) { Text(stringResource(R.string.wordstorm_reset)) }
                }
            },
            confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.wordstorm_done)) } },
        )
    }
}


@Composable
private fun DeeplistBackground(accent: Color, animate: Boolean, pace: Float) {
    val phase = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(animate, pace, lifecycleOwner) {
        if (animate) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                phase.floatValue = (phase.floatValue + 0.004f * pace) % 1f
                delay(33L)
            }
        }
    }
    Box(Modifier.fillMaxSize().drawWithCache {
        val aura = Brush.radialGradient(listOf(accent.copy(alpha = 0.08f), Color.Transparent), Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.65f)
        onDrawBehind {
            drawRect(aura)
            drawOval(accent.copy(alpha = 0.08f), Offset(-size.width * 0.1f, size.height * 0.28f), Size(size.width * 1.2f, size.height * 0.38f), style = Stroke(1.dp.toPx()))
            drawOval(accent.copy(alpha = 0.05f), Offset(-size.width * 0.2f, size.height * 0.22f), Size(size.width * 1.4f, size.height * 0.52f), style = Stroke(1.dp.toPx()))
            repeat(24) { index ->
                drawCircle(accent.copy(alpha = 0.17f), 1.dp.toPx(), Offset(((index * 37) % 100) * size.width / 100, ((index * 19) % 100) * size.height / 100))
            }
        }
    })
    if (animate) Canvas(Modifier.fillMaxSize()) {
        val x = phase.floatValue * size.width
        val y = (1f - phase.floatValue) * size.height
        val stroke = 2.dp.toPx()
        drawLine(accent.copy(alpha = 0.6f), Offset(x, 0f), Offset((x + size.width * 0.18f).coerceAtMost(size.width), 0f), stroke)
        drawLine(accent.copy(alpha = 0.45f), Offset(size.width - x, size.height), Offset((size.width - x - size.width * 0.18f).coerceAtLeast(0f), size.height), stroke)
        drawLine(accent.copy(alpha = 0.45f), Offset(0f, y), Offset(0f, (y - size.height * 0.18f).coerceAtLeast(0f)), stroke)
        drawLine(accent.copy(alpha = 0.6f), Offset(size.width, y), Offset(size.width, (y - size.height * 0.18f).coerceAtLeast(0f)), stroke)
    }
}
