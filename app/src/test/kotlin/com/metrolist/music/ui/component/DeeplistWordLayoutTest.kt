package com.metrolist.music.ui.component

import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeeplistWordLayoutTest {
    @Test fun `Apple requested size changes the actual painted word size`() {
        val small = deeplistApplePaint(32f)
        val large = deeplistApplePaint(64f)
        assertEquals(32f, small.textSize, 0f)
        assertEquals(64f, large.textSize, 0f)
        assertTrue(large.measureText("Wasted") > small.measureText("Wasted") * 1.9f)
    }

    @Test fun `word spacing tightens spaces without changing text offsets or letter widths`() {
        val text = "Stay with me"
        val spaced = deeplistSpacedText(text, .5f)
        assertEquals(text, spaced.toString())
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f }
        fun width(value: CharSequence): Float = Layout.getDesiredWidth(value, paint)
        assertTrue(width(spaced) < width(text))
        assertEquals(width("Stay"), width(deeplistSpacedText("Stay", .5f)), .01f)
        val layout = StaticLayout.Builder.obtain(spaced, 0, spaced.length, paint, 100).build()
        val word = DeeplistMotionWord(5, 9, 0, 1000)
        val bounds = deeplistWordBounds(layout, word)
        assertTrue(bounds.isNotEmpty())
        assertTrue(bounds.all { it.right >= it.left && it.bottom > it.top })
    }
}
