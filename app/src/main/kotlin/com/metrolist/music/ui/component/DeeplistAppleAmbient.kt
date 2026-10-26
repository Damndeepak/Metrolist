package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
internal fun DeeplistAppleAmbient(playing: Boolean, blur: Float, dim: Float) {
    val clock = rememberDeeplistClock(playing, smooth = false)
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        val drift = sin(clock.longValue / 16000f * Math.PI).toFloat()
        scaleX = 1.18f + drift * .06f
        scaleY = scaleX
        rotationZ = drift * 6f
        translationX = drift * size.width * .025f
        translationY = drift * size.height * .015f
    }.blur((blur.coerceIn(0f, 1f) * 60f).dp)) {
        drawRect(Color(0xFF302127))
        drawRect(Brush.radialGradient(listOf(Color(0xFFBA7469), Color.Transparent),
            center = Offset(size.width * .2f, size.height * .25f), radius = size.maxDimension * .65f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF3B4962), Color.Transparent),
            center = Offset(size.width * .85f, size.height * .7f), radius = size.maxDimension * .6f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF8F443F), Color.Transparent),
            center = Offset(size.width * .35f, size.height * .9f), radius = size.maxDimension * .5f))
        drawRect(Color.Black.copy(alpha = (dim.coerceIn(.15f, .85f) * .55f)))
    }
}
