package com.metrolist.music.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import com.metrolist.music.constants.DeeplistNeonKey
import com.metrolist.music.constants.DeeplistSpeedKey
import com.metrolist.music.constants.DeeplistContextKey
import com.metrolist.music.constants.DeeplistPerformanceKey
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.metrolist.music.constants.DeeplistSyncEnabledKey
import com.metrolist.music.constants.DeeplistTokenKey
import com.metrolist.music.deeplist.DeeplistBridgeStatus
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    immersive: Boolean = false,
    onFullscreen: () -> Unit = {},
    onExitFullscreen: () -> Unit = {},
) {
    if (!showLyrics) return
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val entity by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    val latestSlider by rememberUpdatedState(sliderPositionProvider)
    var preset by rememberPreference(WordstormPresetKey, 0)
    var fontSize by rememberPreference(WordstormFontSizeKey, 44f)
    var motion by rememberPreference(WordstormMotionKey, true)
    var cover by rememberPreference(WordstormCoverKey, false)
    var accentIndex by rememberPreference(WordstormAccentKey, 0)
    var settings by remember { mutableStateOf(false) }
    var controls by remember(immersive) { mutableStateOf(!immersive) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var neon by rememberPreference(DeeplistNeonKey, 0.65f)
    var speed by rememberPreference(DeeplistSpeedKey, 1f)
    var showContext by rememberPreference(DeeplistContextKey, false)
    var performance by rememberPreference(DeeplistPerformanceKey, false)
    LaunchedEffect(controls, immersive, settings) { if (immersive && controls && !settings) { delay(4000L); controls = false } }
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
    var syncEnabled by rememberPreference(DeeplistSyncEnabledKey, false)
    var pairingToken by rememberPreference(DeeplistTokenKey, "")
    var tokenDraft by remember { mutableStateOf(pairingToken) }
    val syncStatus by DeeplistBridgeStatus.state.collectAsStateWithLifecycle()
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
    LaunchedEffect(metadata?.id, playing, motion, preset, lifecycleOwner, performance) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                position.longValue = latestSlider() ?: connection.player.currentPosition
                delay(if (playing && motion && preset != 2 && !performance) 32L else 100L)
            }
        }
    }
    val times = remember(entries) { entries.map { it.time } }
    val activeIndex by remember(times, position, offset) {
        derivedStateOf { wordstormLineIndex(times, position.longValue + offset) }
    }
    val active = entries.getOrNull(activeIndex)
    val rainWords = remember(entries) { entries.take(12).flatMap { it.text.split(Regex("\\s+")) } }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF05040C)).clickable(enabled = immersive, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { controls = !controls }) {
        val lineHeightLimit = maxHeight * (if (showContext && (!immersive || controls)) 0.4f else 0.7f)
        if (immersive) DeeplistEdgeLights(playing)
        if (preset == 1 && motion && !performance) DeeplistWordRain(rainWords, playing, accent)
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(vertical = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showContext && (!immersive || controls)) {
                entries.getOrNull(activeIndex - 1)?.let {
                    Text(it.text, color = accent.copy(alpha = 0.25f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = fontSize.coerceIn(30f, 64f).sp, lineHeight = (fontSize.coerceIn(30f, 64f) * 1.25f).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            if (active != null) {
                key(metadata?.id, active.time, active.text, preset) {
                    DeeplistLine(
                        entry = active, position = position, offset = offset,
                        fontSize = fontSize.coerceIn(30f, 64f), accent = accent,
                        wordstorm = preset == 1, playing = playing && motion && preset != 2,
                        speed = speed, performance = performance || !motion || preset == 2,
                        heightBudget = lineHeightLimit, nextTime = entries.getOrNull(activeIndex + 1)?.time,
                        modifier = Modifier.padding(horizontal = 6.dp).clickable(enabled = canSeek && !immersive) {
                            connection.seekTo((active.time - offset).coerceAtLeast(0L))
                        },
                    )
                }
            } else if (!synced && !validText.isNullOrBlank() && validText != "LYRICS_NOT_FOUND") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                    Text(validText, color = Color.White, fontSize = fontSize.coerceIn(30f, 64f).sp, lineHeight = (fontSize.coerceIn(30f, 64f) * 1.25f).sp, textAlign = TextAlign.Center)
                }
            } else {
                Text(metadata?.title.orEmpty(), color = accent.copy(alpha = 0.55f), fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            }
            if (showContext && (!immersive || controls)) {
                entries.getOrNull(activeIndex + 1)?.let {
                    Text(it.text, color = accent.copy(alpha = 0.35f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = fontSize.coerceIn(30f, 64f).sp, lineHeight = (fontSize.coerceIn(30f, 64f) * 1.25f).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        if (!immersive || controls) {
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { if (immersive) onExitFullscreen() else onFullscreen() }) { Text(stringResource(if (immersive) R.string.deeplist_exit_fullscreen else R.string.deeplist_fullscreen), color = accent) }
        TextButton(onClick = { websiteDraft = website; tokenDraft = pairingToken; websiteError = false; settings = true }, modifier = Modifier) {
            Text(stringResource(R.string.wordstorm_settings), color = accent)
        }
        }
        if (cover) {
            AsyncImage(model = metadata?.thumbnailUrl, contentDescription = stringResource(R.string.wordstorm_cover), modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(58.dp))
        }
        if (immersive) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF171223)), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            IconButton(onClick = { connection.seekToPrevious() }, enabled = canSeek) { Icon(painterResource(R.drawable.skip_previous), stringResource(R.string.deeplist_previous), tint = accent) }
            IconButton(onClick = { if (playing) connection.player.pause() else connection.player.play() }, enabled = canSeek) { Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), stringResource(R.string.deeplist_play_pause), tint = Color.White) }
            IconButton(onClick = { connection.seekToNext() }, enabled = canSeek) { Icon(painterResource(R.drawable.skip_next), stringResource(R.string.deeplist_next), tint = accent) }
        }
        if (!immersive) Text(metadata?.title.orEmpty(), color = accent.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp))
        }
        }
    }
    if (settings) {
        val modeNames = listOf(R.string.wordstorm_cinematic, R.string.wordstorm_floating, R.string.wordstorm_karaoke)
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(stringResource(R.string.wordstorm_settings)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(R.string.deeplist_lyrics_tab, R.string.deeplist_appearance_tab, R.string.deeplist_connection_tab).forEachIndexed { i, title ->
                            FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(stringResource(title)) })
                        }
                    }
                    if (tab != 2) {
                        DeeplistPreview(fontSize, accent, preset, motion, speed, performance, neon)
                    }
                    if (tab == 0) {
                    modeNames.forEachIndexed { index, label ->
                        FilterChip(selected = preset == index, onClick = { preset = index; if (index != 2) motion = true }, label = { Text(stringResource(label)) })
                    }
                    Text(stringResource(R.string.wordstorm_size))
                    Slider(value = fontSize.coerceIn(30f, 64f), onValueChange = { fontSize = it }, valueRange = 30f..64f)
                    TextButton(onClick = { showContext = !showContext }) { Text(stringResource(if (showContext) R.string.deeplist_context_on else R.string.deeplist_context_off)) }
                    Text(stringResource(R.string.deeplist_speed))
                    Slider(value = speed.coerceIn(0.7f, 1.7f), onValueChange = { speed = it }, valueRange = 0.7f..1.7f)
                    }
                    if (tab == 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        palette.forEachIndexed { index, colour ->
                            val colourLabel = stringResource(R.string.wordstorm_accent, index + 1)
                            TextButton(onClick = { accentIndex = index }, modifier = Modifier.semantics { contentDescription = colourLabel }) { Text("●", color = colour, fontSize = 24.sp) }
                        }
                    }
                    TextButton(onClick = { motion = !motion }) { Text(stringResource(if (motion) R.string.wordstorm_motion_on else R.string.wordstorm_motion_off)) }
                    TextButton(onClick = { cover = !cover }) { Text(stringResource(if (cover) R.string.wordstorm_hide_cover else R.string.wordstorm_show_cover)) }
                    Text(stringResource(R.string.deeplist_neon))
                    Slider(value = neon.coerceIn(0f, 1f), onValueChange = { neon = it }, valueRange = 0f..1f)
                    TextButton(onClick = { performance = !performance }) { Text(stringResource(if (performance) R.string.deeplist_performance_on else R.string.deeplist_performance_off)) }
                    }
                    if (tab == 2) {
                    Text(stringResource(R.string.deeplist_inside_deep))
                    OutlinedTextField(
                        value = websiteDraft,
                        onValueChange = { websiteDraft = it; websiteError = false },
                        label = { Text(stringResource(R.string.deeplist_website_url)) },
                        singleLine = true, isError = websiteError,
                    )
                    OutlinedTextField(value = tokenDraft, onValueChange = { tokenDraft = it; websiteError = false },
                        label = { Text(stringResource(R.string.deeplist_pairing_token)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    Text(stringResource(R.string.deeplist_sharing_notice))
                    Text(stringResource(when (syncStatus) {
                        DeeplistBridgeStatus.State.OFF -> R.string.deeplist_sync_off
                        DeeplistBridgeStatus.State.SENDING -> R.string.deeplist_sync_sending
                        DeeplistBridgeStatus.State.LIVE -> R.string.deeplist_sync_live
                        DeeplistBridgeStatus.State.ERROR -> R.string.deeplist_sync_error
                    }))
                    TextButton(onClick = {
                        if (syncEnabled) syncEnabled = false else {
                            val url = deeplistWebsiteUrl(websiteDraft)
                            if (url == null || !tokenDraft.trim().matches(Regex("[a-f0-9]{64}"))) websiteError = true
                            else { website = url; pairingToken = tokenDraft.trim(); syncEnabled = true }
                        }
                    }) { Text(stringResource(if (syncEnabled) R.string.deeplist_stop_sync else R.string.deeplist_start_sync)) }
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
                    }
                    TextButton(onClick = { preset = 0; fontSize = 44f; motion = true; cover = false; accentIndex = 0; speed = 1f; showContext = false; neon = 0.65f; performance = false }) { Text(stringResource(R.string.wordstorm_reset)) }
                }
            },
            confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.wordstorm_done)) } },
        )
    }
}
