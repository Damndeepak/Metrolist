package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun VerciTransport(preview: Long?, onPreview: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val connection = LocalPlayerConnection.current ?: return
    val manager = LocalListenTogetherManager.current
    val canSeek = manager?.isInRoom != true || manager.isHost
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    var position by remember { mutableLongStateOf(connection.player.currentPosition) }
    var duration by remember { mutableLongStateOf(connection.player.duration.coerceAtLeast(0)) }
    LaunchedEffect(connection) {
        while (isActive) {
            position = connection.player.currentPosition
            duration = connection.player.duration.coerceAtLeast(0)
            delay(100)
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
        Slider(
            value = (preview ?: position).toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { onPreview(it.toLong()) },
            onValueChangeFinished = { preview?.let { connection.seekTo(it) }; onPreview(null) },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = canSeek && duration > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(verciTime(preview ?: position)); Text(verciTime(duration))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { connection.seekToPrevious() }, enabled = canSeek) {
                Icon(painterResource(R.drawable.skip_previous), stringResource(R.string.deeplist_previous))
            }
            IconButton(onClick = { if (playing) connection.player.pause() else connection.player.play() }, enabled = canSeek) {
                Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), stringResource(R.string.deeplist_play_pause))
            }
            IconButton(onClick = { connection.seekToNext() }, enabled = canSeek) {
                Icon(painterResource(R.drawable.skip_next), stringResource(R.string.deeplist_next))
            }
        }
    }
}

internal fun verciTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
