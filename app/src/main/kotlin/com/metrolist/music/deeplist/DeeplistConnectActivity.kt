package com.metrolist.music.deeplist

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import com.metrolist.music.R
import com.metrolist.music.constants.DeeplistSyncEnabledKey
import com.metrolist.music.constants.DeeplistTokenKey
import com.metrolist.music.constants.DeeplistWebsiteKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DeeplistConnectActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.data
        val uri = runCatching { java.net.URI(link?.getQueryParameter("website").orEmpty()) }.getOrNull()
        val code = link?.getQueryParameter("code").orEmpty()
        if (link?.scheme != "deeplist" || link.host != "connect" || uri?.scheme != "https" ||
            uri.host.isNullOrBlank() || uri.userInfo != null || uri.port !in listOf(-1, 443) ||
            !code.matches(Regex("[a-f0-9]{64}"))) { finish(); return }
        val origin = "https://${uri.host}"
        AlertDialog.Builder(this).setTitle(R.string.deeplist_connect_approve)
            .setMessage("$origin\n\n${getString(R.string.deeplist_connect_hint)}")
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .setPositiveButton(android.R.string.ok) { _, _ -> scope.launch {
                val token = withContext(Dispatchers.IO) {
                    runCatching {
                        val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
                        val request = Request.Builder().url("$origin/api/deeplist/claim")
                            .post(JSONObject().put("code", code).toString().toRequestBody("application/json".toMediaType())).build()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) null else JSONObject(response.body.string()).optString("token").takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                        }
                    }.getOrNull()
                }
                if (token != null) {
                    dataStore.edit { it[DeeplistWebsiteKey] = origin; it[DeeplistTokenKey] = token; it[DeeplistSyncEnabledKey] = true }
                    startActivity(android.content.Intent(this@DeeplistConnectActivity, com.metrolist.music.MainActivity::class.java))
                }
                else Toast.makeText(this@DeeplistConnectActivity, R.string.deeplist_connect_failed, Toast.LENGTH_LONG).show()
                finish()
            } }.show()
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
