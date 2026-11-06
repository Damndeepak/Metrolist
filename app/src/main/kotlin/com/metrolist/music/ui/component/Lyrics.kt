/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.constants.WordstormEnabledKey
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LyricsViewModel

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyrics: Boolean,
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    var wordstormEnabled by rememberPreference(WordstormEnabledKey, true)
    if (wordstormEnabled) {
        WordstormLyrics(sliderPositionProvider, showLyrics, { wordstormEnabled = false }, modifier)
        return
    }
    Box(modifier.fillMaxSize()) {
        val (experimentalLyrics, _) = rememberPreference(key = ExperimentalLyricsKey, defaultValue = true)
        if (experimentalLyrics) {
            ExperimentalLyrics(
                sliderPositionProvider = sliderPositionProvider,
                modifier = Modifier,
                showLyrics = showLyrics,
                lyricsViewModel = lyricsViewModel,
            )
        } else {
            OriginalLyrics(
                sliderPositionProvider = sliderPositionProvider,
                modifier = Modifier,
                showLyrics = showLyrics,
            )
        }
        if (showLyrics) {
            TextButton(onClick = { wordstormEnabled = true }, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()) {
                Text(stringResource(R.string.wordstorm_enable))
            }
        }
    }
}
