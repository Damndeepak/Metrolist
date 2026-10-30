package com.metrolist.music.deeplist

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.DeeplistTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@Composable
internal fun DeeplistAccountGate(connection: PlayerConnection?, content: @Composable () -> Unit) {
    if (BuildConfig.APPLICATION_ID != "com.deepu.wordstorm") { content(); return }
    val context = LocalContext.current
    remember { DeeplistAccount.initialize(context); true }
    val state by DeeplistAccount.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var first = true
            while (true) { DeeplistAccount.refresh(first); first = false; delay(300_000) }
        }
    }
    LaunchedEffect(state.user?.optBoolean("blocked"),state.user?.optString("id"),connection) {
        if (!DeeplistAccount.permitted) connection?.player?.pause()
    }
    if (state.user != null && !state.user!!.optBoolean("blocked")) content()
    else MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFFD8F3AB), background=Color(0xFF090C0B), surface=Color(0xFF101813))) {
        DeeplistWelcome(blocked = state.user?.optBoolean("blocked") == true)
    }
}

@Composable
private fun DeeplistWelcome(blocked: Boolean) {
    val state by DeeplistAccount.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF183427),Color(0xFF090C0B),Color(0xFF090C0B)))).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("deeplist",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black)
            Spacer(Modifier.height(60.dp))
            Text(stringResource(if(blocked) R.string.dp_blocked_title else R.string.dp_welcome_title),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
            Text(stringResource(if(blocked) R.string.dp_blocked_body else R.string.dp_welcome_body),color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            if(blocked) {
                Button(onClick={scope.launch { DeeplistAccount.refresh() }},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.dp_check_access)) }
                TextButton(onClick={uri.openUri("mailto:gandhideepak6376@gmail.com")}) { Text(stringResource(R.string.dp_contact_admin)) }
            } else {
                Button(onClick={scope.launch { DeeplistAccount.google() }},enabled=!state.busy,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(28.dp)) { Text(stringResource(R.string.dp_google)) }
                OutlinedButton(onClick={scope.launch { DeeplistAccount.guest() }},enabled=!state.busy,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(28.dp)) { Text(stringResource(R.string.dp_guest)) }
            }
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(30.dp))
            Text(stringResource(R.string.dp_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={uri.openUri(DeeplistAccount.WEBSITE+"/app-privacy.html")}) { Text(stringResource(R.string.dp_privacy)) }
        }
    }
}

