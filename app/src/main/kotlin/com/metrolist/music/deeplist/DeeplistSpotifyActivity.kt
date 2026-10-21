package com.metrolist.music.deeplist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.ui.menu.DeeplistPlaylistImportDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class DeeplistSpotifyActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase
    private val settings by lazy { getSharedPreferences("deeplist-spotify-setup", MODE_PRIVATE) }
    private val redirect get() = "${BuildConfig.APPLICATION_ID}.spotify://callback"
    private var busy by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var playlistText by mutableStateOf("")
    private var clientId by mutableStateOf("")
    private var payload by mutableStateOf<String?>(null)
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clientId = settings.getString("client", "").orEmpty()
        playlistText = intent.getStringExtra("playlist").orEmpty().ifBlank { settings.getString("playlist", "").orEmpty() }
        handleCallback(intent)
        setContent { MaterialTheme { CompositionLocalProvider(LocalDatabase provides database) {
            if (payload != null) DeeplistPlaylistImportDialog(initial = payload!!, onDismiss = { finish() }, onSaved = { finish() })
            else Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.deeplist_connect_spotify), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.deeplist_spotify_limits))
                OutlinedTextField(playlistText, { playlistText = it.take(1000) }, label = { Text(stringResource(R.string.deeplist_spotify_link)) })
                OutlinedTextField(clientId, { clientId = it.trim().take(100) }, label = { Text(stringResource(R.string.deeplist_spotify_client)) })
                Text(stringResource(R.string.deeplist_spotify_redirect, redirect), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { authorize() }, enabled = !busy && clientId.matches(Regex("[A-Za-z0-9]{32}")) && playlistId(playlistText) != null) { Text(stringResource(R.string.deeplist_spotify_authorize)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.deeplist_spotify_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { finish() }) { Text(stringResource(android.R.string.cancel)) }
            } }
        } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleCallback(intent) }
    private fun random(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun playlistId(input: String): String? = runCatching { Uri.parse(input).takeIf { it.scheme == "https" && it.host == "open.spotify.com" }?.pathSegments?.let { segments -> segments.getOrNull(segments.indexOf("playlist") + 1) }?.takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) } }.getOrNull()
    private fun authorize() {
        val id = playlistId(playlistText) ?: return
        val verifier = random(); val state = random()
        settings.edit().putString("client", clientId).putString("playlist", playlistText).putString("id", id).putString("verifier", verifier).putString("state", state).putLong("created", System.currentTimeMillis()).apply()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = Uri.parse("https://accounts.spotify.com/authorize").buildUpon().appendQueryParameter("client_id", clientId).appendQueryParameter("response_type", "code").appendQueryParameter("redirect_uri", redirect).appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative").appendQueryParameter("state", state).appendQueryParameter("code_challenge_method", "S256").appendQueryParameter("code_challenge", challenge).build()
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }.onFailure { failed = true }
    }
    private fun handleCallback(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "${BuildConfig.APPLICATION_ID}.spotify" || uri.host != "callback") return
        intent.data = null
        val expected = settings.getString("state", null)
        val verifier = settings.getString("verifier", null)
        val code = uri.getQueryParameter("code")
        val id = settings.getString("id", null)
        val valid = expected != null && verifier != null && uri.getQueryParameter("state") == expected && code != null && id != null && System.currentTimeMillis() - settings.getLong("created", 0) in 0..600_000
        settings.edit().remove("state").remove("verifier").remove("created").apply()
        if (!valid) { failed = true; return }
        busy = true; failed = false
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val body = FormBody.Builder().add("grant_type", "authorization_code").add("code", code!!).add("redirect_uri", redirect).add("client_id", settings.getString("client", "").orEmpty()).add("code_verifier", verifier!!).build()
                val token = client.newCall(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()).execute().use { response -> check(response.isSuccessful); JSONObject(response.body!!.string()).getString("access_token") }
                fun get(url: String): JSONObject {
                    require(Uri.parse(url).let { it.scheme == "https" && it.host == "api.spotify.com" })
                    return client.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { response -> check(response.isSuccessful); JSONObject(response.body!!.string()) }
                }
                val info = get("https://api.spotify.com/v1/playlists/$id?fields=name")
                val rows = mutableListOf<DeeplistImportTrack>(); var next: String? = "https://api.spotify.com/v1/playlists/$id/items?limit=50"
                while (next != null && rows.size < 2000) {
                    val page = get(next); val items = page.getJSONArray("items")
                    for (i in 0 until items.length()) {
                        val wrapper = items.optJSONObject(i) ?: continue
                        val item = wrapper.optJSONObject("item") ?: wrapper.optJSONObject("track") ?: continue
                        if (item.optString("type") != "track") continue
                        val artists = item.optJSONArray("artists")
                        val artist = (0 until (artists?.length() ?: 0)).map { artists!!.getJSONObject(it).optString("name") }.joinToString(", ")
                        rows.add(DeeplistImportTrack(item.getString("name"), artist, spotifyId = item.getString("id")))
                    }
                    next = page.optString("next").takeIf { it.startsWith("https://api.spotify.com/") }
                }
                check(rows.isNotEmpty())
                DeeplistPlaylistTransfer.encode(DeeplistSharedPlaylist(info.optString("name", "Spotify"), rows.take(2000)))
            } }.onSuccess { payload = it }.onFailure { failed = true }
            busy = false
        }
    }
}
