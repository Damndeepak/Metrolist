package com.metrolist.music.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File

internal data class CapsuleShareData(
    val title: String,
    val month: String,
    val artistsLabel: String,
    val songsLabel: String,
    val minutesLabel: String,
    val minutes: Long,
    val artists: List<String>,
    val songs: List<String>,
)

internal fun capsuleShareCard(context: Context, data: CapsuleShareData): Intent {
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    var output: File? = null
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(22, 19, 28))
        val accent = Color.rgb(191, 160, 255)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        fun text(value: String, x: Float, y: Float, width: Int, size: Float, bold: Boolean = false, lines: Int = 1, color: Int = Color.WHITE) {
            paint.textSize = size
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            paint.color = color
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
        }
        text("deeplist", 70f, 80f, 400, 44f, true, color = accent)
        text(data.month, 600f, 90f, 410, 29f)
        text(data.title, 70f, 285f, 940, 88f, true, lines = 3)
        text(data.artistsLabel, 70f, 750f, 435, 43f, true)
        text(data.songsLabel, 575f, 750f, 435, 43f, true)
        data.artists.take(5).forEachIndexed { index, artist ->
            text("${index + 1}  $artist", 70f, 850f + index * 90, 435, 35f)
        }
        data.songs.take(5).forEachIndexed { index, song ->
            text("${index + 1}  $song", 575f, 850f + index * 90, 435, 35f)
        }
        text(data.minutesLabel, 70f, 1515f, 940, 42f, true)
        text(java.text.NumberFormat.getIntegerInstance().format(data.minutes), 70f, 1600f, 940, 110f, true, color = accent)
        val folder = File(context.cacheDir, "deeplist-capsule-cards").apply { check(isDirectory || mkdirs()) }
        folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(9)?.forEach { it.delete() }
        val file = File.createTempFile("capsule-", ".png", folder)
        output = file
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(data.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } catch (error: Exception) {
        output?.delete()
        throw error
    } finally { bitmap.recycle() }
}
