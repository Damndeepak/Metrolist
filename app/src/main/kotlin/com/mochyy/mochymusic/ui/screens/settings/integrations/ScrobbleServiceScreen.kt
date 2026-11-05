// the last.fm or libre.fm page: every song that counts is scrobbled, and offline plays wait for a network

package com.mochyy.mochymusic.ui.screens.settings.integrations

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.LastFMUseNowPlaying
import com.mochyy.mochymusic.constants.LastFMUseSendLikes
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.IntegrationsViewModel

const val ScrobbleServiceRoute = "integrations_settings/scrobble/{site}"

fun scrobbleServiceRoute(site: ScrobbleSite) = "integrations_settings/scrobble/${site.name}"

@Composable
fun ScrobbleServiceScreen(
    navController: NavController,
    site: ScrobbleSite,
    viewModel: IntegrationsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val overlay = LocalZoomOutOverlayState.current
    val accounts by viewModel.accounts.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val sendResult by viewModel.sendResult.collectAsState()
    val (nowPlaying, onNowPlayingChange) = rememberPreference(LastFMUseNowPlaying, defaultValue = true)
    val (sendLikes, onSendLikesChange) = rememberPreference(LastFMUseSendLikes, defaultValue = false)

    LaunchedEffect(sendResult) {
        sendResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSendResult()
        }
    }

    fun signIn() {
        var sheet: PopupSheetHandle? = null
        sheet = overlay.showWithHandle {
            ScrobbleSignInContent(
                site = site,
                viewModel = viewModel,
                onSignedIn = { name ->
                    sheet?.dismiss()
                    Toast.makeText(context, context.getString(R.string.scrobble_signed_in, site.label, name), Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    SubSettingsScaffold(
        title = site.label,
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.integrations),
    ) {
        IntegrationPageSubtitle(stringResource(R.string.scrobble_page_subtitle))
        ScrobbleAccountGroup(
            site = site,
            username = accounts[site],
            onSignIn = ::signIn,
            onSignOut = { viewModel.signOut(site) },
        )
        Spacer(Modifier.height(16.dp))

        // shared by both services, so it reads the same on either page
        val nowPlayingTitle = stringResource(R.string.scrobble_now_playing)
        val sendLikesTitle = stringResource(R.string.scrobble_send_likes)
        SettingsGroup(
            title = stringResource(R.string.scrobble_while_listening),
            style = SettingsGroupStyle.Grouped,
            items = listOfNotNull(
                SettingsItem(
                    title = { Text(nowPlayingTitle) },
                    highlightKey = "Now Playing",
                    searchText = nowPlayingTitle,
                    descriptionText = stringResource(R.string.scrobble_now_playing_desc),
                    icon = painterResource(R.drawable.music_note),
                    iconShape = CircleShape,
                    onClick = { onNowPlayingChange(!nowPlaying) },
                    trailingContent = { AppSwitch(checked = nowPlaying, onCheckedChange = onNowPlayingChange) },
                ),
                SettingsItem(
                    title = { Text(sendLikesTitle) },
                    highlightKey = "Send likes",
                    searchText = sendLikesTitle,
                    descriptionText = stringResource(R.string.scrobble_send_likes_desc),
                    icon = painterResource(R.drawable.favorite),
                    iconShape = CircleShape,
                    onClick = { onSendLikesChange(!sendLikes) },
                    trailingContent = { AppSwitch(checked = sendLikes, onCheckedChange = onSendLikesChange) },
                ),
                if (pending > 0) {
                    val waiting = pluralStringResource(R.plurals.scrobble_plays_waiting, pending, pending)
                    SettingsItem(
                        title = { Text(waiting) },
                        descriptionText = stringResource(R.string.scrobble_plays_waiting_desc),
                        icon = painterResource(R.drawable.cloud),
                        iconShape = CircleShape,
                        onClick = viewModel::sendNow,
                    )
                } else null,
            ),
        )

        Spacer(Modifier.height(140.dp))
    }
}

/** sign in, or once signed in: scrobbling on or paused, the profile, and sign out */
@Composable
private fun ScrobbleAccountGroup(
    site: ScrobbleSite,
    username: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val (enabled, onEnabledChange) = rememberPreference(site.enabledKey, defaultValue = true)
    val uriHandler = LocalUriHandler.current
    val items = if (username == null) {
        listOf(
            SettingsItem(
                title = { Text(stringResource(R.string.scrobble_sign_in_title, site.label)) },
                highlightKey = site.label,
                descriptionText = stringResource(
                    if (site.api.isConfigured) R.string.scrobble_sign_in_desc else R.string.scrobble_not_set_up,
                ),
                icon = painterResource(R.drawable.login),
                iconShape = CircleShape,
                enabled = site.api.isConfigured,
                onClick = onSignIn,
            ),
        )
    } else {
        listOf(
            SettingsItem(
                title = { Text(stringResource(R.string.scrobble_to, site.label)) },
                highlightKey = site.label,
                descriptionText = stringResource(
                    if (enabled) R.string.scrobble_signed_in_as else R.string.scrobble_paused_signed_in_as,
                    username,
                ),
                icon = painterResource(R.drawable.music_history),
                iconShape = CircleShape,
                onClick = { onEnabledChange(!enabled) },
                trailingContent = { AppSwitch(checked = enabled, onCheckedChange = onEnabledChange) },
            ),
            SettingsItem(
                title = { Text(stringResource(R.string.scrobble_open_profile)) },
                descriptionText = stringResource(R.string.scrobble_open_profile_desc, site.label),
                icon = painterResource(R.drawable.web_link),
                iconShape = CircleShape,
                onClick = { uriHandler.openUri(site.profileUrl(username)) },
            ),
            SettingsItem(
                title = { Text(stringResource(R.string.scrobble_sign_out)) },
                descriptionText = stringResource(R.string.scrobble_sign_out_desc, site.label),
                icon = painterResource(R.drawable.logout),
                iconShape = CircleShape,
                onClick = onSignOut,
            ),
        )
    }
    SettingsGroup(title = stringResource(R.string.scrobble_account), style = SettingsGroupStyle.Grouped, items = items)
}
