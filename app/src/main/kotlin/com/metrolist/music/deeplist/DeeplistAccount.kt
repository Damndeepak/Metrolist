package com.metrolist.music.deeplist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.metrolist.music.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class DeeplistAccountState(val ready: Boolean = false, val user: JSONObject? = null, val busy: Boolean = false, val error: String? = null)
internal object DeeplistAccount {
    const val WEBSITE = "https://world.damndeepak.workers.dev"
    const val NOTICE = "2026-10-30"
    private val mutable = MutableStateFlow(DeeplistAccountState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private lateinit var context: Context
    private var saved = JSONObject()
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    val permitted: Boolean get() = BuildConfig.APPLICATION_ID != "com.deepu.wordstorm" || (state.value.user != null && state.value.user?.optBoolean("blocked") != true)

    fun initialize(value: Context) {
        if (mutable.value.ready) return
        context = value.applicationContext
        saved = runCatching {
            val bytes = File(context.noBackupFilesDir, "deeplist-account.enc").readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8))
        }.getOrDefault(JSONObject())
        mutable.value = DeeplistAccountState(ready = true, user = saved.optJSONObject("user"))
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("deeplist-account-v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("deeplist-account-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun persist() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(saved.toString().toByteArray(Charsets.UTF_8))
        val file = android.util.AtomicFile(File(context.noBackupFilesDir, "deeplist-account.enc"))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private class ApiError(val status: Int, message: String): Exception(message)
    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, bytes: ByteArray? = null): JSONObject = withContext(Dispatchers.IO) {
        val type = if (bytes != null) "image/jpeg" else "application/json"
        val payload = if (method == "GET") null else (bytes ?: (body ?: JSONObject()).toString().toByteArray()).toRequestBody(type.toMediaType())
        val builder = Request.Builder().url("$WEBSITE/api/app/$path").method(method,payload)
        saved.optString("token").takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            val value = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            if (!response.isSuccessful) throw ApiError(response.code, value.optString("error").ifBlank { "Connection unavailable. Try again." })
            value
        }
    }
    private suspend fun action(block: suspend () -> Unit) = mutex.withLock {
        mutable.value = mutable.value.copy(busy = true, error = null)
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e is ApiError && e.status == 401) { saved = JSONObject(); persist(); mutable.value = mutable.value.copy(user = null) }
            if (e is ApiError && e.status == 403 && mutable.value.user != null) {
                val user = JSONObject(mutable.value.user.toString()).put("blocked",true)
                saved.put("user",user); persist(); mutable.value = mutable.value.copy(user = user)
            }
            mutable.value = mutable.value.copy(error = if (e is ApiError) e.message else "Could not connect. Your local music is safe; try again.")
        } finally { mutable.value = mutable.value.copy(busy = false) }
    }
    private fun accept(value: JSONObject) {
        value.optString("token").takeIf { it.isNotBlank() }?.let { saved.put("token",it) }
        val user = value.getJSONObject("user"); saved.put("user",user); persist()
        mutable.value = mutable.value.copy(user = user)
    }
    suspend fun guest() = action { accept(request("auth/guest","POST",JSONObject().put("noticeVersion",NOTICE))) }
    suspend fun refresh(open: Boolean = false) = action {
        if (saved.optString("token").isBlank()) return@action
        // Me is readable even when blocked, so an unblock is picked up automatically.
        val value = request("me"); accept(value)
        if (!value.getJSONObject("user").optBoolean("blocked")) accept(request("heartbeat","POST",JSONObject().put("open",open).put("version",BuildConfig.BASE_VERSION_NAME)))
    }
    suspend fun google() = action {
        if (!request("config").optBoolean("googleConfigured")) throw ApiError(503,"Google sign-in is not configured yet. Continue as Guest for now.")
        val verifier = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        saved.put("verifier",verifier).put("signInAt",System.currentTimeMillis());persist()
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("$WEBSITE/api/app/auth/google?challenge=$challenge")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    suspend fun claim(uri: Uri) = action {
        require(uri.scheme == "deeplist" && uri.host == "account")
        val code = uri.getQueryParameter("code").orEmpty()
        val verifier = saved.optString("verifier")
        require(code.matches(Regex("[a-f0-9]{64}")) && verifier.isNotBlank() && System.currentTimeMillis()-saved.optLong("signInAt") in 0..600_000)
        val value = request("auth/claim","POST",JSONObject().put("code",code).put("verifier",verifier))
        saved.remove("verifier"); saved.remove("signInAt"); accept(value)
    }
    suspend fun edit(name: String, username: String, bio: String) = action { accept(request("me","PATCH",JSONObject().put("name",name).put("username",username).put("bio",bio))) }
    suspend fun avatar(bytes: ByteArray) = action { accept(request("avatar","POST",bytes=bytes)) }
    suspend fun publish(collections: JSONObject) = action { accept(request("collections","PUT",collections)) }
    suspend fun signOut() = action {
        request("session","DELETE");saved = JSONObject();persist();mutable.value = mutable.value.copy(user = null)
    }
    suspend fun delete() = action {
        request("me","DELETE");saved = JSONObject();persist();mutable.value = mutable.value.copy(user = null)
    }
}
