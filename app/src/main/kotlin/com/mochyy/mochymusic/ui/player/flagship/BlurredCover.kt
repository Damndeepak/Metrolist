package com.mochyy.mochymusic.ui.player.flagship

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size as CoilSize
import com.mochyy.mochymusic.ui.component.DownscaledBlur
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.drawGlassNode
import com.mochyy.mochymusic.ui.component.glassNodeHasContent
import com.mochyy.mochymusic.ui.player.BackdropBlurTransformation
import com.mochyy.mochymusic.ui.player.ReflectionBlurPx
import com.mochyy.mochymusic.ui.player.ReflectionPx
import com.mochyy.mochymusic.ui.utils.resize

// about as soft as the still picture's blurred copy once it's stretched over the cover
private val LiveBlur = 28.dp

// the picture the sharp cover fades into: the same picture read small and blurred once, the very bitmap its mirror below uses, cropped like the cover
@Composable
fun BlurredCoverPicture(url: String?, modifier: Modifier = Modifier) {
    if (url == null) return
    val context = LocalContext.current
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url.resize(ReflectionPx, ReflectionPx))
            .allowHardware(false)
            .transformations(BackdropBlurTransformation(radiusPx = ReflectionBlurPx))
            .crossfade(false)
            .size(CoilSize(ReflectionPx, ReflectionPx))
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

// a playing video or animated cover, blurred live at a quarter of the size, for the sharp one to fade into
@Composable
fun BlurredLiveCover(glass: GlassState, modifier: Modifier = Modifier) {
    val radiusPx = with(LocalDensity.current) { LiveBlur.toPx() }
    DownscaledBlur(
        radius = { radiusPx },
        tileMode = android.graphics.Shader.TileMode.CLAMP,
        mask = null,
        modifier = modifier,
    ) { _ ->
        val node = glass.renderNode ?: return@DownscaledBlur
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !glassNodeHasContent(node)) return@DownscaledBlur
        drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
    }
}
