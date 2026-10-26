package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    @Test
    fun parsesKmpReleaseArtifact() {
        val response =
            """
            {
              "tag_name": "v1.2.3",
              "body": null,
              "published_at": "2026-09-05T12:00:00Z",
              "assets": [{
                "name": "Metrolist.apk",
                "browser_download_url": "https://example.com/Metrolist.apk",
                "size": 42
              }]
            }
            """.trimIndent()
        val release = checkNotNull(Updater.parseKmpRelease(response))

        assertEquals("1.2.3", release.versionName)
        assertEquals("", release.description)
        assertEquals("https://example.com/Metrolist.apk", release.assets.single().downloadUrl)
        assertNull(Updater.parseKmpRelease(response.replace("Metrolist.apk", "Metrolist-with-Google-Cast.apk")))
    }
    @Test
    fun deeplistFeedChecksIdentityAndUsesVersionCode() {
        val response = """{
            "packageId":"com.deepu.wordstorm", "versionName":"13.7.1", "versionCode":154,
            "signingCertificateSha256":"1c73c95916d7e14bac0533d0a4a20a3c01be78f066fcc0c1102254712c4bcd5d",
            "sha256":"d89d4a2e8d2fb9ba5cb3f7d71b60e299393327ec2f3a1ccd97eea8efd9b768d3",
            "sizeBytes":25394213, "publishedAt":"2026-10-26", "changelog":["Fixes"],
            "downloadUrl":"https://world.damndeepak.workers.dev/downloads/deeplist.apk"
        }"""
        val release = Updater.parseDeeplistRelease(response)
        org.junit.Assert.assertTrue(Updater.hasNewerVersion(release, 153))
        org.junit.Assert.assertFalse(Updater.hasNewerVersion(release, 154))
        org.junit.Assert.assertFalse(Updater.hasNewerVersion(release, 155))
        assertEquals("Fixes", release.description)
        for (invalid in listOf(
            response.replace("com.deepu.wordstorm", "com.metrolist.music"),
            response.replace("1c73c959", "ffffffff"),
            response.replace("https://world.damndeepak.workers.dev/downloads/deeplist.apk", "https://example.com/app.apk"),
        )) org.junit.Assert.assertTrue(runCatching { Updater.parseDeeplistRelease(invalid) }.isFailure)
    }

}
