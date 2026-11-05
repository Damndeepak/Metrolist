// Scrobbler.kt

package com.mochyy.mochymusic.scrobble

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.LastFMUseNowPlaying
import com.mochyy.mochymusic.constants.LastFMUseSendLikes
import com.mochyy.mochymusic.utils.OfflineMode
import com.mochyy.mochymusic.utils.dataStore
import com.music.lastfm.Audioscrobbler
import com.music.lastfm.AudioscrobblerException
import com.music.lastfm.ScrobbleTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends what's played to Last.fm and Libre.fm. Every play is saved to [ScrobbleQueue] first and
 * sent from there, so nothing is lost offline: what's left is tried again once there's a network,
 * by WorkManager if the app is closed by then. Now Playing goes straight out and is never queued,
 * since it means nothing once the song is over.
 */
@Singleton
class Scrobbler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ScrobbleQueue(File(context.filesDir, "scrobble_queue.json"))
    private val sending = Mutex()

    /** Plays still waiting to be sent, for the settings page. */
    val pending: StateFlow<Int> get() = queue.size

    init {
        // plays left from last time go out as soon as the app is back
        scope.launch {
            queue.load()
            if (queue.size.value > 0) send()
        }
    }

    fun nowPlaying(track: ScrobbleTrack) {
        if (OfflineMode.isOn) return
        scope.launch {
            if (context.dataStore.data.first()[LastFMUseNowPlaying] == false) return@launch
            for ((site, session) in sites(includePaused = false)) {
                try {
                    site.api.updateNowPlaying(session, track)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // nothing to keep: by the next try it's another song
                    if (e is AudioscrobblerException && e.isSignedOut) signedOut(site)
                    Timber.tag(LOG_TAG).d(e, "${site.label} Now Playing didn't go through")
                }
            }
        }
    }

    /** A play that counted, which started at [startedAt] (seconds since 1970). */
    fun scrobble(track: ScrobbleTrack, startedAt: Long) {
        scope.launch {
            val sites = sites(includePaused = false).map { it.first }.toSet()
            if (sites.isEmpty()) return@launch
            queue.add(PendingScrobble(track, startedAt, sites))
            send()
        }
    }

    /** A like here becomes a love there, when the user asked for that. */
    fun love(artist: String, track: String, love: Boolean) {
        if (OfflineMode.isOn || artist.isBlank()) return
        scope.launch {
            if (context.dataStore.data.first()[LastFMUseSendLikes] != true) return@launch
            for ((site, session) in sites(includePaused = false)) {
                try {
                    site.api.love(session, artist, track, love)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is AudioscrobblerException && e.isSignedOut) signedOut(site)
                    Timber.tag(LOG_TAG).d(e, "${site.label} love didn't go through")
                }
            }
        }
    }

    /**
     * Sends what's waiting, a batch at a time, until each service has it all or can't take more
     * right now. False when something has to be tried again later (no network, the service down),
     * which also books that try.
     */
    suspend fun send(): Boolean = sending.withLock {
        // offline mode keeps the app off the network; the next play after it's turned off sends these
        if (OfflineMode.isOn) return@withLock true
        var allSent = true
        // paused services still get plays from before the pause
        for ((site, session) in sites(includePaused = true)) {
            while (true) {
                val batch = queue.nextFor(site, Audioscrobbler.MAX_BATCH)
                if (batch.isEmpty()) break
                try {
                    site.api.scrobble(session, batch.map { it.track to it.startedAt })
                    queue.markSent(site, batch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AudioscrobblerException) {
                    when {
                        e.isSignedOut -> {
                            signedOut(site)
                            break
                        }
                        // turned down for good (bad song data): kept, it would hold back every play after it
                        e.isRejected -> {
                            Timber.tag(LOG_TAG).w(e, "${site.label} turned down ${batch.size} scrobbles")
                            queue.markSent(site, batch)
                        }
                        else -> {
                            allSent = false
                            break
                        }
                    }
                } catch (e: Exception) {
                    // no network, most likely
                    Timber.tag(LOG_TAG).d(e, "${site.label} scrobbles will be sent later")
                    allSent = false
                    break
                }
            }
        }
        if (!allSent) sendLater()
        allSent
    }

    // ---- signing in and out ----

    /** signs in from mochy's own form and keeps only the session key, the password is never stored; gives back the account's name */
    suspend fun signIn(site: ScrobbleSite, username: String, password: String): String {
        val session = site.api.getMobileSession(username.trim(), password)
        context.dataStore.edit {
            it[site.sessionKey] = session.key
            it[site.usernameKey] = session.name
            it[site.enabledKey] = true
        }
        return session.name
    }

    suspend fun signOut(site: ScrobbleSite) {
        context.dataStore.edit {
            it.remove(site.sessionKey)
            it.remove(site.usernameKey)
            it.remove(site.enabledKey)
        }
        queue.forget(site)
    }

    /** The service took back Mochy's access, so the account is forgotten here too. */
    private suspend fun signedOut(site: ScrobbleSite) {
        signOut(site)
        val message = context.getString(R.string.scrobble_signed_out, site.label)
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    /** Signed-in services and their session keys; paused ones only with [includePaused]. */
    private suspend fun sites(includePaused: Boolean): List<Pair<ScrobbleSite, String>> {
        val prefs = context.dataStore.data.first()
        return ScrobbleSite.entries.mapNotNull { site ->
            val session = prefs[site.sessionKey]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!includePaused && prefs[site.enabledKey] == false) return@mapNotNull null
            site to session
        }
    }

    /** Hands the rest to WorkManager, which waits for a network and survives the app closing. */
    private fun sendLater() {
        val request = OneTimeWorkRequestBuilder<ScrobbleSendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val LOG_TAG = "Scrobble"
        const val WORK_NAME = "scrobble_send"
    }
}
