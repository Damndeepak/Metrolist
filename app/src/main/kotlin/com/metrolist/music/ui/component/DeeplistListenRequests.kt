package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.deeplist.*
import kotlinx.coroutines.launch

@Composable
internal fun DeeplistListenRequestPrompt() {
    val state by DeeplistListenInbox.state.collectAsStateWithLifecycle()
    val account by DeeplistAccount.state.collectAsStateWithLifecycle()
    if (state.userId != account.user?.optString("id")) return
    val skipped = remember { mutableStateListOf<String>() }
    val request = state.requests.firstOrNull { it.id !in skipped && it.expires > System.currentTimeMillis() } ?: return
    AlertDialog(onDismissRequest = { skipped.add(request.id) }, title = { Text(stringResource(R.string.dp_listen_request_title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dp_listen_request_from, request.name))
            DeeplistRequestActions(request)
        }
    }, confirmButton = {})
}

@Composable
internal fun DeeplistListenTogetherScreen(navController: NavController, onRooms: () -> Unit) {
    val state by DeeplistListenInbox.state.collectAsStateWithLifecycle()
    val account by DeeplistAccount.state.collectAsStateWithLifecycle()
    val requests = if (state.userId == account.user?.optString("id")) state.requests.filter { it.expires > System.currentTimeMillis() } else emptyList()
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Vertical)), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.dp_back)) }
            Text(stringResource(R.string.together), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.dp_listen_request_hint))
            if (!state.connected) TextButton(onClick = { navController.navigate("settings/integrations") }) { Text(stringResource(R.string.integrations)) }
        }
        if (requests.isEmpty()) item { Text(stringResource(R.string.dp_listen_no_requests)) }
        items(requests, key = { it.id }) { request ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dp_listen_request_from, request.name))
                DeeplistRequestActions(request)
            } }
        }
        item {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onRooms) { Text(stringResource(R.string.dp_listen_room_codes)) }
        }
    }
}

@Composable
private fun DeeplistRequestActions(request: DeeplistListenRequest) {
    val scope = rememberCoroutineScope()
    var busy by remember(request.id) { mutableStateOf(false) }
    var failure by remember(request.id) { mutableStateOf<String?>(null) }
    fun respond(action: String) { scope.launch {
        busy = true
        try { DeeplistListenInbox.respond(request, action) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { failure = e.message }
        finally { busy = false }
    } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { respond("accept") }, enabled = !busy) { Text(stringResource(R.string.dp_listen_accept)) }
        OutlinedButton(onClick = { respond("reject") }, enabled = !busy) { Text(stringResource(R.string.dp_listen_reject)) }
    }
    TextButton(onClick = { respond("mute") }, enabled = !busy) { Text(stringResource(R.string.dp_listen_mute)) }
    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
