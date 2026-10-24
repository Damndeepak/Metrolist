package com.metrolist.music.ui.component

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

internal fun deeplistApplePaint(requestedSizePx: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textSize = requestedSizePx
    typeface = Typeface.create("sans-serif", Typeface.BOLD)
}

private class DeeplistSpaceSpan(private val factor: Float) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        (paint.measureText(text, start, end) * factor).roundToInt().coerceAtLeast(1)

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
        top: Int, y: Int, bottom: Int, paint: Paint) = Unit
}

internal fun deeplistSpacedText(text: String, spacing: Float): CharSequence {
    if (spacing == 1f) return text
    return SpannableString(text).apply {
        text.forEachIndexed { index, c ->
            if (c == ' ') setSpan(DeeplistSpaceSpan(spacing.coerceIn(.5f, 1.4f)), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

internal fun deeplistWordBounds(layout: Layout, word: DeeplistMotionWord): List<RectF> {
    val first = layout.getLineForOffset(word.startChar)
    val last = layout.getLineForOffset((word.endChar - 1).coerceAtLeast(word.startChar))
    return (first..last).map { row ->
        val start = maxOf(word.startChar, layout.getLineStart(row))
        val end = minOf(word.endChar, layout.getLineEnd(row))
        val x1 = layout.getPrimaryHorizontal(start)
        val x2 = if (end == layout.getLineEnd(row)) {
            if (layout.getParagraphDirection(row) < 0) layout.getLineLeft(row) else layout.getLineRight(row)
        } else layout.getPrimaryHorizontal(end)
        RectF(minOf(x1, x2), layout.getLineTop(row).toFloat(), maxOf(x1, x2), layout.getLineBottom(row).toFloat())
    }
}
