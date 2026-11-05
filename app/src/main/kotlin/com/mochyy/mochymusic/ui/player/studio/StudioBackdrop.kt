package com.mochyy.mochymusic.ui.player.studio

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.mochyy.mochymusic.constants.PlayerBottomBarStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The player, frozen: as the middle part or the bottom bar opens, the player is captured once (that
 * part's pieces lifted out first) and blurred once (small, on a background thread); the editor's
 * camera moves over that still. Nothing of the live player is drawn behind it after that.
 */
@Stable
class StudioCapture internal constructor(internal val layer: GraphicsLayer) {
    /** the sheet records itself into [layer] while this is set */
    internal var recording by mutableStateOf(false)
    /** the player as it was when the part opened, its pieces lifted out */
    var image by mutableStateOf<ImageBitmap?>(null)
        internal set
    /** [image], softened: what the camera settles on behind the pieces */
    var soft by mutableStateOf<ImageBitmap?>(null)
        internal set

    /** has the sheet record itself for a frame or two, then keeps that as [image] */
    internal suspend fun take(): ImageBitmap? {
        recording = true
        withFrameNanos { }
        withFrameNanos { }
        val frame = runCatching { layer.toImageBitmap() }.getOrNull()
        recording = false
        image = frame
        return frame
    }
}

/** the parts of the player the editor opens with a camera move, lifting their pieces off it */
enum class LiftedPart { MIDDLE, BAR }

/**
 * The pieces the editor lifts off the player: the middle part's side button, chevron, seek bar and
 * end time, or the transport and the bottom bar (in both its styles). While a part is lifted, each
 * of its pieces stops drawing in its place and records itself into its own layer instead, and the
 * editor draws those layers where the pieces are going: the player's own drawing of each piece,
 * so nothing is swapped for a lookalike.
 */
@Stable
class StudioPieces internal constructor(internal val layers: List<GraphicsLayer>) {
    var liftedPart by mutableStateOf<LiftedPart?>(null)
        internal set

    val lifted: Boolean get() = liftedPart != null

    internal fun isLifted(which: Int): Boolean = when (liftedPart) {
        LiftedPart.MIDDLE -> which <= END_TIME
        LiftedPart.BAR -> which >= TRANSPORT
        null -> false
    }

    companion object {
        const val SIDE = 0
        const val CHEVRON = 1
        const val SEEK_BAR = 2
        const val END_TIME = 3
        const val TRANSPORT = 4
        const val CARD_STACK = 5
        const val VOLUME_BAR = 6

        /** the piece that draws the bottom bar in [style] */
        fun bar(style: PlayerBottomBarStyle): Int = when (style) {
            PlayerBottomBarStyle.STACK -> CARD_STACK
            PlayerBottomBarStyle.VOLUME -> VOLUME_BAR
        }
    }
}

@Composable
fun rememberStudioPieces(): StudioPieces {
    val layers = List(7) { rememberGraphicsLayer() }
    return remember(layers) { StudioPieces(layers) }
}

/** on one of the pieces: drawn in place normally, recorded for the editor while its part is lifted */
fun Modifier.studioPiece(pieces: StudioPieces?, which: Int): Modifier {
    if (pieces == null) return this
    return drawWithContent {
        if (pieces.isLifted(which)) {
            pieces.layers[which].record { this@drawWithContent.drawContent() }
        } else {
            drawContent()
        }
    }
}

/**
 * Runs the camera moves' stills. Opening the middle part or the bottom bar lifts its pieces and
 * takes the rest of the player as one picture (the camera waits for it, a frame or two); the live
 * player stops drawing under it until the camera is back out.
 */
@Composable
fun StudioCameraEffect(
    studio: PlayerStudioState,
    capture: StudioCapture,
    pieces: StudioPieces,
    /** the bottom bar style the player shows */
    barStyle: PlayerBottomBarStyle,
) {
    LaunchedEffect(studio, capture, pieces) {
        snapshotFlow { studio.cameraPart }.collectLatest { part ->
            val up = pieces.liftedPart
            if (up != null && up != part) {
                // another part's camera is still on its way out (a quick tap from one to the
                // other): this one waits until the player is itself again
                if (part != null) studio.camera(part).value = 0f
                snapshotFlow { studio.camera(up).value }.first { it < 0.002f }
                studio.sheetCovered = false
                // the live player is back under the still for a frame before the pieces go home
                withFrameNanos { }
                pieces.liftedPart = null
                capture.image = null
                capture.soft = null
            }
            // nothing to open, or opened again before it had closed: the still is already there
            if (part == null || pieces.liftedPart == part) return@collectLatest
            val camera = studio.camera(part)
            camera.value = 0f
            pieces.liftedPart = part
            val frame = capture.take()
            if (frame != null) studio.sheetCovered = true
            camera.animateTo(1f)
            if (frame != null) capture.soft = soften(frame)
        }
    }
    // a bar style picked in the editor moves the controls above it: the still is taken again, so
    // the camera pulls back out onto the player as it is now
    LaunchedEffect(barStyle) {
        if (pieces.liftedPart != LiftedPart.BAR || capture.image == null) return@LaunchedEffect
        val frame = capture.take() ?: return@LaunchedEffect
        val soft = soften(frame)
        if (pieces.liftedPart == LiftedPart.BAR) capture.soft = soft
    }
}

private suspend fun soften(frame: ImageBitmap): ImageBitmap? =
    withContext(Dispatchers.Default) { runCatching { softBackdrop(frame) }.getOrNull() }

/** on the player sheet: records it into the capture's layer, while a capture is being taken */
fun Modifier.studioCaptureSource(capture: StudioCapture?): Modifier {
    if (capture == null) return this
    return drawWithContent {
        if (capture.recording) {
            capture.layer.record { this@drawWithContent.drawContent() }
            drawLayer(capture.layer)
        } else {
            drawContent()
        }
    }
}

/** a soft copy of [image]: an eighth of the size, box-blurred three times */
internal fun softBackdrop(image: ImageBitmap): ImageBitmap {
    val source = image.asAndroidBitmap().let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.config == Bitmap.Config.HARDWARE) {
            it.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            it
        }
    }
    val w = (source.width / 8).coerceAtLeast(1)
    val h = (source.height / 8).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(source, w, h, true)
    val a = IntArray(w * h)
    small.getPixels(a, 0, w, 0, 0, w, h)
    val b = IntArray(w * h)
    repeat(3) {
        boxBlur(a, b, w, h, 5, horizontal = true)
        boxBlur(b, a, w, h, 5, horizontal = false)
    }
    return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val window = r * 2 + 1
    for (line in 0 until lines) {
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (horizontal) src[line * w + c] else src[c * w + line]
        }
        for (i in -r..r) {
            val p = at(i)
            sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
        }
        for (i in 0 until length) {
            val out = ((sa / window) shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
            if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
            val add = at(i + r + 1)
            val remove = at(i - r)
            sa += (add ushr 24) - (remove ushr 24)
            sr += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            sg += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            sb += (add and 0xFF) - (remove and 0xFF)
        }
    }
}
