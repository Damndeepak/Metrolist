package com.metrolist.music.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Switch
import com.metrolist.music.LocalDatabase
import com.metrolist.music.constants.DeeplistBackgroundKey
import com.metrolist.music.constants.DeeplistDensityKey
import com.metrolist.music.constants.DeeplistRainSpeedKey
import com.metrolist.music.constants.DeeplistWordBrightnessKey
import com.metrolist.music.constants.DeeplistRingsKey
import com.metrolist.music.constants.DeeplistGlowKey
import com.metrolist.music.constants.DeeplistSparksKey
import com.metrolist.music.constants.DeeplistLiftKey
import com.metrolist.music.constants.DeeplistFlightKey
import com.metrolist.music.constants.DeeplistGesturesKey
import com.metrolist.music.constants.DeeplistAutoPerformanceKey
import com.metrolist.music.constants.DeeplistSavedStyleKey
import com.metrolist.music.constants.DeeplistSavedLyricsKey
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.withFrameNanos
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
import com.metrolist.music.constants.VerciLyricsKey
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
    useVerci: Boolean = true,
) {
    if (!showLyrics) return
    var verciEnabled by rememberPreference(VerciLyricsKey, false)
    if (useVerci && verciEnabled) {
        VerciLyrics(sliderPositionProvider, modifier, showLyrics, onOriginal, immersive, onFullscreen, onExitFullscreen)
        return
    }
    val studioItems = verciLyricsSettingsItems()
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val entity by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    var transportPreview by remember { mutableStateOf<Long?>(null) }
    val latestSlider by rememberUpdatedState<() -> Long?>({ transportPreview ?: sliderPositionProvider() })
    var preset by rememberPreference(WordstormPresetKey, 1)
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
    var backgroundMode by rememberPreference(DeeplistBackgroundKey, 1)
    var density by rememberPreference(DeeplistDensityKey, 28)
    var rainSpeed by rememberPreference(DeeplistRainSpeedKey, 1f)
    var wordBrightness by rememberPreference(DeeplistWordBrightnessKey, 0.23f)
    var rings by rememberPreference(DeeplistRingsKey, true)
    var glow by rememberPreference(DeeplistGlowKey, 0.4f)
    var sparks by rememberPreference(DeeplistSparksKey, 0.65f)
    var lift by rememberPreference(DeeplistLiftKey, 7f)
    var flight by rememberPreference(DeeplistFlightKey, 0.43f)
    var gestures by rememberPreference(DeeplistGesturesKey, true)
    var autoPerformance by rememberPreference(DeeplistAutoPerformanceKey, true)
    var savedStyle by rememberPreference(DeeplistSavedStyleKey, "")
    var savedLyrics by rememberPreference(DeeplistSavedLyricsKey, "[]")
    var savedDialog by remember { mutableStateOf(false) }
    var cardBusy by remember { mutableStateOf(false) }
    var studioStatus by remember { mutableStateOf<Int?>(null) }
    val automaticEconomy = rememberDeeplistEconomy(playing, autoPerformance, metadata?.id)
    val effectivePerformance = performance || automaticEconomy
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(controls, immersive, settings, transportPreview) { if (immersive && controls && !settings && transportPreview == null) { delay(4000L); controls = false } }
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
    var offsetDraft by remember(metadata?.id) { mutableIntStateOf(offset.toInt()) }
    LaunchedEffect(offset) { offsetDraft = offset.toInt() }
    fun adjustOffset(delta: Int, reset: Boolean = false) {
        val current = song?.song ?: return
        if (current.id != metadata?.id) return
        offsetDraft = if (reset) 0 else (offsetDraft + delta).coerceIn(-10000, 10000)
        val changed = offsetDraft
        database.query { upsert(current.copy(lyricsOffset = changed)) }
    }
    fun applyStyle(v: List<Float>) {
        preset = v[0].toInt().coerceIn(0,2); fontSize = v[1].coerceIn(30f,64f)
        motion = v[2] >= 0.5f; cover = v[3] >= 0.5f; accentIndex = v[4].toInt().coerceIn(0,2)
        speed = v[5].coerceIn(0.7f,1.7f); showContext = v[6] >= 0.5f; neon = v[7].coerceIn(0f,1f)
        performance = v[8] >= 0.5f; backgroundMode = v[9].toInt().coerceIn(0,2); density = v[10].toInt().coerceIn(6,36)
        rainSpeed = v[11].coerceIn(0.2f,2f); wordBrightness = v[12].coerceIn(0.07f,0.32f)
        rings = v[13] >= 0.5f; glow = v[14].coerceIn(0f,1f); sparks = v[15].coerceIn(0f,1f)
        lift = v[16].coerceIn(0f,14f); flight = v[17].coerceIn(0.2f,0.65f)
    }
    fun resetStyle() = applyStyle(listOf(1f,44f,1f,0f,0f,1f,0f,0.65f,0f,1f,28f,1f,0.23f,1f,0.4f,0.65f,7f,0.43f))
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
    LaunchedEffect(metadata?.id, playing, motion, preset, lifecycleOwner, effectivePerformance) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                position.longValue = latestSlider() ?: connection.player.currentPosition
                if (playing && motion && preset != 2 && !effectivePerformance) withFrameNanos { }
                else delay(100L)
            }
        }
    }
    val times = remember(entries) { entries.map { it.time } }
    val activeIndex by remember(times, position, offset) {
        derivedStateOf { wordstormLineIndex(times, position.longValue + offset) }
    }
    val active = entries.getOrNull(activeIndex)
    val rainWords = remember(entries) { entries.take(12).flatMap { it.text.split(Regex("\\s+")) } }
    val gestureThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val gestureModifier = if (immersive && gestures) Modifier
        .pointerInput(playing, canSeek) {
            detectTapGestures(onTap = { controls = !controls }, onDoubleTap = {
                if (canSeek) { if (playing) connection.player.pause() else connection.player.play() }
            })
        }.pointerInput(canSeek) {
            var drag = 0f
            detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
                onHorizontalDrag = { change, amount -> change.consume();drag += amount }, onDragEnd = {
                    if (canSeek && drag < -gestureThreshold) connection.seekToNext()
                    else if (canSeek && drag > gestureThreshold) connection.seekToPrevious()
                    drag = 0f
                })
        } else Modifier.clickable(enabled = immersive, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { controls = !controls }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF05040C)).then(gestureModifier)) {
        val lineHeightLimit = maxHeight * (if (showContext && (!immersive || controls)) 0.4f else 0.7f)
        DeeplistAtmosphere(rainWords, playing && motion, accent, backgroundMode, density, rainSpeed, wordBrightness, rings, glow, effectivePerformance)
        if (immersive) VerciEdgeNeon(playing, true)
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
                        speed = speed, performance = effectivePerformance || !motion || preset == 2,
                        liftAmount = lift, flight = flight, sparks = sparks,
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
        TextButton(onClick = { verciEnabled = !verciEnabled }) { Text(stringResource(if (verciEnabled) R.string.verci_original else R.string.verci_switch), color = accent) }
        TextButton(onClick = { if (immersive) onExitFullscreen() else onFullscreen() }) { Text(stringResource(if (immersive) R.string.deeplist_exit_fullscreen else R.string.deeplist_fullscreen), color = accent) }
        TextButton(onClick = { websiteDraft = website; tokenDraft = pairingToken; websiteError = false; settings = true }, modifier = Modifier) {
            Text(stringResource(R.string.wordstorm_settings), color = accent)
        }
        }
        if (cover) {
            AsyncImage(model = metadata?.thumbnailUrl, contentDescription = stringResource(R.string.wordstorm_cover), modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(58.dp))
        }
        if (immersive) VerciTransport(transportPreview, { transportPreview = it }, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())

        if (!immersive) Text(metadata?.title.orEmpty(), color = accent.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp))
        }
        }
    }
    fun shareCard(lyric: DeeplistSavedLyric) {
        if (cardBusy) return
        cardBusy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { deeplistShareCard(context, lyric) } }
                .onSuccess { intent -> runCatching { context.startActivity(Intent.createChooser(intent, null)) }.onFailure { studioStatus = R.string.deeplist_card_error } }
                .onFailure { studioStatus = R.string.deeplist_card_error }
            cardBusy = false
        }
    }
    if (savedDialog) AlertDialog(onDismissRequest = { savedDialog = false }, title = { Text(stringResource(R.string.deeplist_saved_lyrics)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            val saved = deeplistSavedLyrics(savedLyrics)
            if (saved.isEmpty()) Text(stringResource(R.string.deeplist_no_saved))
            saved.forEach { lyric ->
                Text(lyric.text, fontWeight = FontWeight.Bold)
                Text(lyric.title, fontSize = 12.sp, color = accent)
                TextButton(onClick = { shareCard(lyric) }, enabled = !cardBusy) { Text(stringResource(R.string.deeplist_share_card)) }
            }
            TextButton(onClick = { savedLyrics = "[]" }) { Text(stringResource(R.string.deeplist_clear_saved)) }
        }
    }, confirmButton = { TextButton(onClick = { savedDialog = false }) { Text(stringResource(R.string.wordstorm_done)) } })
    if (settings) {
        val modeNames = listOf(R.string.wordstorm_cinematic, R.string.wordstorm_floating, R.string.wordstorm_karaoke)
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(stringResource(R.string.wordstorm_settings)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Material3SettingsGroup(items = studioItems)
                    listOf(R.string.deeplist_lyrics_tab, R.string.deeplist_background_tab, R.string.deeplist_controls_tab, R.string.deeplist_connection_tab).chunked(2).forEachIndexed { row, labels ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            labels.forEachIndexed { column, title ->
                                val index = row * 2 + column
                                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(stringResource(title)) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (tab < 2) {
                        DeeplistPreview(fontSize, accent, preset, motion, speed, effectivePerformance, neon, backgroundMode, density, rainSpeed, wordBrightness, rings, glow, sparks, lift, flight)
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
                    StudioSlider(R.string.deeplist_lift, lift, 0f..14f, { lift = it })
                    StudioSlider(R.string.deeplist_flight, flight, 0.2f..0.65f, { flight = it })
                    Text(stringResource(R.string.deeplist_word_timing_note), fontSize = 11.sp)
                    Text(stringResource(R.string.deeplist_sync_adjust, offsetDraft))
                    Text(stringResource(R.string.deeplist_sync_hint), fontSize = 11.sp)
                    Row {
                        TextButton(onClick = { adjustOffset(-100) }, enabled = song?.song?.id == metadata?.id) { Text("−100 ms") }
                        TextButton(onClick = { adjustOffset(100) }, enabled = song?.song?.id == metadata?.id) { Text("+100 ms") }
                        TextButton(onClick = { adjustOffset(0,true) }) { Text(stringResource(R.string.deeplist_zero)) }
                    }
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
                    listOf(R.string.deeplist_cloud, R.string.deeplist_rain, R.string.deeplist_quiet).forEachIndexed { i, label ->
                        FilterChip(selected = backgroundMode == i, onClick = { backgroundMode = i }, label = { Text(stringResource(label)) })
                    }
                    StudioSlider(R.string.deeplist_density, density.toFloat(), 6f..36f, { density = it.toInt() }, true)
                    StudioSlider(R.string.deeplist_rain_speed, rainSpeed, 0.2f..2f, { rainSpeed = it })
                    StudioSlider(R.string.deeplist_word_brightness, wordBrightness, 0.07f..0.32f, { wordBrightness = it })
                    StudioSlider(R.string.deeplist_glow, glow, 0f..1f, { glow = it })
                    StudioSlider(R.string.deeplist_sparks, sparks, 0f..1f, { sparks = it })
                    StudioToggle(R.string.deeplist_rings, rings) { rings = it }
                    }
                    if (tab == 2) {
                    StudioToggle(R.string.deeplist_gestures, gestures) { gestures = it }
                    Text(stringResource(R.string.deeplist_gestures_hint), fontSize = 11.sp)
                    StudioToggle(R.string.deeplist_auto_performance, autoPerformance) { autoPerformance = it }
                    StudioToggle(R.string.deeplist_reduce_effects, performance) { performance = it }
                    if (effectivePerformance) Text(stringResource(R.string.deeplist_economy_active), color = accent)
                    Row {
                        TextButton(onClick = { resetStyle() }) { Text(stringResource(R.string.deeplist_approved_style)) }
                        TextButton(onClick = { preset = 2; motion = false; backgroundMode = 2; neon = 0f; sparks = 0f }) { Text(stringResource(R.string.deeplist_quiet)) }
                    }
                    TextButton(onClick = {
                        savedStyle = listOf(preset.toFloat(),fontSize,if(motion)1f else 0f,if(cover)1f else 0f,accentIndex.toFloat(),speed,if(showContext)1f else 0f,neon,if(performance)1f else 0f,backgroundMode.toFloat(),density.toFloat(),rainSpeed,wordBrightness,if(rings)1f else 0f,glow,sparks,lift,flight).joinToString("|")
                        studioStatus = R.string.deeplist_style_saved
                    }) { Text(stringResource(R.string.deeplist_save_style)) }
                    TextButton(onClick = { deeplistStyleValues(savedStyle)?.let { applyStyle(it) } }, enabled = deeplistStyleValues(savedStyle) != null) { Text(stringResource(R.string.deeplist_load_style)) }
                    val track = metadata
                    val current = active
                    if (track != null && current != null) {
                        val lyric = DeeplistSavedLyric(current.text, track.title, track.artists.joinToString { it.name })
                        TextButton(onClick = { savedLyrics = deeplistSaveLyric(savedLyrics, lyric); studioStatus = R.string.deeplist_lyric_saved }) { Text(stringResource(R.string.deeplist_save_lyric)) }
                        TextButton(onClick = { shareCard(lyric) }, enabled = !cardBusy) { Text(stringResource(R.string.deeplist_share_card)) }
                    }
                    TextButton(onClick = { savedDialog = true }) { Text(stringResource(R.string.deeplist_saved_lyrics)) }
                    }
                    if (tab == 3) {
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
                    studioStatus?.let { Text(stringResource(it), fontSize = 12.sp, color = accent) }
                    TextButton(onClick = { resetStyle() }) { Text(stringResource(R.string.wordstorm_reset)) }
                }
            },
            confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.wordstorm_done)) } },
        )
    }
}

@Composable
private fun StudioSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, changed: (Float) -> Unit, integer: Boolean = false) {
    Text(stringResource(label) + " · " + if (integer) value.toInt().toString() else String.format(java.util.Locale.ROOT,"%.2f",value), fontSize = 12.sp)
    Slider(value = value.coerceIn(range.start,range.endInclusive), onValueChange = changed, valueRange = range)
}

@Composable
private fun StudioToggle(label: Int, checked: Boolean, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = changed)
    }
}
