// AccountImportSession.kt

package com.mochyy.mochymusic.importer.account

import android.content.Context
import com.mochyy.mochymusic.constants.AccountNameKey
import com.mochyy.mochymusic.extensions.isUserLoggedIn
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.importer.ImportedTrack
import com.mochyy.mochymusic.importer.ParsedImport
import com.mochyy.mochymusic.importer.PlaylistOrigin
import com.mochyy.mochymusic.importer.ResyncRead
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.music.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

sealed interface AccountImportState {
    /** The service's sign-in page is showing. */
    data object SigningIn : AccountImportState
    /** YouTube Music uses the app's account, which isn't signed in yet. */
    data object NeedsYouTubeSignIn : AccountImportState
    data class Loading(val label: String) : AccountImportState
    data class Choosing(
        val accountName: String?,
        val playlists: List<RemotePlaylist>,
        val selected: Set<String>,
        /** A Re-sync that couldn't find this playlist by name: the user picks which one it is. */
        val pickingFor: String? = null,
    ) : AccountImportState
    data class Reading(val label: String, val done: Int, val total: Int) : AccountImportState
    data class Failed(val message: String, val signedOut: Boolean) : AccountImportState
}

/** The imported playlist a Re-sync reads again: its id here, its name, and its id on the service if saved. */
data class ResyncTarget(val playlistId: String, val name: String, val remoteId: String?)

/** What a Re-sync read after sign-in, by playlist id here, and the names it couldn't find. */
data class ResyncReads(val reads: Map<String, ResyncRead>, val missing: List<String>)

/**
 * One sign-in → list → pick → read pass against one service, shared by the Import settings page
 * and onboarding. Runs on the [scope] it's given (a ViewModel's), so it stops with its screen.
 */
