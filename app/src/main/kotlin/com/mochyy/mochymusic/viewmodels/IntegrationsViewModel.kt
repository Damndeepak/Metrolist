// IntegrationsViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.scrobble.Scrobbler
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings → Integrations: the scrobbling accounts, and the plays still waiting to be sent. */
@HiltViewModel
class IntegrationsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val scrobbler: Scrobbler,
) : ViewModel() {

    /** The name each service is signed in as; a service that isn't signed in is left out. */
    val accounts: StateFlow<Map<ScrobbleSite, String>> = context.dataStore.data
        .map { prefs ->
            ScrobbleSite.entries.mapNotNull { site ->
                val name = prefs[site.usernameKey]?.takeIf { prefs[site.sessionKey] != null }
                name?.let { site to it }
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val pending: StateFlow<Int> = scrobbler.pending

    /** What "Send now" did, for a short message; cleared once shown. */
    private val _sendResult = MutableStateFlow<Int?>(null)
    val sendResult: StateFlow<Int?> = _sendResult.asStateFlow()

    suspend fun signIn(site: ScrobbleSite, username: String, password: String): String =
        scrobbler.signIn(site, username, password)

    fun signOut(site: ScrobbleSite) {
        viewModelScope.launch { scrobbler.signOut(site) }
    }

    fun sendNow() {
        viewModelScope.launch {
            _sendResult.value = if (scrobbler.send()) R.string.scrobble_all_sent else R.string.scrobble_send_later
        }
    }

    fun clearSendResult() {
        _sendResult.value = null
    }
}
