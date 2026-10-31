package com.metrolist.music.deeplist

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import android.content.Context
import android.os.SystemClock
import com.metrolist.music.constants.*
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

internal data class DeeplistListenRequest(val id: String, val name: String, val expires: Long)
internal data class DeeplistListenState(val userId: String = "", val connected: Boolean = false, val requests: List<DeeplistListenRequest> = emptyList(), val error: String? = null)
internal object DeeplistListenInbox {
    private val mutable = MutableStateFlow(DeeplistListenState())
    val state = mutable.asStateFlow()
    private val pollMutex = Mutex()
    private var binding = ""
    private var lastPoll = 0L
    private var notified = ""
    private fun notification(context: Context, requests: List<DeeplistListenRequest>, notify: Boolean) {
        val manager=context.getSystemService(NotificationManager::class.java)
        val request=requests.firstOrNull()
        if(request == null) { manager.cancel(7304);notified="";return }
        if(!notify || notified == request.id) return
        if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.createNotificationChannel(NotificationChannel("deeplist_requests",context.getString(R.string.dp_listen_request_title),NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,7304,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(7304,NotificationCompat.Builder(context,"deeplist_requests").setSmallIcon(R.drawable.small_icon).setContentTitle(context.getString(R.string.dp_listen_request_title)).setContentText(context.getString(R.string.dp_listen_request_from,request.name)).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter((request.expires-System.currentTimeMillis()).coerceAtLeast(1)).build())
        notified=request.id
    }
    suspend fun poll(context: Context, force: Boolean = false, notify: Boolean = false) {
        if (!pollMutex.tryLock()) return
        try {
            val user = DeeplistAccount.state.value.user
            if (user == null || user.optBoolean("blocked")) { binding = ""; mutable.value = DeeplistListenState(); notification(context,emptyList(),false);return }
            val owner = user.optString("id")
            if (mutable.value.userId != owner) { binding = ""; mutable.value = DeeplistListenState(userId = owner);notification(context,emptyList(),false) }
            val prefs = context.dataStore.data.first()
            val token = prefs[DeeplistTokenKey].orEmpty()
            if (prefs[DeeplistSyncEnabledKey] != true || prefs[DeeplistWebsiteKey].orEmpty().trimEnd('/') != DeeplistAccount.WEBSITE || !token.matches(Regex("[a-f0-9]{64}"))) {
                binding = ""; mutable.value = DeeplistListenState(); notification(context,emptyList(),false);return
            }
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastPoll < 9_000 && binding == user.optString("id") + token) return
            lastPoll = now
            if (binding != user.optString("id") + token) {
                DeeplistAccount.authenticatedRequest("listen/connect", "POST", JSONObject().put("token", token), expectedUserId = owner)
                binding = user.optString("id") + token
            }
            val result = DeeplistAccount.authenticatedRequest("listen/inbox", expectedUserId = owner).getJSONArray("requests")
            val requests = List(result.length()) { i -> result.getJSONObject(i).let { DeeplistListenRequest(it.getString("id"), it.getString("label"), it.getLong("expires")) } }
            if (DeeplistAccount.state.value.user?.optString("id") == owner) { mutable.value = DeeplistListenState(owner, true, requests);notification(context,requests,notify) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message) }
        finally { pollMutex.unlock() }
    }
    suspend fun respond(request: DeeplistListenRequest, action: String) {
        require(mutable.value.userId == DeeplistAccount.state.value.user?.optString("id"))
        DeeplistAccount.authenticatedRequest("listen/respond", "POST", JSONObject().put("id", request.id).put("action", action), expectedUserId = mutable.value.userId)
        mutable.value = mutable.value.copy(requests = mutable.value.requests.filterNot { it.id == request.id }, error = null)
    }
}
