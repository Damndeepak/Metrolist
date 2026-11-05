// opens the support sheet on its own: once after a new user's first 5 minutes of listening, then 4 times a week

package com.mochyy.mochymusic.ui.screens.donate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.DonateFirstPromptShownKey
import com.mochyy.mochymusic.constants.DonateOpenPromptAtKey
import com.mochyy.mochymusic.constants.DonatePlaytimeMsKey
import com.mochyy.mochymusic.constants.DonatePromptSlotsKey
import com.mochyy.mochymusic.constants.DonatePromptWindowStartKey
import com.mochyy.mochymusic.constants.LastDonateShownTimeKey
import com.mochyy.mochymusic.ui.component.LocalBottomSheetPageState
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random

// a new user gets asked once they've listened this long in total
private const val NewUserPlaytimeMs = 5L * 60 * 1000

// playback is counted in steps this long, a pause throws away the step it was in
private const val PlaytimeStepMs = 10_000L

// a song only counts as listened to after this much straight play, so skipping through a queue doesn't
private const val ListenThresholdMs = 30_000L

// each week asks once when the app is opened and 3 more times at random, 4 in all
private const val WindowMs = 7L * 24 * 60 * 60 * 1000
private const val RandomPromptsPerWindow = 3

// nothing comes due in the first hours of a week, so the week's first ask can't follow the last one straight away
private const val WindowLeadInMs = 6L * 60 * 60 * 1000

// two asks never land closer than this, whatever came due
private const val MinGapMs = 12L * 60 * 60 * 1000

// let home draw its first frames before a sheet slides over it
private const val AppOpenSettleMs = 2_000L

/** place once inside PopupSheetHost's content, enabled = false (setup wizard still up) holds everything */
@Composable
fun DonatePromptScheduler(enabled: Boolean) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sheet = LocalZoomOutOverlayState.current
    val otherSheets = listOf(LocalMenuState.current, LocalBottomSheetPageState.current)
    val isEnabled by rememberUpdatedState(enabled)

    // every time the app comes to the front counts as an open, leaving within the settle time cancels it
    LaunchedEffect(lifecycle) {
        // the ask is already used up once decided, so it waits out here where a pause or leaving can't cancel it
        val ask = { launch { showWhenFree(lifecycle, sheet, otherSheets) } }
        lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.RESUMED) }
            .distinctUntilChanged()
            .collectLatest { onScreen ->
                if (!onScreen) return@collectLatest
                delay(AppOpenSettleMs)
                if (isEnabled && onAppOpened(context.dataStore, System.currentTimeMillis())) ask()
            }
    }

    if (playerConnection == null) return
    LaunchedEffect(playerConnection) {
        val ask = { launch { showWhenFree(lifecycle, sheet, otherSheets) } }
        val dataStore = context.dataStore
        var newUserDone = dataStore.data.first()[DonateFirstPromptShownKey] == true
        var lastCountedId: String? = null

        combine(playerConnection.mediaMetadata, playerConnection.isPlaying) { metadata, playing ->
            metadata?.id.takeIf { playing }
        }.collectLatest { playingId ->
            // restarts on every pause and track change, so only an unbroken stretch of play counts
            if (playingId == null) return@collectLatest
            var playedMs = 0L
            while (!newUserDone || playingId != lastCountedId) {
                delay(PlaytimeStepMs)
                playedMs += PlaytimeStepMs
                if (!isEnabled) continue
                val now = System.currentTimeMillis()

                if (!newUserDone) {
                    if (addPlaytime(dataStore, now, PlaytimeStepMs)) {
                        newUserDone = true
                        // this song already got its ask, it shouldn't also count toward a random one
                        lastCountedId = playingId
                        ask()
                    }
                    continue
                }

                if (playedMs < ListenThresholdMs) continue
                lastCountedId = playingId
                if (onSongListened(dataStore, now)) ask()
            }
        }
    }
}

// waits until the app is on screen and no other sheet is up, then asks
private suspend fun showWhenFree(lifecycle: Lifecycle, sheet: PopupSheetState, otherSheets: List<PopupSheetState>) {
    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
    snapshotFlow { !sheet.isVisible && otherSheets.none { it.isVisible } }.first { it }
    sheet.showSupportSheet()
}

/** adds listening time for a new user, true once they've reached the first ask */
private suspend fun addPlaytime(dataStore: DataStore<Preferences>, now: Long, stepMs: Long): Boolean {
    var show = false
    dataStore.edit { prefs ->
        if (prefs[DonateFirstPromptShownKey] == true) return@edit
        val played = (prefs[DonatePlaytimeMsKey] ?: 0L) + stepMs
        prefs[DonatePlaytimeMsKey] = played
        if (played < NewUserPlaytimeMs) return@edit
        prefs[DonateFirstPromptShownKey] = true
        startWindow(prefs, now)
        prefs[LastDonateShownTimeKey] = now
        show = true
    }
    return show
}

/** true if this week's app-open ask has come due and nothing was shown too recently */
private suspend fun onAppOpened(dataStore: DataStore<Preferences>, now: Long): Boolean {
    var show = false
    dataStore.edit { prefs ->
        if (!weekIsRunning(prefs, now)) return@edit
        val due = prefs[DonateOpenPromptAtKey] ?: 0L
        if (due == 0L || now < due || tooSoon(prefs, now)) return@edit
        prefs[DonateOpenPromptAtKey] = 0L
        prefs[LastDonateShownTimeKey] = now
        show = true
    }
    return show
}

/** true if one of this week's random asks has come due on this listened-to song */
private suspend fun onSongListened(dataStore: DataStore<Preferences>, now: Long): Boolean {
    var show = false
    dataStore.edit { prefs ->
        if (!weekIsRunning(prefs, now)) return@edit
        val slots = prefs[DonatePromptSlotsKey].orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        val (due, pending) = slots.partition { it <= now }
        if (due.isEmpty() || tooSoon(prefs, now)) return@edit
        // several overdue at once (app unused for days) still means one ask, not a burst
        prefs[DonatePromptSlotsKey] = pending.joinToString(",")
        prefs[LastDonateShownTimeKey] = now
        show = true
    }
    return show
}

// false before the first ask, and on the call that had to start a fresh week (its times are still ahead)
private fun weekIsRunning(prefs: MutablePreferences, now: Long): Boolean {
    if (prefs[DonateFirstPromptShownKey] != true) return false
    val weekStart = prefs[DonatePromptWindowStartKey] ?: 0L
    // no open-ask time yet means the install still has the old schedule, so it moves to the weekly one now
    val oldSchedule = prefs[DonateOpenPromptAtKey] == null
    if (weekStart == 0L || now >= weekStart + WindowMs || oldSchedule) {
        // asks left over from a week that ended unused are dropped, not carried over
        startWindow(prefs, now)
        return false
    }
    return true
}

private fun tooSoon(prefs: Preferences, now: Long): Boolean =
    now - (prefs[LastDonateShownTimeKey] ?: 0L) < MinGapMs

private fun startWindow(prefs: MutablePreferences, now: Long) {
    prefs[DonatePromptWindowStartKey] = now
    prefs[DonateOpenPromptAtKey] = randomTimeInWindow(now)
    prefs[DonatePromptSlotsKey] = List(RandomPromptsPerWindow) { randomTimeInWindow(now) }
        .sorted()
        .joinToString(",")
}

private fun randomTimeInWindow(windowStart: Long): Long =
    windowStart + Random.nextLong(WindowLeadInMs, WindowMs)
