package com.metrolist.music.ui.component

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.*
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.rememberEnumPreference
import kotlin.math.abs

@Composable
internal fun VerciStylePreview() {
    val clock = rememberDeeplistClock(true, smooth = true)
    val movement by rememberEnumPreference(VerciStyleKey, VerciStyle.FISHEYE)
    val font by rememberEnumPreference(VerciFontKey, VerciFont.BOLD)
    val legacySize by rememberPreference(VerciTextSizeKey, 52f)
    val sampleFontSize by rememberPreference(deeplistSizeKey(movement), if (movement == VerciStyle.APPLE) 32f else legacySize)
    val colorIndex by rememberPreference(VerciColorKey, 0)
    val emojis by rememberPreference(VerciEmojiKey, true)
    val advanced by rememberPreference(VerciAdvancedEmojiKey, false)
    val spacing by rememberPreference(VerciSpacingKey, 1f)
    Box(Modifier.fillMaxWidth().height(150.dp).clipToBounds()) {
        Canvas(Modifier.matchParentSize().drawWithCache {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = (sampleFontSize * .46f).dp.toPx()
                typeface = Typeface.create(when (font) { VerciFont.BOLD -> "sans-serif"; VerciFont.ROUNDED -> "sans-serif-rounded"; VerciFont.SERIF -> "serif"; VerciFont.MONO -> "monospace" }, Typeface.BOLD)
                this.color = verciColors[colorIndex.coerceIn(0, 4)]
            }
            val words = listOf("Love", "the", "music").map { text ->
                val icon = com.metrolist.music.lyrics.verciEmoji(text, advanced, emojis)
                if (icon.isEmpty()) text else "$text $icon"
            }
            val widths = words.map { paint.measureText(it) }
            onDrawBehind {
                val phase = (clock.longValue % 3600L) / 1200f
                val c = drawContext.canvas.nativeCanvas
                words.forEachIndexed { i, word ->
                    var distance = i - phase
                    if (distance < -1.5f) distance += 3
                    if (distance > 1.5f) distance -= 3
                    val scale = 1f / (1f + .8f * abs(distance))
                    val drift = if (movement == VerciStyle.SHIP) kotlin.math.sin(i * 127.1f) * this.size.width * .16f * minOf(1f, abs(distance)) else 0f
                    c.save(); c.translate(this.size.width / 2 + drift, this.size.height / 2 + distance * this.size.height * .32f * spacing)
                    c.scale(scale, scale); paint.alpha = ((1 - .4f * abs(distance)) * 255).toInt().coerceIn(0, 255)
                    c.drawText(word, -widths[i] / 2, -paint.fontMetrics.ascent / 3, paint); c.restore()
                }
            }
        }) {}
    }
}
