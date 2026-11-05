// StreamFormatBadge.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.playback.StreamFacts
import com.mochyy.mochymusic.ui.theme.InterFontFamily

/**
 * "OPUS", or "OPUS · 48,0 KHz" with the sample rate on: an outlined tag in the design's proportions,
 * every one of them a fraction of [height], so it reads the same at any size and font scale.
 */
@Composable
fun StreamFormatBadge(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    color: Color = Color.White.copy(alpha = 0.55f),
) {
    // the text is sized off the box, not the user's font scale, so the two never drift apart
    val fontSize = with(LocalDensity.current) { (height * 0.5f).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .border(height * 0.0625f, color, RoundedCornerShape(height * 0.31f))
            .padding(horizontal = height * 0.29f),
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            // Inter's line box centres on its capitals, as the design centres them in the tag
            style = TextStyle(
                color = color,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = fontSize,
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

/** The badge's words for [facts]: nothing until the codec is known */
fun streamBadgeText(facts: StreamFacts?, showSampleRate: Boolean): String? {
    val codec = facts?.codec ?: return null
    val rate = facts.sampleRate?.takeIf { showSampleRate && it > 0 } ?: return codec
    // written as the design writes it, "44,1 KHz", whatever the phone's language
    val tenths = (rate + 50) / 100
    return "$codec · ${tenths / 10},${tenths % 10} KHz"
}
