package com.metrolist.music.ui.component

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import kotlinx.coroutines.flow.collect
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

internal class WastedDemoPlaybackBridge(private val onPlayback: () -> Unit) {
    @JavascriptInterface
    fun onPlay() = onPlayback()
}

internal class WastedDemoWebViewClient(private val context: android.content.Context) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
            uri.path == "/deeplist-wasted/index.html"
        ) return WebResourceResponse("text/html", "UTF-8", context.assets.open("deeplist-wasted-demo.html"))
        if (uri.scheme == "https" && uri.host in setOf(
                "world.damndeepak.workers.dev", "fonts.googleapis.com", "fonts.gstatic.com",
            )
        ) return null
        return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DeeplistWastedDemo(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val connection = LocalPlayerConnection.current
    val latestConnection by rememberUpdatedState(connection)
    val webView = remember(context, owner) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(16, 12, 16))
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = WastedDemoWebViewClient(context)
        }
    }
    val active = remember(webView) { AtomicBoolean(true) }
    DisposableEffect(webView, owner) {
        webView.addJavascriptInterface(WastedDemoPlaybackBridge {
            webView.post { if (active.get()) latestConnection?.pause() }
        }, "DeeplistDemo")
        webView.loadUrl("https://appassets.androidplatform.net/deeplist-wasted/index.html")
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                webView.evaluateJavascript("window.deeplistPauseDemo && window.deeplistPauseDemo()", null)
                webView.onPause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                webView.onResume()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            active.set(false)
            owner.lifecycle.removeObserver(observer)
            webView.evaluateJavascript("window.deeplistPauseDemo && window.deeplistPauseDemo()", null)
            webView.removeJavascriptInterface("DeeplistDemo")
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }
    LaunchedEffect(connection, webView) {
        connection?.pause()
        connection?.isPlaying?.collect { playing ->
            if (playing) webView.evaluateJavascript("window.deeplistPauseDemo && window.deeplistPauseDemo()", null)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF100C10), contentColor = Color.White) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.deeplist_wasted_demo), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.wordstorm_done)) }
                }
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}
