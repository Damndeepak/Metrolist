package com.metrolist.music.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class DeeplistLoop(private val scope: CoroutineScope, private val player: () -> Player, private val canControl: () -> Boolean) {
    var start by mutableStateOf<Long?>(null)
        private set
    var end by mutableStateOf<Long?>(null)
        private set
    private var track: String? = null
    private var job: Job? = null
    fun markStart() {
        clear()
        if (!canControl()) return
        track = player().currentMediaItem?.mediaId
        start = player().currentPosition
    }
    fun markEnd(): Boolean {
        if (!canControl()) { clear(); return false }
        val a = start ?: return false
        val b = player().currentPosition
        if (player().currentMediaItem?.mediaId != track || b - a < 500) return false
        end = b
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val p = player()
                if (!canControl() || p.currentMediaItem?.mediaId != track) { clear(); break }
                if (p.isPlaying && p.currentPosition >= b) p.seekTo(a)
                delay(40)
            }
        }
        return true
    }
    fun clear() { job?.cancel(); job = null; start = null; end = null; track = null }
}
