// AccountImportViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.importer.ParsedImport
import com.mochyy.mochymusic.importer.PlaylistResync
import com.mochyy.mochymusic.importer.ResyncStart
import com.mochyy.mochymusic.importer.account.AccountImportSession
import com.mochyy.mochymusic.importer.account.AccountImportState
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.ui.screens.settings.importsync.ResyncAllArg
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Import settings page's account import; all the work is in [AccountImportSession]. Opened by a
 * Re-sync instead, it signs in the same way and reads just the playlists being re-synced: one
 * playlist's, or with Re-sync all, every one this service has.
 */
@HiltViewModel
class AccountImportViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val playlistResync: PlaylistResync,
) : ViewModel() {

    val service: AccountService = AccountService.fromRoute(savedStateHandle.get<String>("service")) ?: AccountService.SPOTIFY

    /** What a Re-sync opened this page for: a playlist's id, or [ResyncAllArg]. Null for a plain import. */
    private val resyncArg: String? = savedStateHandle.get<String>("resync")
    val isResync: Boolean = resyncArg != null

    /** The services Re-sync all goes on to once this one is done. */
    val nextServices: List<AccountService> = savedStateHandle.get<String>("then").orEmpty()
        .split(',').mapNotNull(AccountService::fromRoute)

    /** Playlists the Re-sync couldn't find in this account, for the message as it starts. */
    var missingFromAccount: List<String> = emptyList()
        private set

    private val session = AccountImportSession(service, context, viewModelScope)

    val state: StateFlow<AccountImportState> = session.state
    val ready: StateFlow<ParsedImport?> = session.ready
    val notice: StateFlow<String?> = session.notice

    /** How the Re-sync went once it was handed on; the page closes on Started. */
    private val _resyncStart = MutableStateFlow<ResyncStart?>(null)
    val resyncStart: StateFlow<ResyncStart?> = _resyncStart.asStateFlow()

    init {
        val arg = resyncArg
        if (arg == null) {
            session.start()
        } else {
            viewModelScope.launch {
                val targets = if (arg == ResyncAllArg) {
                    playlistResync.accountTargets(service)
                } else {
                    listOfNotNull(playlistResync.target(arg))
                }
                if (targets.isEmpty()) {
                    _resyncStart.value = ResyncStart.Failed("There's nothing from ${service.label} to re-sync any more.")
                    return@launch
                }
                session.startResync(targets)
                session.resyncReads.filterNotNull().collect { result ->
                    missingFromAccount = result.missing
                    _resyncStart.value = if (result.reads.isEmpty()) {
                        ResyncStart.Failed("Couldn't find ${result.missing.joinToString(", ")} in your ${service.label} library.")
                    } else {
                        playlistResync.startWithReads(result.reads)
                    }
                }
            }
        }
    }

    fun onHeader(name: String, value: String, url: String) = session.onHeader(name, value, url)
    fun onApplePageProbe(raw: String?) = session.onApplePageProbe(raw)
    fun onCookies(cookieHeader: String?) = session.onCookies(cookieHeader)
    fun signInAgain() = session.signInAgain()
    fun refreshYouTube() = session.refreshYouTube()
    fun retry() = session.retry()
    fun toggle(id: String) = session.toggle(id)
    fun selectAll() = session.selectAll()
    fun selectNone() = session.selectNone()
    fun readSelected() = session.readSelected()
    fun pickForResync(id: String) = session.pickForResync(id)
    fun cancelReading() = session.cancelReading()
    fun consumeReady() = session.consumeReady()
    fun clearNotice() = session.clearNotice()

    /** A Re-sync whose playlist isn't in this account, read from its link instead. */
    fun resyncFromLink(link: String) {
        val playlistId = resyncArg?.takeIf { it != ResyncAllArg } ?: return
        viewModelScope.launch { _resyncStart.value = playlistResync.startFromLink(playlistId, link) }
    }

    /** A failed Re-sync start has been shown; the page stays for another try. */
    fun clearResyncStart() {
        _resyncStart.value = null
    }
}
