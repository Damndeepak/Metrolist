package com.metrolist.music.ui.component

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DeeplistWastedDemoTest {
    private fun request(address: String) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(address)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    @Test
    fun embeddedDemoBlocksExternalPagesAndUntrustedResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = WebView(context)
        val client = WastedDemoWebViewClient(context)
        try {
            assertNull(client.shouldInterceptRequest(view, request("https://world.damndeepak.workers.dev/media/clip.mp3")))
            for (url in listOf("https://example.com/script.js", "http://world.damndeepak.workers.dev/media/clip.mp3",
                "file:///data/data/private", "https://appassets.androidplatform.net/other")) {
                assertEquals(403, client.shouldInterceptRequest(view, request(url))?.statusCode)
                assertTrue(client.shouldOverrideUrlLoading(view, request(url)))
            }
        } finally {
            view.destroy()
        }
    }
}
