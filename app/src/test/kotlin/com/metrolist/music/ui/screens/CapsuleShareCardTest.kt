package com.metrolist.music.ui.screens

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CapsuleShareCardTest {
    @Test fun `monthly share is a portrait PNG with temporary read permission`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = capsuleShareCard(context, CapsuleShareData(
            "My October Sound Capsule", "October 2026", "Top artists", "Top songs", "Time listened (minutes)",
            2144, listOf("Artist", "A very long artist name ".repeat(10)), listOf("Song", "हिन्दी गाना"),
        ))
        assertEquals("image/png", intent.type)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(intent.clipData)
        val files = File(context.cacheDir, "deeplist-capsule-cards").listFiles().orEmpty()
        val bitmap = BitmapFactory.decodeFile(files.maxBy { it.lastModified() }.absolutePath)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        bitmap.recycle()
    }
}
