package com.mochyy.mochymusic.ui.player.ipod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mochyy.mochymusic.constants.IpodShadowKey
import com.mochyy.mochymusic.constants.IpodTallScreenKey
import com.mochyy.mochymusic.constants.IpodTheme
import com.mochyy.mochymusic.constants.IpodThemeKey
import com.mochyy.mochymusic.constants.IpodWheelColor
import com.mochyy.mochymusic.constants.IpodWheelColorKey
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

// every colour the ipod is drawn with, plus the two layout switches, worked out once from the settings
@Immutable
class IpodLook(
    val body: IpodBodyColors,
    val wheel: IpodWheelColors,
    val screen: IpodScreenColors,
    val shadow: Boolean,
    val tall: Boolean,
)

@Immutable
class IpodBodyColors(
    val top: Color,
    val bottom: Color,
    val chevron: Color,
    val bezel: Color,
    // light enough that the status bar icons need to turn dark over it
    val light: Boolean,
) {
    val brush: Brush = Brush.verticalGradient(listOf(top, bottom))
}

@Immutable
class IpodWheelColors(
    val face: Color,
    val edge: Color,
    val ink: Color,
    val center: Color,
    val centerPressed: Color,
    val pressGlow: Color,
)

@Immutable
class IpodScreenColors(
    val background: Color,
    val ink: Color,
    val softInk: Color,
    val quietInk: Color,
    val header: Brush,
    val headerLine: Color,
    val track: Brush,
    val trackLine: Color,
    val scrollTrack: Brush,
    val artBlank: Color,
)

val LocalIpodLook = staticCompositionLocalOf { lookOf(IpodTheme.SILVER, IpodWheelColor.THEME, shadow = true, tall = false) }

@Composable
fun rememberIpodLook(): IpodLook {
    val theme by rememberEnumPreference(IpodThemeKey, IpodTheme.SILVER)
    val wheel by rememberEnumPreference(IpodWheelColorKey, IpodWheelColor.THEME)
    val (shadow) = rememberPreference(IpodShadowKey, defaultValue = true)
    val (tall) = rememberPreference(IpodTallScreenKey, defaultValue = false)
    return remember(theme, wheel, shadow, tall) { lookOf(theme, wheel, shadow, tall) }
}

fun lookOf(theme: IpodTheme, wheel: IpodWheelColor, shadow: Boolean, tall: Boolean): IpodLook {
    val body = when (theme) {
        IpodTheme.SILVER -> SilverBody
        IpodTheme.BLACK, IpodTheme.U2 -> BlackBody
        IpodTheme.TRUE_DARK -> DarkBody
    }
    val wheelColors = when (wheel) {
        IpodWheelColor.WHITE -> WhiteWheel
        IpodWheelColor.BLACK -> BlackWheel
        IpodWheelColor.RED -> RedWheel
        IpodWheelColor.THEME -> when (theme) {
            IpodTheme.SILVER -> WhiteWheel
            IpodTheme.BLACK -> BlackWheel
            IpodTheme.U2 -> RedWheel
            IpodTheme.TRUE_DARK -> DarkWheel
        }
    }
    val screen = if (theme == IpodTheme.TRUE_DARK) DarkScreen else LightScreen
    return IpodLook(body, wheelColors, screen, shadow, tall)
}

// the colours below come from the mockup for silver and from ipod.js for the rest
private val SilverBody = IpodBodyColors(Color(0xFFE9E9E9), Color(0xFFD9D9D9), Color(0xFF555555), Color(0xFF8A8A8A), light = true)
private val BlackBody = IpodBodyColors(Color(0xFF7D7C7D), Color(0xFF1E1E1E), Color(0xFFE0E0E0), Color(0xFF0E0E0E), light = false)
private val DarkBody = IpodBodyColors(Color.Black, Color.Black, Color(0xFF5E5E5E), Color(0xFF1C1C1C), light = false)

private val WhiteWheel = IpodWheelColors(Color.White, Color(0xFFB9B9B9), Color(0xFFAFAFAF), Color(0xFFE6E6E6), Color(0xFFD4D4D4), Color(0x14000000))
private val BlackWheel = IpodWheelColors(Color(0xFF2A2A2A), Color(0xFF1A1A1A), Color.White, Color(0xFF7D7C7D), Color(0xFF656465), Color(0x1FFFFFFF))
private val RedWheel = IpodWheelColors(Color(0xFFDE2029), Color(0xFF1A1A1A), Color.White, Color(0xFF5D5C5D), Color(0xFF4A494A), Color(0x24FFFFFF))
private val DarkWheel = IpodWheelColors(Color(0xFF121212), Color(0xFF242424), Color(0xFF8A8A8A), Color(0xFF050505), Color(0xFF1C1C1C), Color(0x14FFFFFF))

private val LightScreen = IpodScreenColors(
    background = Color.White,
    ink = Color.Black,
    softInk = Color(0xFF6F6F6F),
    quietInk = Color(0xFF8A8A8A),
    header = Brush.verticalGradient(listOf(Color(0xFFFDFDFD), Color(0xFFE2E4E6), Color(0xFFA9AEB3))),
    headerLine = Color(0xFF8C8C8C),
    track = Brush.verticalGradient(listOf(Color(0xFFFDFDFD), Color(0xFFE6E6E6), Color(0xFFD4D4D4), Color(0xFFF2F2F2))),
    trackLine = Color(0xFFC4C4C4),
    scrollTrack = Brush.horizontalGradient(listOf(Color(0xFFDADADA), Color(0xFFF6F6F6), Color(0xFFDADADA))),
    artBlank = Color(0xFFD9D9D9),
)

private val DarkScreen = IpodScreenColors(
    background = Color(0xFF0B0B0B),
    ink = Color(0xFFF2F2F2),
    softInk = Color(0xFF9C9C9C),
    quietInk = Color(0xFF6A6A6A),
    header = Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF242424), Color(0xFF151515))),
    headerLine = Color(0xFF2E2E2E),
    track = Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF262626), Color(0xFF1C1C1C), Color(0xFF2E2E2E))),
    trackLine = Color(0xFF444444),
    scrollTrack = Brush.horizontalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF2E2E2E), Color(0xFF1E1E1E))),
    artBlank = Color(0xFF222222),
)
