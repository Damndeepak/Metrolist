package com.metrolist.music.ui.component

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DeeplistSavedLyric(val text: String, val title: String, val artist: String, val songId: String = "", val position: Long = 0)

internal fun deeplistSavedLyrics(value: String): List<DeeplistSavedLyric> = runCatching {
    val array = JSONArray(value)
    List(array.length().coerceAtMost(30)) { i ->
        val item = array.getJSONObject(i)
        DeeplistSavedLyric(item.getString("text"), item.getString("title"), item.getString("artist"), item.optString("songId"), item.optLong("position"))
    }
}.getOrDefault(emptyList())

internal fun deeplistSaveLyric(value: String, lyric: DeeplistSavedLyric): String {
    val items = listOf(lyric) + deeplistSavedLyrics(value).filter { it != lyric }
    return JSONArray().apply { items.take(30).forEach { put(JSONObject().put("text",it.text).put("title",it.title).put("artist",it.artist).put("songId",it.songId).put("position",it.position)) } }.toString()
}

internal fun deeplistShareCard(context: Context, lyric: DeeplistSavedLyric): Intent {
    val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f,0f,900f,1200f,intArrayOf(Color.rgb(8,5,16),Color.rgb(35,17,50),Color.rgb(7,5,14)),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,900f,1200f,paint);paint.shader = null
        paint.color = Color.argb(50,192,158,255);paint.style = Paint.Style.STROKE;paint.strokeWidth = 2f
        canvas.drawCircle(450f,570f,325f,paint);paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT,Typeface.BOLD);paint.textSize = 30f;paint.color = Color.rgb(192,158,255)
        canvas.drawText("deeplist",75f,100f,paint)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE;typeface = Typeface.create(Typeface.DEFAULT,Typeface.BOLD);textSize = 62f }
        fun layout(text: String, width: Int) = StaticLayout.Builder.obtain(text,0,text.length,textPaint,width).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        var text = layout(lyric.text.take(600),720)
        while (text.height > 650 && textPaint.textSize > 30f) {textPaint.textSize -= 4f;text = layout(lyric.text.take(600),720)}
        canvas.save();canvas.translate(90f,(1120-text.height)/2f);text.draw(canvas);canvas.restore()
        textPaint.textSize = 27f;textPaint.color = Color.rgb(213,195,238)
        val credit = layout(lyric.title.take(100)+"\n"+lyric.artist.take(100),720)
        canvas.save();canvas.translate(90f,1000f);credit.draw(canvas);canvas.restore()
        val folder = File(context.cacheDir,"deeplist-lyric-cards").apply { mkdirs() }
        folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(9)?.forEach { it.delete() }
        val file = File(folder,"lyric-${System.currentTimeMillis()}.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        val uri = FileProvider.getUriForFile(context,"${context.packageName}.FileProvider",file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png";putExtra(Intent.EXTRA_STREAM,uri);clipData = ClipData.newRawUri("Lyric card",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } finally { bitmap.recycle() }
}