class AccountImportSession(
    val service: AccountService,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val connector: AccountConnector? = when (service) {
        AccountService.SPOTIFY -> SpotifyConnector
        AccountService.APPLE_MUSIC -> AppleMusicConnector
        AccountService.TIDAL -> TidalConnector
        AccountService.YOUTUBE_MUSIC -> null
    }

    private val _state = MutableStateFlow<AccountImportState>(AccountImportState.Loading("Getting ready…"))
    val state: StateFlow<AccountImportState> = _state.asStateFlow()

    /** What the user picked, read and ready to import. */
    private val _ready = MutableStateFlow<ParsedImport?>(null)
    val ready: StateFlow<ParsedImport?> = _ready.asStateFlow()

    /** A one-off note for the list page (e.g. some playlists couldn't be read). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Set for a Re-sync: after sign-in these playlists are read, instead of listing them all to pick from. */
    @Volatile private var resyncTargets: List<ResyncTarget> = emptyList()

    /** A Re-sync's playlists, read and ready to be matched. */
    private val _resyncReads = MutableStateFlow<ResyncReads?>(null)
    val resyncReads: StateFlow<ResyncReads?> = _resyncReads.asStateFlow()

    private val credentials = CapturedCredentials()
    private val connectSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile var library: AccountLibrary? = null
        private set
    private var connectJob: Job? = null
    private var workJob: Job? = null
    private var lastChoosing: AccountImportState.Choosing? = null

    fun start() {
        if (service == AccountService.YOUTUBE_MUSIC) refreshYouTube() else startSignIn()
    }

    /** Signs in like an import does, then reads only [targets]. */
    fun startResync(targets: List<ResyncTarget>) {
        resyncTargets = targets
        start()
    }

    // ---- sign-in ----

    private fun startSignIn() {
        library = null
        lastChoosing = null
        _state.value = AccountImportState.SigningIn
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.IO) {
            for (signal in connectSignal) {
                if (library != null) break
                try {
                    val connected = connector?.connect(credentials) ?: continue
                    library = connected
                    afterConnect()
                    break
                } catch (e: CancellationException) {
                    // only a real cancellation ends sign-in; a stray one from a request mustn't
                    // leave the page waiting with nothing listening
                    ensureActive()
                    Timber.w(e, "${service.label} connect attempt was cut short")
                } catch (e: AccountRetryLater) {
                    delay(e.seconds * 1000)
                    connectSignal.trySend(Unit)
                } catch (e: Exception) {
                    // offline for a moment, or the service hiccuped: the next captured request retries
                    Timber.w(e, "${service.label} connect attempt failed")
                }
            }
        }
    }

    /** From the web page's hook or request headers; may arrive on any thread. */
    fun onHeader(name: String, value: String, url: String) {
        if (library != null) return
        credentials.addHeader(name, value, url)
        connectSignal.trySend(Unit)
    }

    /** Apple: tokens read straight from MusicKit in the page, as JSON {dev, user}. */
    fun onApplePageProbe(raw: String?) {
        if (library != null || raw.isNullOrBlank() || raw == "null") return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        json.optString("dev").takeIf { it.length > 20 && it != "null" }?.let {
            credentials.addHeader("authorization", "Bearer $it", "https://amp-api.music.apple.com/")
        }
        json.optString("user").takeIf { it.length > 20 && it != "null" }?.let {
            credentials.addHeader("media-user-token", it, "https://amp-api.music.apple.com/")
        }
        connectSignal.trySend(Unit)
    }

    /** Apple also keeps the user token as a cookie on music.apple.com. */
    fun onCookies(cookieHeader: String?) {
        if (library != null || cookieHeader.isNullOrBlank()) return
        cookieHeader.split(';').forEach { pair ->
            if (pair.substringBefore('=').trim().equals("media-user-token", ignoreCase = true)) {
                credentials.addHeader("media-user-token", pair.substringAfter('=').trim(), "https://music.apple.com/")
                connectSignal.trySend(Unit)
            }
        }
    }

    /** Forget this session (the caller clears the web cookies) and sign in again. */
    fun signInAgain() {
        workJob?.cancel()
        credentials.clear()
        library = null
        start()
    }

    fun refreshYouTube() {
        if (service != AccountService.YOUTUBE_MUSIC) return
        scope.launch(Dispatchers.IO) {
            if (!context.isUserLoggedIn()) {
                _state.value = AccountImportState.NeedsYouTubeSignIn
                return@launch
            }
            if (library == null) {
                val info = YouTube.accountInfo().getOrNull()
                library = YouTubeMusicLibrary(
                    accountName = info?.name ?: context.dataStore.get(AccountNameKey, "").ifBlank { null },
                    accountPhotoUrl = info?.thumbnailUrl,
                )
            }
            afterConnect()
        }
    }

    // ---- choosing ----

    fun retry() {
        if (library != null) scope.launch(Dispatchers.IO) { afterConnect() } else signInAgain()
    }

    private suspend fun afterConnect() {
        val targets = resyncTargets
        if (targets.isEmpty()) loadPlaylists() else readForResync(targets)
    }

    private suspend fun loadPlaylists() {
        val lib = library ?: return
        _state.value = AccountImportState.Loading("Reading your ${service.label} library…")
        try {
            val playlists = lib.playlists()
            val choosing = AccountImportState.Choosing(lib.accountName, playlists, emptySet())
            lastChoosing = choosing
            _state.value = choosing
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            Timber.w(e, "${service.label} library read was cut short")
            _state.value = AccountImportState.Failed("Couldn't read your ${service.label} library.", signedOut = false)
        } catch (e: AccountImportException) {
            if (e.signedOut) library = null
            _state.value = AccountImportState.Failed(e.message ?: "Couldn't read your library.", e.signedOut)
        } catch (e: Exception) {
            Timber.e(e, "${service.label} library read failed")
            _state.value = AccountImportState.Failed("Couldn't read your ${service.label} library.", signedOut = false)
        }
    }

    fun toggle(id: String) = updateChoosing { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }

    fun selectAll() = updateChoosing { it.copy(selected = it.playlists.map(RemotePlaylist::id).toSet()) }

    fun selectNone() = updateChoosing { it.copy(selected = emptySet()) }

    private fun updateChoosing(change: (AccountImportState.Choosing) -> AccountImportState.Choosing) {
        _state.update { current ->
            if (current is AccountImportState.Choosing) change(current).also { lastChoosing = it } else current
        }
    }

    // ---- reading what was picked ----

    fun readSelected() {
        val choosing = _state.value as? AccountImportState.Choosing ?: return
        val lib = library ?: return
        val chosen = choosing.playlists.filter { it.id in choosing.selected }
        if (chosen.isEmpty()) return

        workJob?.cancel()
        workJob = scope.launch(Dispatchers.IO) {
            val liked = mutableListOf<ImportedTrack>()
            val librarySongs = mutableListOf<ImportedTrack>()
            val playlists = linkedMapOf<String, List<ImportedTrack>>()
            val covers = mutableMapOf<String, String>()
            val origins = mutableMapOf<String, PlaylistOrigin>()
            val failed = mutableListOf<String>()
            var signedOut = false

            for ((index, playlist) in chosen.withIndex()) {
                _state.value = AccountImportState.Reading("Reading \"${playlist.name}\"", index, chosen.size)
                try {
                    val tracks = lib.tracks(playlist)
                    when {
                        playlist.isLiked -> liked += tracks
                        playlist.isLibrary -> librarySongs += tracks
                        else -> {
                            val name = uniqueName(playlist.name, playlists.keys)
                            playlists[name] = tracks
                            playlist.coverUrl?.let { covers[name] = it }
                            origins[name] = PlaylistOrigin(remoteId = playlist.id)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AccountImportException) {
                    failed += playlist.name
                    if (e.signedOut) {
                        signedOut = true
                        break
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Reading ${playlist.name} failed")
                    failed += playlist.name
                }
            }

            if (signedOut) {
                library = null
                _state.value = AccountImportState.Failed("${service.label} signed you out while reading. Sign in again.", signedOut = true)
                return@launch
            }
            _state.value = lastChoosing ?: choosing
            if (liked.isEmpty() && librarySongs.isEmpty() && playlists.isEmpty()) {
                _notice.value = "Couldn't read ${failed.joinToString(", ").ifEmpty { "those playlists" }}."
                return@launch
            }
            _ready.value = ParsedImport(
                likedSongs = liked,
                librarySongs = librarySongs,
                playlists = playlists,
                warnings = if (failed.isEmpty()) emptyList() else listOf("Couldn't read: ${failed.joinToString(", ")}. The rest imports fine."),
                provider = ImportSource.fromKey(service.route),
                playlistCovers = covers,
                playlistOrigins = origins,
            )
        }
    }

    // ---- reading playlists again, for a Re-sync ----

    private suspend fun readForResync(targets: List<ResyncTarget>) {
        val lib = library ?: return
        val single = targets.singleOrNull()
        try {
            val reads = linkedMapOf<String, ResyncRead>()
            val missing = mutableListOf<ResyncTarget>()
            var listed: List<RemotePlaylist>? = null
            for ((index, target) in targets.withIndex()) {
                _state.value = AccountImportState.Reading("Reading \"${target.name}\"", index, targets.size)
                // straight to it when the import saved its id: no need to list the whole library
                val byId = target.remoteId?.let { readById(lib, it, target.name) }
                if (!byId.isNullOrEmpty()) {
                    reads[target.playlistId] = ResyncRead(byId, PlaylistOrigin(remoteId = target.remoteId))
                    continue
                }
                val playlists = listed ?: lib.playlists().filterNot { it.isLiked || it.isLibrary }.also { listed = it }
                val found = playlists.firstOrNull { it.id == target.remoteId } ?: playlists.named(target.name)
                if (found == null) {
                    missing += target
                    continue
                }
                reads[target.playlistId] = ResyncRead(lib.tracks(found), PlaylistOrigin(remoteId = found.id))
            }
            if (single != null && missing.isNotEmpty()) {
                // renamed on one side, or never in this account: the user says which one it is
                val choosing = AccountImportState.Choosing(lib.accountName, listed.orEmpty(), emptySet(), pickingFor = single.name)
                lastChoosing = choosing
                _state.value = choosing
                return
            }
            _resyncReads.value = ResyncReads(reads, missing.map { it.name })
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            Timber.w(e, "${service.label} re-sync read was cut short")
            _state.value = AccountImportState.Failed("Couldn't read your playlists on ${service.label}.", signedOut = false)
        } catch (e: AccountImportException) {
            if (e.signedOut) library = null
            _state.value = AccountImportState.Failed(e.message ?: "Couldn't read your playlists.", e.signedOut)
        } catch (e: Exception) {
            Timber.e(e, "${service.label} re-sync read failed")
            _state.value = AccountImportState.Failed("Couldn't read your playlists on ${service.label}.", signedOut = false)
        }
    }

    /** Null when the service no longer knows that id (deleted, or a link to someone else's playlist). */
    private suspend fun readById(lib: AccountLibrary, id: String, name: String): List<ImportedTrack>? = try {
        lib.tracks(RemotePlaylist(id, name, trackCount = null))
    } catch (e: AccountImportException) {
        if (e.signedOut) throw e
        Timber.w(e, "${service.label} has no playlist $id any more")
        null
    }

    /** The user picked which of the account's playlists a single Re-sync is for. */
    fun pickForResync(id: String) {
        val target = resyncTargets.singleOrNull() ?: return
        val choosing = _state.value as? AccountImportState.Choosing ?: return
        val lib = library ?: return
        val picked = choosing.playlists.firstOrNull { it.id == id } ?: return
        workJob?.cancel()
        workJob = scope.launch(Dispatchers.IO) {
            try {
                _state.value = AccountImportState.Reading("Reading \"${picked.name}\"", 0, 1)
                val read = ResyncRead(lib.tracks(picked), PlaylistOrigin(remoteId = picked.id))
                _resyncReads.value = ResyncReads(mapOf(target.playlistId to read), missing = emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountImportException) {
                if (e.signedOut) library = null
                _state.value = AccountImportState.Failed(e.message ?: "Couldn't read \"${picked.name}\".", e.signedOut)
            } catch (e: Exception) {
                Timber.e(e, "Reading ${picked.name} failed")
                _state.value = AccountImportState.Failed("Couldn't read \"${picked.name}\" on ${service.label}.", signedOut = false)
            }
        }
    }

    fun cancelReading() {
        workJob?.cancel()
        lastChoosing?.let { _state.value = it }
    }

    fun consumeReady() {
        _ready.value = null
    }

    /** Stops anything in flight; used when onboarding switches to another service. */
    fun cancel() {
        connectJob?.cancel()
        workJob?.cancel()
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        var n = 2
        while ("$name ($n)" in taken) n++
        return "$name ($n)"
    }
}
