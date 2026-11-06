package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val source = wordstormLyricsForTrack(metadata?.id, entity?.id, entity?.lyrics).orEmpty()
    val synced = remember(source) { lyricsTextLooksSynced(source) }
    val lines by produceState(emptyList<com.metrolist.music.lyrics.LyricsEntry>(), source) {
        value = withContext(Dispatchers.Default) {
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
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
        AsyncImage(metadata?.thumbnailUrl, null, Modifier.fillMaxSize().blur(48.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .48f)))
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AsyncImage(metadata?.thumbnailUrl, metadata?.title,
                    Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).widthIn(max = 360.dp).clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState, verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 160.dp)) {
                    if (lines.isEmpty()) item { Text(stringResource(R.string.lyrics_not_found), color = Color.White) }
                    itemsIndexed(lines) { index, line ->
                        val wordIndex = if (index == active) artworkActiveWord(line.words, position) else -1
                        val hasTiming = !line.words.isNullOrEmpty() && line.words!!.all { it.startTime.isFinite() && it.endTime.isFinite() && it.startTime >= 0 && it.endTime > it.startTime }
                        Text(buildAnnotatedString {
                            if (!hasTiming) append(line.text)
                            else line.words!!.forEachIndexed { i, word ->
                                withStyle(SpanStyle(color = Color.White.copy(alpha = if (index == active && i == wordIndex) 1f else .36f), fontSize = if (index == active && i == wordIndex) 31.sp else 28.sp)) {
                                    append(word.text)
                                    if (word.hasTrailingSpace) append(" ")
                                }
                            }
                        }, color = Color.White.copy(alpha = if (index == active) 1f else .36f), fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ArtworkTransport(if (song?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border, R.string.like, connection::toggleLike)
                    ArtworkTransport(R.drawable.skip_previous, R.string.previous, connection::seekToPrevious)
                    ArtworkTransport(if (playing) R.drawable.pause else R.drawable.play, if (playing) R.string.pause else R.string.play, connection::togglePlayPause)
                    ArtworkTransport(R.drawable.skip_next, R.string.next, connection::seekToNext)
                }
            }
        }
    }
}

@Composable
private fun ArtworkTransport(icon: Int, label: Int, onClick: () -> Unit) {
    Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .2f))) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(icon), stringResource(label), tint = Color.White)
        }
    }
}
