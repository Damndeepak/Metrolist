package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.IpodShadowKey
import com.mochyy.mochymusic.constants.IpodTallScreenKey
import com.mochyy.mochymusic.constants.IpodTheme
import com.mochyy.mochymusic.constants.IpodThemeKey
import com.mochyy.mochymusic.constants.IpodWheelColor
import com.mochyy.mochymusic.constants.IpodWheelColorKey
import com.mochyy.mochymusic.ui.player.customize.CoverOption
import com.mochyy.mochymusic.ui.player.ipod.IpodLook
import com.mochyy.mochymusic.ui.player.ipod.lookOf
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

// the ipod's sheet: its body, its wheel, how tall its screen is and the shading round it
@Composable
internal fun ColumnScope.IpodOptions() {
    var theme by rememberEnumPreference(IpodThemeKey, IpodTheme.SILVER)
    var wheel by rememberEnumPreference(IpodWheelColorKey, IpodWheelColor.THEME)
    var tall by rememberPreference(IpodTallScreenKey, defaultValue = false)
    OptionSheet(title = "The iPod") {
        IpodTheme.entries.forEachIndexed { index, option ->
            OptionRow(
                selected = theme == option,
                onClick = { theme = option },
                caption = if (index == 0) "Body" else null,
                compact = true,
            ) {
                Swatch(lookOf(option, wheel, shadow = true, tall = false), option.title, option.description)
            }
        }
        IpodWheelColor.entries.forEachIndexed { index, option ->
            OptionRow(
                selected = wheel == option,
                onClick = { wheel = option },
                caption = if (index == 0) "Click wheel" else null,
                compact = true,
            ) {
                Swatch(lookOf(theme, option, shadow = true, tall = false), option.title, option.description)
            }
        }
        OptionRow(selected = !tall, onClick = { tall = false }, caption = "Screen", compact = true) {
            TwoLines("Classic", "the original shape, wheel in the middle")
        }
        OptionRow(selected = tall, onClick = { tall = true }, compact = true) {
            TwoLines("Tall", "the screen stretches down to the wheel")
        }
        SwitchRow(IpodShadowOption)
    }
}

// a tiny ipod in the chosen colours next to the choice's name
@Composable
private fun Swatch(look: IpodLook, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 26.dp, height = 40.dp)) {
            val corner = CornerRadius(5.dp.toPx())
            drawRoundRect(look.body.brush, cornerRadius = corner)
            drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = corner, style = Stroke(1.dp.toPx()))
            val glass = Size(size.width * 0.72f, size.height * 0.3f)
            drawRoundRect(look.screen.background, topLeft = Offset((size.width - glass.width) / 2f, size.height * 0.1f), size = glass, cornerRadius = CornerRadius(2.dp.toPx()))
            val wheelCenter = Offset(size.width / 2f, size.height * 0.7f)
            drawCircle(look.wheel.face, size.width * 0.3f, wheelCenter)
            drawCircle(look.wheel.center, size.width * 0.12f, wheelCenter)
        }
        Spacer(Modifier.width(14.dp))
        TwoLines(title, description)
    }
}

private val IpodTheme.title: String
    get() = when (this) {
        IpodTheme.SILVER -> "Silver"
        IpodTheme.BLACK -> "Black"
        IpodTheme.U2 -> "U2 edition"
        IpodTheme.TRUE_DARK -> "True dark"
    }

private val IpodTheme.description: String
    get() = when (this) {
        IpodTheme.SILVER -> "the classic white and grey"
        IpodTheme.BLACK -> "grey fading down to black"
        IpodTheme.U2 -> "black with a red wheel"
        IpodTheme.TRUE_DARK -> "pitch black, the screen too"
    }

private val IpodWheelColor.title: String
    get() = when (this) {
        IpodWheelColor.THEME -> "Match the body"
        IpodWheelColor.WHITE -> "White"
        IpodWheelColor.BLACK -> "Black"
        IpodWheelColor.RED -> "Red"
    }

private val IpodWheelColor.description: String
    get() = when (this) {
        IpodWheelColor.THEME -> "whichever wheel the body comes with"
        IpodWheelColor.WHITE -> "white with grey buttons"
        IpodWheelColor.BLACK -> "dark grey with white buttons"
        IpodWheelColor.RED -> "red with white buttons"
    }

private val IpodShadowOption = CoverOption.Switch(
    title = "Shadow",
    description = "soft shading round the edges of the body",
    icon = R.drawable.contrast,
    key = IpodShadowKey,
    default = true,
)
