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

@Composable
fun SoundCapsuleScreen(navController: NavController) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val currentMonth = remember { YearMonth.now() }
    var month by remember { mutableStateOf(currentMonth) }
    val start = remember(month) { month.atDay(1).atStartOfDay() }
    val end = remember(month) { month.plusMonths(1).atDay(1).atStartOfDay() }
    val events by remember(start, end) { database.capsuleEvents(start, end) }.collectAsStateWithLifecycle(emptyList())
    val previousTime by remember(start) { database.getTotalPlayTimeInRange(start.minusMonths(1), start.minusNanos(1)) }.collectAsStateWithLifecycle(null)
    val songs by remember(start, end) { database.mostPlayedSongsStats(start.minusNanos(1), limit = 5, toTimeStamp = end.minusNanos(1)) }.collectAsStateWithLifecycle(emptyList())
    val artists by remember(start, end) { database.mostPlayedArtists(start.minusNanos(1), limit = 5, toTimeStamp = end.minusNanos(1)) }.collectAsStateWithLifecycle(emptyList())
    val artistCount by remember(start, end) { database.getUniqueArtistCountInRange(start, end.minusNanos(1)) }.collectAsStateWithLifecycle(0)
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
        if (events.isEmpty()) item { Text(stringResource(R.string.dp_capsule_empty)) }
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
                val text = "$shareTitle · $title\n${summary.totalMinutes} $minutesLabel\n$streakLabel\n" + songs.firstOrNull()?.let { "${it.title} · ${it.artists.joinToString { a -> a.name }}" }.orEmpty()
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, shareTitle))
            }, enabled = events.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dp_capsule_share)) }
            TextButton(onClick = { navController.navigate("stats") }) { Text(stringResource(R.string.dp_capsule_details)) }
        }
    }
}
