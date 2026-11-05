package com.metrolist.music.lyrics

import com.metrolist.music.constants.VerciStyle
import org.json.JSONArray
import org.json.JSONObject

internal data class DeeplistCue(val time: Long, val style: VerciStyle)
internal fun deeplistCues(raw: String, track: String): List<DeeplistCue> = runCatching {
    val array = JSONObject(raw).optJSONArray(track) ?: return@runCatching emptyList()
    List(array.length().coerceAtMost(100)) { i -> array.getJSONObject(i).let { DeeplistCue(it.getLong("time").coerceAtLeast(0), VerciStyle.valueOf(it.getString("style"))) } }.sortedBy { it.time }
}.getOrDefault(emptyList())
internal fun deeplistPutCue(raw: String, track: String, time: Long, style: VerciStyle): String {
    val root = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    require(root.has(track) || root.length() < 300)
    val cues = (deeplistCues(raw, track).filter { kotlin.math.abs(it.time - time) > 500 } + DeeplistCue(time.coerceAtLeast(0), style)).sortedBy { it.time }.take(100)
    root.put(track, JSONArray().apply { cues.forEach { put(JSONObject().put("time", it.time).put("style", it.style.name)) } })
    return root.toString()
}
internal fun deeplistSinger(raw: String, track: String, time: Long): Int = runCatching { JSONObject(raw).optJSONObject(track)?.optInt(time.toString(), 0) ?: 0 }.getOrDefault(0).coerceIn(0, 2)
