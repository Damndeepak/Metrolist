// RememberSettled.kt

package com.mochyy.mochymusic.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * [value] once it has stayed the same for [delayMs]. The first value comes straight through; a new
 * one reads as null until it has settled, so whatever the old value started stops at once.
 *
 * For heavy things that follow the playing song, like a video decoder: skipping through songs
 * shouldn't start one for every song passed on the way.
 */
@Composable
fun <T : Any> rememberSettled(value: T, delayMs: Long): T? {
    var settled by remember { mutableStateOf<T?>(value) }
    LaunchedEffect(value) {
        if (settled == value) return@LaunchedEffect
        settled = null
        delay(delayMs)
        settled = value
    }
    return settled?.takeIf { it == value }
}
