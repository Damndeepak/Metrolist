package com.mochyy.mochymusic.core.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.mochyy.mochymusic.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val GithubOwner = "Damndeepak"
const val GithubRepo = "Metrolist"
const val GithubProfileUrl = "https://github.com/$GithubOwner"
const val GithubRepoUrl = "https://github.com/$GithubOwner/$GithubRepo"
const val InstagramUrl = "https://www.instagram.com/furfurrfoxx/"
const val ThreadsUrl = "https://www.threads.net/@furfurrfoxx"
const val DiscordUrl = "https://discord.gg/NTmqVphbRY"
internal const val DeeplistFeedUrl = "https://world.damndeepak.workers.dev/downloads/deeplist.json"
internal const val DeeplistApkUrl = "https://world.damndeepak.workers.dev/downloads/deeplist.apk"
internal const val DeeplistPackage = "com.deepu.wordstorm"
internal const val DeeplistCertificate = "1c73c95916d7e14bac0533d0a4a20a3c01be78f066fcc0c1102254712c4bcd5d"

data class GithubRelease(
    val title: String, val tag: String, val version: String, val body: String,
    val htmlUrl: String, val apkUrl: String?, val apkName: String?,
    val apkSizeBytes: Long, val publishedAt: String,
    val versionCode: Int, val sha256: String,
)

sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GithubRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

data class FullVersion(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val buildNumber: Int = 0
) : Comparable<FullVersion> {
    override fun compareTo(other: FullVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return buildNumber.compareTo(other.buildNumber)
    }
}

fun parseFullVersion(text: String): FullVersion {
    val semverRegex = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")
    val buildNumRegex = Regex("""(?:build#|\(#|#|\()(\d+)\)?""", RegexOption.IGNORE_CASE)

    val semverMatch = semverRegex.find(text)
    val major = semverMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val minor = semverMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
    val patch = semverMatch?.groupValues?.getOrNull(3)?.toIntOrNull() ?: 0

    val buildMatch = buildNumRegex.find(text)
    val buildNumber = buildMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

    return FullVersion(major, minor, patch, buildNumber)
}


internal fun parseDeeplistRelease(response: String): GithubRelease {
    val json = JSONObject(response)
    require(json.getString("packageId") == DeeplistPackage)
    require(json.getString("signingCertificateSha256") == DeeplistCertificate)
    require(json.getString("downloadUrl") == DeeplistApkUrl)
    require(json.get("versionCode") is Number && json.get("versionCode").toString().matches(Regex("[0-9]+")))
    require(json.get("sizeBytes") is Number && json.get("sizeBytes").toString().matches(Regex("[0-9]+")))
    val codeLong = json.getLong("versionCode")
    require(codeLong in 1..Int.MAX_VALUE.toLong())
    val code = codeLong.toInt()
    val size = json.getLong("sizeBytes")
    val hash = json.getString("sha256")
    val version = json.getString("versionName")
    require(code > 0 && size in 1..500_000_000L)
    require(hash.matches(Regex("[a-f0-9]{64}")))
    require(version.matches(Regex("""\d+\.\d+\.\d+""")))
    val asset = "$GithubRepoUrl/releases/download/deeplist-$code/Deeplist.apk"
    require(json.getString("assetUrl") == asset)
    val changes = json.getJSONArray("changelog")
    return GithubRelease("Deeplist $version", "deeplist-$code", version,
        (0 until changes.length()).joinToString("\n") { changes.getString(it) },
        "$GithubRepoUrl/releases/tag/deeplist-$code", DeeplistApkUrl, "Deeplist-$code.apk",
        size, json.getString("publishedAt"), code, hash)
}

@Volatile private var cachedLatest: GithubRelease? = null
@Volatile private var cachedAt = 0L

suspend fun getLatestRelease(force: Boolean = false): Result<GithubRelease?> = withContext(Dispatchers.IO) {
    try {
        val cached = cachedLatest
        if (!force && cached != null && System.currentTimeMillis() - cachedAt < 15 * 60_000L) {
            return@withContext Result.success(cached)
        }
        val connection = (URL(DeeplistFeedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 12_000
            setRequestProperty("User-Agent", "Deeplist-Android")
            setRequestProperty("Cache-Control", "no-cache")
        }
        val response = try {
            check(connection.responseCode == 200)
            connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                require(text.length <= 100_000)
                text
            }
        } finally { connection.disconnect() }
        val release = parseDeeplistRelease(response)
        cachedLatest = release; cachedAt = System.currentTimeMillis()
        Result.success(release)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }
}

