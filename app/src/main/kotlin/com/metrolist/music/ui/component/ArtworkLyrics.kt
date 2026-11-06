package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.lyrics.artworkActiveWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
internal fun ArtworkLyrics(slider: () -> Long?, modifier: Modifier = Modifier) {
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val entity by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    val source = wordstormLyricsForTrack(metadata?.id, entity?.id, entity?.lyrics)
        .takeUnless { it == com.metrolist.music.db.entities.LyricsEntity.LYRICS_NOT_FOUND }.orEmpty()
    val synced = remember(source) { lyricsTextLooksSynced(source) }
    var lines by remember(source) { mutableStateOf(emptyList<com.metrolist.music.lyrics.LyricsEntry>()) }
    LaunchedEffect(source) {
        lines = withContext(Dispatchers.Default) {
            if (synced) LyricsUtils.parseLyrics(source).filter { !it.isBackground && it.text.isNotBlank() }.sortedBy { it.time }
            else source.lines().filter { it.isNotBlank() }.map { com.metrolist.music.lyrics.LyricsEntry(0, it) }
        }
    }
    val owner = LocalLifecycleOwner.current
    val latestSlider by rememberUpdatedState(slider)
    val offset = song?.song?.takeIf { it.id == metadata?.id }?.lyricsOffset ?: 0
    var position by remember(metadata?.id) { mutableLongStateOf(0L) }
    LaunchedEffect(connection, owner, metadata?.id, offset) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                position = (latestSlider() ?: connection.player.currentPosition) + offset
                delay(32)
            }
        }
    }
    val active = if (synced) lines.indexOfLast { it.time <= position } else -1
    val listState = rememberLazyListState()
    LaunchedEffect(active, source) {
        if (active >= 0) listState.animateScrollToItem(active)
        else listState.scrollToItem(0)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val coverHeight = maxHeight * .54f
        val artworkShape = remember {
            GenericShape { size, _ ->
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height * .67f)
                quadraticBezierTo(size.width * .64f, size.height * 1.02f, 0f, size.height)
                close()
            }
        }
        AsyncImage(metadata?.thumbnailUrl, null, Modifier.fillMaxSize().blur(48.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55333345), Color(0xCC291626)))))
        AsyncImage(metadata?.thumbnailUrl, metadata?.title,
            Modifier.fillMaxWidth().height(coverHeight).clip(artworkShape),
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        LazyColumn(
            Modifier.fillMaxWidth().padding(top = maxHeight * .57f).height(maxHeight * .29f),
            state = listState, verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 180.dp),
        ) {
            if (lines.isEmpty()) item { Text(stringResource(R.string.lyrics_not_found), color = Color.White) }
            itemsIndexed(lines) { index, line ->
                val wordIndex = if (index == active) artworkActiveWord(line.words, position) else -1
                val hasTiming = !line.words.isNullOrEmpty() && line.words!!.all { it.startTime.isFinite() && it.endTime.isFinite() && it.startTime >= 0 && it.endTime > it.startTime }
                Text(buildAnnotatedString {
                    if (!hasTiming) append(line.text)
                    else line.words!!.forEachIndexed { i, word ->
                        withStyle(SpanStyle(color = Color.White.copy(alpha = if (index == active && i == wordIndex) 1f else .36f), fontSize = if (index == active && i == wordIndex) 36.sp else 30.sp)) {
                            append(word.text)
                            if (word.hasTrailingSpace) append(" ")
                        }
                    }
                }, color = Color.White.copy(alpha = if (index == active) 1f else .36f), fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(start = 20.dp, end = 88.dp, bottom = 26.dp)) {
            Text(metadata?.artists?.joinToString { it.name }.orEmpty(), color = Color.White.copy(alpha = .55f), fontSize = 12.sp, maxLines = 1)
            Text(metadata?.title.orEmpty(), color = Color.White.copy(alpha = .7f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        }
        // Overlap the empty margins to match the pill gaps while retaining 48dp button bounds.
        Column(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy((-8).dp)) {
            ArtworkTransport(if (song?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border, R.string.like, connection::toggleLike)
            ArtworkTransport(R.drawable.skip_previous, R.string.previous, connection::seekToPrevious)
            ArtworkTransport(if (playing) R.drawable.pause else R.drawable.play, if (playing) R.string.pause else R.string.play, connection::togglePlayPause)
            ArtworkTransport(R.drawable.skip_next, R.string.next, connection::seekToNext)
        }
    }
}

@Composable
private fun ArtworkTransport(icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Surface(color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(50), modifier = Modifier.size(width = 44.dp, height = 32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), stringResource(label), tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
