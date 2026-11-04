package com.metrolist.music.ui.screens

import android.content.Intent
import coil3.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.SongWithStats
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

private data class CapsuleMonthData(
    val events: List<Event>,
    val previousTime: Long?,
    val songs: List<SongWithStats>,
    val artists: List<Artist>,
    val artistCount: Int,
)

@Composable
fun SoundCapsuleScreen(navController: NavController) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var shareFailed by remember { mutableStateOf(false) }
    val currentMonth = remember { YearMonth.now() }
    var month by remember { mutableStateOf(currentMonth) }
    val start = remember(month) { month.atDay(1).atStartOfDay() }
    val end = remember(month) { month.plusMonths(1).atDay(1).atStartOfDay() }
    val data by key(month) {
        remember(start, end) {
            combine(
                database.capsuleEvents(start, end),
                database.getTotalPlayTimeInRange(start.minusMonths(1), start.minusNanos(1)),
                database.mostPlayedSongsStats(start.minusNanos(1), limit = 5, toTimeStamp = end.minusNanos(1)),
                database.mostPlayedArtists(start.minusNanos(1), limit = 5, toTimeStamp = end.minusNanos(1)),
                database.getUniqueArtistCountInRange(start, end.minusNanos(1)),
            ) { events, previousTime, songs, artists, artistCount ->
                CapsuleMonthData(events, previousTime, songs, artists, artistCount)
            }
        }.collectAsStateWithLifecycle(initialValue = null)
    }
    val events = data?.events.orEmpty()
    val previousTime = data?.previousTime
    val songs = data?.songs.orEmpty()
    val artists = data?.artists.orEmpty()
    val artistCount = data?.artistCount ?: 0
    val summary = remember(events, month) { capsuleSummary(month, events.map { it.timestamp.toLocalDate() to it.playTime }) }
    val activeDays = summary.dailyMinutes.count { it > 0 }
    val peakDay = summary.dailyMinutes.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index?.plus(1)
    val peakPeriod = remember(events) { events.groupBy { it.timestamp.hour / 6 }.mapValues { (_, plays) -> plays.sumOf { it.playTime } }.maxByOrNull { it.value }?.key }
    val periodLabels = listOf(R.string.dp_capsule_night, R.string.dp_capsule_morning, R.string.dp_capsule_afternoon, R.string.dp_capsule_evening)
    val title = remember(month) { month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) }
    val accent = MaterialTheme.colorScheme.primary
    val dailyDescription = summary.dailyMinutes.mapIndexed { index, minutes -> "${index + 1}: $minutes min" }.joinToString(", ")
    val shareTitle = stringResource(R.string.dp_sound_capsule)
    val minutesLabel = stringResource(R.string.dp_capsule_minutes)
    val streakLabel = stringResource(R.string.dp_capsule_streak, summary.longestStreak)
    val cardData = CapsuleShareData(
        title = stringResource(R.string.dp_capsule_my_month, month.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))),
        month = title,
        artistsLabel = stringResource(R.string.dp_capsule_top_artists),
        songsLabel = stringResource(R.string.dp_capsule_top_songs),
        minutesLabel = minutesLabel,
        minutes = summary.totalMinutes,
        artists = artists.map { it.artist.name },
        songs = songs.map { it.title },
    )
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Vertical)),
        contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.dp_back)) }
            Text(shareTitle, style = MaterialTheme.typography.headlineLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text(stringResource(R.string.dp_capsule_previous)) }
                TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < currentMonth) { Text(stringResource(R.string.dp_capsule_next)) }
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary.totalMinutes.toString(), style = MaterialTheme.typography.displayLarge)
                    Text(minutesLabel, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.dp_capsule_unique, events.map { it.songId }.distinct().size, artistCount))
                    Text(streakLabel)
                    previousTime?.takeIf { it > 0 }?.let { previous ->
                        val change = ((summary.totalMillis.toDouble() - previous) * 100 / previous).toInt()
                        Text(stringResource(R.string.dp_capsule_change, if (change >= 0) "+$change" else "$change"))
                    }
                }
            }
        }
        if (data == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (data != null && events.isEmpty()) item { Text(stringResource(R.string.dp_capsule_empty)) }
        if (events.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.dp_capsule_rhythm),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.dp_capsule_days,activeDays,month.lengthOfMonth()))
                peakDay?.let { Text(stringResource(R.string.dp_capsule_peak_day, month.atDay(it).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())))) }
                peakPeriod?.let { Text(stringResource(R.string.dp_capsule_peak_time,stringResource(periodLabels[it]))) }
            } }
        }
        item {
            Text(stringResource(R.string.dp_capsule_daily), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = dailyDescription }) {
                val highest = summary.dailyMinutes.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                val slot = size.width / summary.dailyMinutes.size.coerceAtLeast(1)
                summary.dailyMinutes.forEachIndexed { index, minutes ->
                    val height = if (minutes == 0L) 2.dp.toPx() else (size.height * minutes.toFloat() / highest).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(accent.copy(alpha = if (minutes == 0L) .14f else 1f), Offset(index * slot, size.height - height), Size((slot - 3.dp.toPx()).coerceAtLeast(1f), height), CornerRadius(3.dp.toPx()))
                }
            }
        }
        item { Text(stringResource(R.string.dp_capsule_top_songs), style = MaterialTheme.typography.titleLarge) }
        itemsIndexed(songs, key = { _, song -> "song-" + song.id }) { index, song ->
            ListItem(headlineContent = { Text(song.title) }, supportingContent = { Text(song.artists.joinToString { it.name }) }, leadingContent = { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text("${index + 1}"); AsyncImage(model=song.thumbnailUrl,contentDescription=null,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) } }, trailingContent = { Text("${(song.timeListened ?: 0L) / 60000} min") })
        }
        item { Text(stringResource(R.string.dp_capsule_top_artists), style = MaterialTheme.typography.titleLarge) }
        itemsIndexed(artists, key = { _, artist -> "artist-" + artist.artist.id }) { index, artist ->
            ListItem(headlineContent = { Text(artist.artist.name) }, leadingContent = { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text("${index + 1}"); AsyncImage(model=artist.artist.thumbnailUrl,contentDescription=null,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))) } }, trailingContent = { Text("${(artist.timeListened?.toLong() ?: 0L) / 60000} min") })
        }
        item {
            Button(onClick = {
                val snapshot = cardData
                sharing = true
                shareFailed = false
                scope.launch {
                    try {
                        val intent = withContext(Dispatchers.IO) { capsuleShareCard(context, snapshot) }
                        context.startActivity(Intent.createChooser(intent, shareTitle))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        shareFailed = true
                    } finally { sharing = false }
                }
            }, enabled = data != null && events.isNotEmpty() && !sharing, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dp_capsule_share)) }
            if (shareFailed) Text(stringResource(R.string.dp_capsule_share_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { navController.navigate("stats") }) { Text(stringResource(R.string.dp_capsule_details)) }
        }
    }
}
