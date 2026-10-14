package com.metrolist.music.ui.component

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class VerciBezelTest {
    @Test fun `actual camera path produces a separate camera contour`() {
        val outline = bezelPath(400f, 800f, 20f, emptyList(), 1f)
        val camera = Path().apply { addCircle(200f, 28f, 10f, Path.Direction.CW) }
        outline.op(camera, Path.Op.DIFFERENCE)
        assertTrue(neonContours(outline).size == 2)
    }
    @Test fun `top cutout adds a closed detour around the camera`() {
        val plain = PathMeasure(bezelPath(400f, 800f, 20f, emptyList(), 1f), true)
        val notch = PathMeasure(bezelPath(400f, 800f, 20f, listOf(RectF(170f, 0f, 230f, 32f)), 1f), true)
        assertTrue("Native path measure must be initialized, length=${plain.length}", plain.length > 0f)
        assertTrue(notch.isClosed)
        assertTrue(notch.length > plain.length)
    }
}
