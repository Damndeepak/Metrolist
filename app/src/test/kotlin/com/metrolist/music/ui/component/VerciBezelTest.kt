package com.metrolist.music.ui.component

import android.graphics.PathMeasure
import android.graphics.RectF
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VerciBezelTest {
    @Test fun `top cutout adds a closed detour around the camera`() {
        val plain = PathMeasure(bezelPath(400f, 800f, 20f, emptyList(), 1f), true)
        val notch = PathMeasure(bezelPath(400f, 800f, 20f, listOf(RectF(170f, 0f, 230f, 32f)), 1f), true)
        assertTrue(notch.isClosed)
        assertTrue(notch.length > plain.length)
    }
}
