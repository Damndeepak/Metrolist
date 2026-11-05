package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.theme.InterFontFamily

/**
 * The studio's type: Inter at the app's own tracking, in a handful of sizes, so the selector, the
 * editor's header and its sheets read like the rest of the app rather than a poster.
 */
internal object StudioType {
    /** the app's letter spacing (theme/Type.kt), on every studio text */
    val Tracking = (-0.05).em

    /** page headers: "Styles", "Edit player", a sheet's title */
    val Title = 16.sp
    /** the line under a header: the style shown, what a sheet is for */
    val Subtitle = 11.sp
    /** button labels */
    val Button = 14.sp
    /** a row's title */
    val Body = 14.sp
    /** a row's second line */
    val Caption = 12.sp
    /** small caps-style labels ("STYLE - 001", "SELECTED") */
    val Label = 10.sp
    /** the name of the cover or background being looked at */
    val Name = 20.sp
}

internal fun studioText(size: TextUnit, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = size,
    fontWeight = weight,
    letterSpacing = StudioType.Tracking,
)
