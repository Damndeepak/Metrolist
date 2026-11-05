package com.mochyy.mochymusic.ui.utils

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier

/**
 * The system bars' insets as they are when shown - what screens should lay out against.
 *
 * The player hides the navigation bar while it's expanded. Every screen that read the live
 * `WindowInsets.systemBars` in composition recomposed through that bar's hide and show animation,
 * which runs at the end of every open and the start of every close - exactly when the screen is
 * being revealed behind the player. The bars' shown size never changes, so nothing has to.
 */
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.stableSystemBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsets.systemBarsIgnoringVisibility

// the status bar's height as it is when shown: the player can hide it, and pages laid out against the live one jumped as it went and came back
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.stableStatusBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsets.statusBarsIgnoringVisibility

// statusBarsPadding that stays put while the player hides the status bar
@Composable
fun Modifier.stableStatusBarsPadding(): Modifier = windowInsetsPadding(WindowInsets.stableStatusBars)
