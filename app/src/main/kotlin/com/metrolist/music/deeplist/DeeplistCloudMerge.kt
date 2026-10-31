package com.metrolist.music.deeplist

import org.json.JSONArray
import org.json.JSONObject

internal fun cloudEqual(a: Any?, b: Any?): Boolean = when {
    a === b -> true
    a is JSONObject && b is JSONObject -> a.keySet() == b.keySet() && a.keySet().all { cloudEqual(a.opt(it), b.opt(it)) }
    a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { cloudEqual(a.opt(it), b.opt(it)) }
    a is Number && b is Number -> a.toDouble() == b.toDouble()
    else -> a == b
}
private fun JSONObject.keySet() = keys().asSequence().toSet()

// Missing keys are tombstones relative to this device's last acknowledged snapshot.
internal fun mergeCloud(base: Any?, local: Any?, remote: Any?, firstSync: Boolean = false): Any? {
    if (cloudEqual(local, base)) return remote
    if (cloudEqual(remote, base) || cloudEqual(local, remote)) return local
    if (local is JSONObject && remote is JSONObject) {
        val before = base as? JSONObject
        return JSONObject().apply {
            (local.keySet() + remote.keySet() + before?.keySet().orEmpty()).sorted().forEach { key ->
                mergeCloud(before?.opt(key), local.opt(key), remote.opt(key), firstSync)?.let { put(key, it) }
            }
        }
    }
    if (local == null) return remote
    if (remote == null) return local
    return if (firstSync) remote else local
}
