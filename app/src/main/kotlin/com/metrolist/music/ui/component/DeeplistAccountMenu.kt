package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.metrolist.music.LocalDatabase
import com.metrolist.music.deeplist.DeeplistCloudSync
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.deeplist.DeeplistAccount
import com.metrolist.music.ui.menu.DeeplistPlaylistImportDialog
import kotlinx.coroutines.launch

@Composable
internal fun DeeplistAccountMenu(navController: NavController, onClose: () -> Unit) {
    val state by DeeplistAccount.state.collectAsStateWithLifecycle()
    val user = state.user
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val database = LocalDatabase.current
    val cloud by DeeplistCloudSync.state.collectAsStateWithLifecycle()
    var importing by remember { mutableStateOf(false) }
    if (importing) DeeplistPlaylistImportDialog(onDismiss = { importing = false })
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(user?.optString("name").orEmpty().ifBlank { stringResource(R.string.dp_profile) }, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.close), stringResource(R.string.close)) }
        }
        Text(user?.optString("username")?.let { "@$it" }.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        if (user?.optString("provider") != "google") {
            Button(onClick = { scope.launch { DeeplistAccount.google() } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dp_connect_google))
            }
            Text(stringResource(R.string.dp_connect_google_hint), style = MaterialTheme.typography.bodySmall)
        }
        if (user?.optString("provider") == "google") {
            Text(stringResource(R.string.dp_cloud_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { scope.launch { DeeplistCloudSync.sync(context,database) } }, enabled = !cloud.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if(cloud.busy) R.string.dp_cloud_syncing else R.string.dp_cloud_sync)) }
            if(cloud.userId == user.optString("id")) {
                if(cloud.lastSync > 0) Text(stringResource(R.string.dp_cloud_saved, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(cloud.lastSync))), style=MaterialTheme.typography.bodySmall)
                cloud.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }
        }
        OutlinedButton(onClick = { onClose(); navController.navigate("deeplist/profile") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dp_profile)) }
        OutlinedButton(onClick = { importing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dp_import_playlist_file)) }
        OutlinedButton(onClick = { onClose(); navController.navigate("sound_capsule") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dp_sound_capsule)) }
        OutlinedButton(onClick = { onClose(); navController.navigate("settings/integrations") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.integrations)) }
        TextButton(onClick = { onClose(); navController.navigate("settings") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings)) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
