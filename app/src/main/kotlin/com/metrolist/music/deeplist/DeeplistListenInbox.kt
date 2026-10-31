package com.metrolist.music.deeplist

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
    suspend fun poll(context: Context, force: Boolean = false) {
        if (!pollMutex.tryLock()) return
        try {
            val user = DeeplistAccount.state.value.user
            if (user == null || user.optBoolean("blocked")) { binding = ""; mutable.value = DeeplistListenState(); return }
            val owner = user.optString("id")
            if (mutable.value.userId != owner) { binding = ""; mutable.value = DeeplistListenState(userId = owner) }
            val prefs = context.dataStore.data.first()
            val token = prefs[DeeplistTokenKey].orEmpty()
            if (prefs[DeeplistSyncEnabledKey] != true || prefs[DeeplistWebsiteKey].orEmpty().trimEnd('/') != DeeplistAccount.WEBSITE || !token.matches(Regex("[a-f0-9]{64}"))) {
                binding = ""; mutable.value = DeeplistListenState(); return
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
            if (DeeplistAccount.state.value.user?.optString("id") == owner) mutable.value = DeeplistListenState(owner, true, requests)
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
