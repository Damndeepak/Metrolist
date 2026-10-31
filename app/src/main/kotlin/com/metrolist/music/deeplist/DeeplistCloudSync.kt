package com.metrolist.music.deeplist

import android.content.Context
import android.util.AtomicFile
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.lyrics.DeeplistSetup
import com.metrolist.music.utils.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal data class DeeplistCloudState(val userId: String = "", val busy: Boolean = false, val lastSync: Long = 0, val error: String? = null)
internal object DeeplistCloudSync {
    private val mutable = MutableStateFlow(DeeplistCloudState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    suspend fun sync(context: Context, database: MusicDatabase) {
        if (!mutex.tryLock()) return
        try {
            val user=DeeplistAccount.state.value.user ?: return
            if(user.optString("provider") != "google" || user.optBoolean("blocked")) return
            val owner=user.getString("id")
            require(owner.matches(Regex("[a-f0-9-]{36}")))
            mutable.value=mutable.value.takeIf { it.userId == owner }?.copy(busy=true,error=null) ?: DeeplistCloudState(owner,true)
            withContext(Dispatchers.IO) {
                val ownership=File(context.noBackupFilesDir,"deeplist-cloud-owner")
                if(ownership.exists() && ownership.readText() != owner) error("This device’s library belongs to another Google account. Sign back into that account before syncing.")
                if(!ownership.exists()) ownership.writeText(owner)
                val file=AtomicFile(File(context.noBackupFilesDir,"deeplist-cloud-$owner.json"))
                val baseline=try { file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) } } catch(_: java.io.FileNotFoundException) { null }
                val remote=DeeplistAccount.authenticatedRequest("cloud")
                check(DeeplistAccount.state.value.user?.optString("id") == owner)
                val previous=baseline?.optJSONObject("data")
                val preferences=context.dataStore.data.first()
                var local: JSONObject?=null
                database.withTransaction { local=DeeplistCloudData.snapshot(this,preferences) }
                val cloud=remote.optJSONObject("data")
                val merged=if(cloud == null) local!! else mergeCloud(previous,local,cloud,baseline == null) as JSONObject
                // Validate settings before publishing or applying a snapshot from another device.
                DeeplistCloudData.settings(merged)
                val bytes=merged.toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= 15*1024*1024) { "Account data exceeds the 15 MB sync limit. Your local data is safe." }
                val revision=if(cloudEqual(merged,cloud)) remote.getLong("revision") else DeeplistAccount.authenticatedRequest("cloud","PUT",JSONObject().put("revision",remote.getLong("revision")).put("data",merged)).getLong("revision")
                check(DeeplistAccount.state.value.user?.optString("id") == owner)
                // Rebase edits made during the network request rather than replacing a live library.
                val latestPrefs=context.dataStore.data.first()
                var applied=merged
                database.withTransaction {
                    check(DeeplistAccount.state.value.user?.optString("id") == owner)
                    val latest=DeeplistCloudData.snapshot(this,latestPrefs)
                    applied=mergeCloud(local,latest,merged) as JSONObject
                    DeeplistCloudData.settings(applied)
                    DeeplistCloudData.apply(this,latest,applied)
                }
                context.dataStore.edit { prefs ->
                    check(DeeplistAccount.state.value.user?.optString("id") == owner)
                    val current=DeeplistCloudData.snapshotSettings(prefs)
                    val finalSettings=mergeCloud(DeeplistCloudData.snapshotSettings(latestPrefs),current,applied.optJSONObject("settings")) as JSONObject
                    DeeplistSetup.apply(prefs,DeeplistCloudData.settings(JSONObject().put("settings",finalSettings)),false)
                }
                val output=file.startWrite()
                try { output.write(JSONObject().put("revision",revision).put("data",merged).toString().toByteArray());file.finishWrite(output) } catch(e: Exception) { file.failWrite(output);throw e }
                mutable.value=DeeplistCloudState(owner,lastSync=System.currentTimeMillis())
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { mutable.value=mutable.value.copy(error=e.message ?: "Account sync failed. Your local data is safe.") }
        finally { mutable.value=mutable.value.copy(busy=false);mutex.unlock() }
    }
}
