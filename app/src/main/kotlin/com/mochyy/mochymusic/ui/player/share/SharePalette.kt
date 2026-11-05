// SharePalette.kt
//
// The cover's colours, which the backgrounds, the card and the line picker are all tinted with.

package com.mochyy.mochymusic.ui.player.share

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The cover's colours, strongest first, each pulled into a range where white text reads well on
 * it. Never empty once it returns: a cover that won't load gives a pair of greys.
 */
internal suspend fun coverPalette(context: Context, thumbnailUrl: String?): List<Color> = withContext(Dispatchers.IO) {
    val fallback = listOf(Color(0xFF3A3A3C), Color(0xFF242426))
    if (thumbnailUrl == null) return@withContext fallback
    val bitmap = runCatching {
        context.imageLoader.execute(
            ImageRequest.Builder(context).data(thumbnailUrl).size(128).allowHardware(false).build(),
        ).image?.toBitmap()
    }.getOrNull() ?: return@withContext fallback
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val swatches = listOfNotNull(
        palette.vibrantSwatch,
        palette.dominantSwatch,
        palette.darkVibrantSwatch,
        palette.mutedSwatch,
        palette.lightVibrantSwatch,
        palette.darkMutedSwatch,
    ) + palette.swatches.sortedByDescending { it.population }
    swatches
        .map { readableTone(it.rgb) }
        .distinctBy { it.toArgb() }
        .take(6)
        .ifEmpty { fallback }
}

/** Keeps the hue, caps the brightness so white text stays readable, and lifts colours that are near black. */
private fun readableTone(rgb: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rgb, hsv)
    hsv[1] = hsv[1].coerceIn(0.12f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.22f, 0.62f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