// The verified feed deliberately exposes only the current release; never offer upstream downgrades.
suspend fun getPreviousRelease(force: Boolean = false): Result<GithubRelease?> = Result.success(null)

fun isNewerThanInstalled(candidateVersionText: String): Boolean =
    BuildConfig.APPLICATION_ID == DeeplistPackage &&
        (cachedLatest?.takeIf { it.version == candidateVersionText }?.let { it.versionCode > BuildConfig.VERSION_CODE } ?: false)

internal fun releaseManifest(release: GithubRelease): String = JSONObject()
    .put("packageId", DeeplistPackage).put("signingCertificateSha256", DeeplistCertificate)
    .put("downloadUrl", DeeplistApkUrl).put("versionCode", release.versionCode)
    .put("versionName", release.version).put("sizeBytes", release.apkSizeBytes)
    .put("sha256", release.sha256).put("publishedAt", release.publishedAt)
    .put("assetUrl", "$GithubRepoUrl/releases/download/deeplist-${release.versionCode}/Deeplist.apk")
    .put("changelog", org.json.JSONArray().put(release.body)).toString()

fun apkFileFor(context: Context, release: GithubRelease): File =
    File(context.cacheDir, "Deeplist-${release.versionCode}.apk")

private fun manifestFile(apk: File) = File(apk.parentFile, apk.name + ".json")

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun fileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

@Suppress("DEPRECATION")
internal fun validateDownloadedApk(context: Context, apk: File, expected: GithubRelease? = null) {
    val release = expected ?: parseDeeplistRelease(manifestFile(apk).readText())
    require(context.packageName == DeeplistPackage && release.versionCode > BuildConfig.VERSION_CODE)
    require(apk.length() == release.apkSizeBytes && fileSha256(apk) == release.sha256)
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags))
    require(archive.packageName == DeeplistPackage)
    val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
    require(code == release.versionCode.toLong() && archive.versionName == release.version)
    val certificates = requireNotNull(if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures)
    require(certificates.size == 1 && certificates.all { sha256(it.toByteArray()) == DeeplistCertificate })
}

fun isDownloaded(context: Context, release: GithubRelease): Boolean {
    val file = apkFileFor(context, release)
    return file.isFile && file.length() == release.apkSizeBytes && runCatching {
        parseDeeplistRelease(manifestFile(file).readText()) == release
    }.getOrDefault(false)
}

suspend fun downloadApk(context: Context, release: GithubRelease, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
    var temporary: File? = null
    try {
        require(release.apkUrl == DeeplistApkUrl && release.versionCode > BuildConfig.VERSION_CODE)
        val target = apkFileFor(context, release)
        if (isDownloaded(context, release)) {
            validateDownloadedApk(context, target, release); onProgress(1f)
            return@withContext Result.success(target)
        }
        val partial = File.createTempFile("deeplist-update-", ".part", context.cacheDir).also { temporary = it }
        val connection = (URL(DeeplistApkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "Deeplist-Android")
        }
        try {
            check(connection.responseCode == 200)
            connection.inputStream.use { input -> partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var written = 0L
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val read = input.read(buffer); if (read < 0) break
                    written += read; require(written <= release.apkSizeBytes)
                    output.write(buffer, 0, read); onProgress(written.toFloat() / release.apkSizeBytes)
                }
            } }
        } finally { connection.disconnect() }
        validateDownloadedApk(context, partial, release)
        check(partial.renameTo(target))
        manifestFile(target).writeText(releaseManifest(release))
        onProgress(1f); Result.success(target)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }
    finally { temporary?.delete() }
}

fun canInstallPackages(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

fun requestInstallPermission(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:" + context.packageName),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun installApk(context: Context, apk: File): Boolean {
    if (!runCatching { validateDownloadedApk(context, apk) }.isSuccess) return false
    if (!canInstallPackages(context)) {
        requestInstallPermission(context)
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "unknown size"
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000f)
    else -> String.format("%.0f KB", bytes / 1000f)
}
