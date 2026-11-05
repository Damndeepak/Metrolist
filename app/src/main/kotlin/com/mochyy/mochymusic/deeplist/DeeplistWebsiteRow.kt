package com.mochyy.mochymusic.deeplist

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mochyy.mochymusic.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DeeplistWebsiteRow() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val database = com.mochyy.mochymusic.LocalDatabase.current
    val cloud by DeeplistCloudSync.state.collectAsState()
    val account by DeeplistAccount.state.collectAsState()
    val inbox by DeeplistListenInbox.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var responding by remember { mutableStateOf(false) }
    LaunchedEffect(owner, account.user?.optString("id")) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            DeeplistAccount.refresh(open = true)
            while (true) {
                DeeplistListenInbox.poll(context)
                delay(10_000L)
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF111111), RoundedCornerShape(28.dp))
        .clickable { open = true }.padding(22.dp)) {
        Text(stringResource(R.string.deeplist_website_title), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(account.user?.optString("name")?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.deeplist_website_sign_in), color = Color(0xFF999999), fontSize = 14.sp)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.deeplist_website_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.deeplist_website_description))
                if (account.user == null) {
                    TextButton(enabled = !account.busy, onClick = { scope.launch { DeeplistAccount.google() } }) {
                        Text(stringResource(R.string.deeplist_google_sign_in))
                    }
                    TextButton(enabled = !account.busy, onClick = { scope.launch { DeeplistAccount.guest() } }) {
                        Text(stringResource(R.string.deeplist_guest_sign_in))
                    }
                } else {
                    Text(account.user!!.optString("name"))
                    if (account.user!!.optString("provider") == "google") {
                        TextButton(enabled = !cloud.busy && !account.busy, onClick = {
                            scope.launch { DeeplistCloudSync.sync(context, database) }
                        }) { Text(stringResource(if (cloud.busy) R.string.deeplist_syncing else R.string.deeplist_sync_library)) }
                        cloud.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(enabled = !account.busy, onClick = { scope.launch { DeeplistAccount.refresh(open = true) } }) {
                        Text(stringResource(R.string.deeplist_refresh_account))
                    }
                    TextButton(enabled = !account.busy, onClick = { scope.launch { DeeplistAccount.signOut() } }) {
                        Text(stringResource(R.string.deeplist_sign_out))
                    }
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DeeplistAccount.WEBSITE)))
                }) { Text(stringResource(R.string.deeplist_open_website)) }
                for (request in inbox.requests.take(10)) {
                    Text(stringResource(R.string.dp_listen_request_from, request.name))
                    fun respond(action: String) { scope.launch {
                        responding = true; failure = null
                        try { DeeplistListenInbox.respond(request, action) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { failure = context.getString(R.string.deeplist_request_failed) }
                        finally { responding = false }
                    } }
                    Row {
                        TextButton(enabled = !responding, onClick = { respond("accept") }) { Text(stringResource(R.string.deeplist_accept)) }
                        TextButton(enabled = !responding, onClick = { respond("reject") }) { Text(stringResource(R.string.deeplist_reject)) }
                    }
                    TextButton(enabled = !responding, onClick = { respond("mute") }) { Text(stringResource(R.string.deeplist_mute)) }
                }
                (failure ?: account.error ?: inbox.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.deeplist_done)) } },
    )
}
