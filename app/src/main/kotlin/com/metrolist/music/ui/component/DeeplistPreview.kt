package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.lyrics.LyricsEntry
import kotlinx.coroutines.delay

@Composable
internal fun DeeplistPreview(fontSize: Float, accent: Color, preset: Int, motion: Boolean,
    speed: Float, performance: Boolean, neon: Float, mode: Int, density: Int, rainSpeed: Float, brightness: Float, rings: Boolean, glow: Float, sparks: Float, lift: Float, flight: Float) {
    val text = stringResource(R.string.deeplist_preview)
    val entry = remember(text) { LyricsEntry(0L, text) }
    val position = remember { mutableLongStateOf(0L) }
    var cycle by remember { mutableIntStateOf(0) }
    LaunchedEffect(fontSize, preset, speed, motion, performance) {
        position.longValue = 0L; cycle++
        while (true) { delay(40L); position.longValue += 40L; if (position.longValue >= 4800L) { position.longValue = 0L; cycle++ } }
    }
    Column {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF05040C)), contentAlignment = Alignment.Center) {
            DeeplistAtmosphere(text.split(" "), true, accent, mode, density, rainSpeed, brightness, rings, glow, performance)
            DeeplistEdgeLights(true, neonOverride = neon, performanceOverride = performance)
            key(cycle) {
            if (motion && preset != 2) DeeplistLine(entry, position, 0L, (fontSize * 0.62f).coerceIn(20f, 34f), accent,
                preset == 1, true, 4800L, Modifier.padding(14.dp), speed, performance, heightBudget = 100.dp, liftAmount = lift * 0.6f, flight = flight, sparks = sparks)
            else Text(text, color = accent, fontSize = (fontSize * 0.62f).coerceIn(20f, 34f).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(14.dp))
            }
        }
        Text(stringResource(R.string.deeplist_preview_note), fontSize = 11.sp, color = Color(0xFFB9B0CB), modifier = Modifier.padding(vertical = 8.dp))
    }
}
