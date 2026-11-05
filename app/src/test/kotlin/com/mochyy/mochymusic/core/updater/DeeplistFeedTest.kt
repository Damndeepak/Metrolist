package com.mochyy.mochymusic.core.updater

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DeeplistFeedTest {
    private fun feed() = JSONObject().put("packageId", DeeplistPackage)
        .put("signingCertificateSha256", DeeplistCertificate).put("downloadUrl", DeeplistApkUrl)
        .put("versionCode", 163).put("sizeBytes", 25_000_000).put("versionName", "13.8.1")
        .put("sha256", "a".repeat(64)).put("publishedAt", "2026-11-05T10:00:00Z")
        .put("assetUrl", "$GithubRepoUrl/releases/download/deeplist-163/Deeplist.apk")
        .put("changelog", JSONArray().put("Player improvements").put("Sync fixes"))

    @Test fun `feed maps release code without parsing code from semver or tag`() {
        val release = parseDeeplistRelease(feed().toString())
        assertEquals(163, release.versionCode)
        assertEquals("deeplist-163", release.tag)
        assertEquals(DeeplistApkUrl, release.apkUrl)
        assertEquals("Player improvements\nSync fixes", release.body)
        assertEquals(release, parseDeeplistRelease(releaseManifest(release)))
    }

    @Test fun `wrong app signer URL size hash and fractional code are rejected`() {
        val invalid = listOf(
            "packageId" to "com.mochyy.mochymusic",
            "signingCertificateSha256" to "b".repeat(64),
            "downloadUrl" to "https://github.com/realidkroo/Mochy-Music/mochy.apk",
            "assetUrl" to "$GithubRepoUrl/releases/download/deeplist-162/Deeplist.apk",
            "sizeBytes" to 0, "sizeBytes" to 500_000_001,
            "sha256" to "invalid", "versionCode" to 163.5,
            "versionCode" to 0, "versionCode" to 4_294_967_459L,
            "versionName" to "8.3.2 build#1226",
        )
        invalid.forEach { (key, value) ->
            assertTrue("$key=$value accepted", runCatching { parseDeeplistRelease(feed().put(key, value).toString()) }.isFailure)
        }
    }
}