@Composable
internal fun DeeplistProfileScreen(navController: NavController) {
    val state by DeeplistAccount.state.collectAsStateWithLifecycle()
    val user = state.user ?: return
    val collections=user.optJSONObject("collections")?:JSONObject()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val database = LocalDatabase.current
    var edit by remember { mutableStateOf(false) }
    var moments by remember { mutableStateOf(false) }
    var publish by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri != null) scope.launch {
            photoError=false
            runCatching { withContext(Dispatchers.IO) {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
                require(bounds.outWidth>0 && bounds.outHeight>0)
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1024) sample*=2
                val bitmap=context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: error("Invalid photo")
                try { ByteArrayOutputStream().use { bytes -> bitmap.compress(Bitmap.CompressFormat.JPEG,85,bytes); bytes.toByteArray() } } finally { bitmap.recycle() }
            } }.onSuccess { DeeplistAccount.avatar(it) }.onFailure { photoError=true }
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick={navController.popBackStack()}) { Text(stringResource(R.string.dp_back)) }
        Box(Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
            if(user.optString("avatar").isNotBlank()) AsyncImage(user.optString("avatar"),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Text(user.optString("name").take(1).uppercase(),style=MaterialTheme.typography.displayMedium)
        }
        Text(user.optString("name"),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text("@"+user.optString("username"),color=MaterialTheme.colorScheme.onSurfaceVariant)
        user.optString("bio").takeIf { it.isNotBlank() }?.let { Text(it) }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick={edit=true},enabled=!state.busy) { Text(stringResource(R.string.dp_edit_profile)) }
            OutlinedButton(onClick={
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,DeeplistAccount.WEBSITE+"/app-profile.html?user="+Uri.encode(user.optString("username"))) },null))
            }) { Text(stringResource(R.string.dp_share_profile)) }
        }
        TextButton(onClick={photo.launch("image/*")},enabled=!state.busy) { Text(stringResource(R.string.dp_photo)) }
        HorizontalDivider()
        TextButton(onClick={moments=true}) { Text(stringResource(R.string.dp_moments)) }
        TextButton(onClick={publish=true},enabled=!state.busy) { Text(stringResource(R.string.dp_public_collections)) }
        Text(stringResource(R.string.dp_history_private),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        for(type in listOf("playlists","favourites")) {
            val rows=collections.optJSONArray(type)?:JSONArray()
            if(rows.length()>0) {
                Text(stringResource(if(type=="playlists") R.string.dp_playlists else R.string.dp_favourites),style=MaterialTheme.typography.titleMedium)
                for(i in 0 until rows.length())Text(rows.getJSONObject(i).optString("title"),style=MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
        if(user.optBoolean("admin")) Button(onClick={uriHandler.openUri(DeeplistAccount.WEBSITE+"/app-admin.html")}) { Text(stringResource(R.string.dp_admin)) }
        if(user.optString("provider")=="guest") TextButton(onClick={scope.launch { DeeplistAccount.google() }},enabled=!state.busy) { Text(stringResource(R.string.dp_google)) }
        TextButton(onClick={uriHandler.openUri(DeeplistAccount.WEBSITE+"/app-privacy.html")}) { Text(stringResource(R.string.dp_privacy)) }
        TextButton(onClick={scope.launch { DeeplistAccount.signOut() }},enabled=!state.busy) { Text(stringResource(R.string.dp_sign_out)) }
        if(!user.optBoolean("admin")) TextButton(onClick={deleting=true},enabled=!state.busy) { Text(stringResource(R.string.dp_delete_account),color=MaterialTheme.colorScheme.error) }
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(photoError)Text(stringResource(R.string.dp_photo_error),color=MaterialTheme.colorScheme.error)
    }
    if(moments)DeeplistTools(initialTab=2,onDismiss={moments=false})
    if(edit) {
        var name by remember { mutableStateOf(user.optString("name")) }
        var username by remember { mutableStateOf(user.optString("username")) }
        var bio by remember { mutableStateOf(user.optString("bio")) }
        AlertDialog(onDismissRequest={edit=false},title={Text(stringResource(R.string.dp_edit_profile))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it.take(60)},label={Text(stringResource(R.string.dp_name))},singleLine=true)
            OutlinedTextField(username,{username=it.lowercase().take(30)},label={Text(stringResource(R.string.dp_username))},singleLine=true)
            OutlinedTextField(bio,{bio=it.take(160)},label={Text(stringResource(R.string.dp_bio))})
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }},confirmButton={TextButton(onClick={scope.launch { DeeplistAccount.edit(name,username,bio);if(DeeplistAccount.state.value.error==null)edit=false }},enabled=!state.busy&&name.isNotBlank()&&username.matches(Regex("[a-z0-9_]{3,30}"))) {Text(stringResource(R.string.dp_save))}})
    }
    if(publish) {
        var rows by remember { mutableStateOf<List<Triple<String,String,String>>?>(null) }
        var selected by remember { mutableStateOf(buildSet<String> { for(type in listOf("playlists","favourites")){ val items=collections.optJSONArray(type)?:JSONArray();for(i in 0 until items.length())add(type+":"+items.getJSONObject(i).getString("id")) } }) }
        LaunchedEffect(Unit) { rows=withContext(Dispatchers.IO) {
            database.playlistEntitiesByNameAsc().filter { it.browseId!=null }.take(30).map { Triple("playlists",it.browseId!!,it.name) } + database.likedSongsByCreateDateDesc(100,0).map { Triple("favourites",it.song.id,it.song.title) }
        } }
        AlertDialog(onDismissRequest={publish=false},title={Text(stringResource(R.string.dp_public_collections))},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.dp_publish_hint),style=MaterialTheme.typography.bodySmall)
            if(rows==null)CircularProgressIndicator() else rows!!.forEach { (type,id,title) ->
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(type+":"+id in selected,{checked->selected=if(checked)selected+(type+":"+id) else selected-(type+":"+id)});Text(title,modifier=Modifier.weight(1f)) }
            }
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }},confirmButton={TextButton(onClick={scope.launch {
            val value=JSONObject();for(type in listOf("playlists","favourites"))value.put(type,JSONArray().apply { rows.orEmpty().filter { it.first==type && type+":"+it.second in selected }.forEach { (_,id,title)->put(JSONObject().put("id",id).put("title",title)) } })
            DeeplistAccount.publish(value);if(DeeplistAccount.state.value.error==null)publish=false
        }},enabled=!state.busy&&rows!=null){Text(stringResource(R.string.dp_publish))}})
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text(stringResource(R.string.dp_delete_account))},text={Text(stringResource(R.string.dp_delete_body))},confirmButton={TextButton(onClick={scope.launch { DeeplistAccount.delete();deleting=false }},enabled=!state.busy){Text(stringResource(R.string.dp_delete_account))}},dismissButton={TextButton(onClick={deleting=false}){Text(stringResource(R.string.dp_back))}})
}
