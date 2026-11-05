// last.fm or libre.fm sign-in right inside mochy, the password goes to the service once and only the key it sends back is kept

package com.mochyy.mochymusic.ui.screens.settings.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetTextField
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.IntegrationsViewModel
import com.music.lastfm.AudioscrobblerException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

private val ProblemColor = Color(0xFFFF8A80)

@Composable
fun ScrobbleSignInContent(
    site: ScrobbleSite,
    viewModel: IntegrationsViewModel,
    onSignedIn: (username: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    var username by rememberSaveable { mutableStateOf("") }
    // plain remember on purpose, a saveable password would be written into the saved screen state
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val canSubmit = username.isNotBlank() && password.isNotEmpty() && !busy

    fun submit() {
        if (!canSubmit) return
        keyboard?.hide()
        busy = true
        problem = null
        scope.launch {
            try {
                onSignedIn(viewModel.signIn(site, username, password))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioscrobblerException) {
                Timber.tag("Scrobble").w(e, "${site.label} sign-in turned down")
                problem = if (e.isWrongLogin) {
                    context.getString(R.string.scrobble_sign_in_wrong)
                } else {
                    context.getString(R.string.scrobble_sign_in_busy, site.label)
                }
            } catch (e: Exception) {
                problem = context.getString(R.string.scrobble_sign_in_offline, site.label)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            stringResource(R.string.scrobble_sign_in_title, site.label),
            color = SearchColors.Primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.scrobble_sign_in_body, site.label),
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(22.dp))

        SheetLabel(stringResource(R.string.username))
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = username,
            onValueChange = {
                username = it
                problem = null
            },
            placeholder = stringResource(R.string.scrobble_username_hint, site.label),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.semantics { contentType = ContentType.Username },
        )
        Spacer(Modifier.height(18.dp))

        SheetLabel(stringResource(R.string.password))
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = password,
            onValueChange = {
                password = it
                problem = null
            },
            placeholder = stringResource(R.string.scrobble_password_hint, site.label),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.semantics { contentType = ContentType.Password },
        )

        problem?.let {
            Text(
                it,
                color = ProblemColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
        }
        Spacer(Modifier.height(22.dp))

        SheetPillButton(
            text = stringResource(if (busy) R.string.scrobble_signing_in else R.string.scrobble_sign_in_button),
            onClick = ::submit,
            enabled = canSubmit,
        )
        Text(
            stringResource(R.string.scrobble_sign_up, site.label),
            color = SearchColors.Secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clickable { uriHandler.openUri(site.signUpUrl) }
                .padding(vertical = 10.dp),
        )
    }
}
