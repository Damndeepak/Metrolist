package com.mochyy.mochymusic.module.applemusic

import java.util.Base64

/**
 * Apple Music's HLS playlists, as far as Widevine playback needs them.
 *
 * Apple also serves "enhanced HLS" (AAC, the binaural Spatial render, Atmos, ALAC) whose playlists
 * list Widevine keys beside FairPlay's, but its audio is labelled with an all-zero key id: FairPlay
 * ignores the label, Widevine matches on it and finds no key. Licenses for it are granted and then
 * decrypt nothing (seen on a phone, 2026-10-12), so only the web-playback stream is used.
 */
internal object AppleHls {

    const val WidevineKeyFormat = "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"

    class WidevineKey(
        /** the key's data: URI, which Apple's license server wants back with the challenge */
        val uri: String,
        /** the whole PSSH box, what the CDM builds its challenge from */
        val pssh: ByteArray,
        /** the key id inside it, found again inside a challenge to tell keys apart */
        val keyId: ByteArray,
    )

    fun segmentCount(media: String): Int = media.lines().count { it.isNotBlank() && !it.startsWith("#") }

    // ---- the older web-playback stream ("ctrp256") ----------------------------------------------
    //
    // About half of Apple's encodes carry no Widevine key in their enhanced HLS; for those, Apple's
    // web player in Chrome plays the older stream web playback hands out. Its key is a bare 16-byte
    // key id in a data URI, not a PSSH box, so the playlist is rewritten with a proper box for the
    // player, every other URI made absolute, and served to it as a data: URI.

    private val WidevineSystemId = byteArrayOf(
        0xed.toByte(), 0xef.toByte(), 0x8b.toByte(), 0xa9.toByte(), 0x79, 0xd6.toByte(), 0x4a, 0xce.toByte(),
        0xa3.toByte(), 0xc8.toByte(), 0x27, 0xdc.toByte(), 0xd5.toByte(), 0x1d, 0x21, 0xed.toByte(),
    )

    /** the stream's key: its URI as written (what the license server wants back) and the key id */
    fun legacyKey(media: String): Pair<String, ByteArray>? {
        val line = media.lines().firstOrNull { line ->
            line.startsWith("#EXT-X-KEY") &&
                (attribute(line, "KEYFORMAT") == WidevineKeyFormat || attribute(line, "URI")?.startsWith("data:") == true)
        } ?: return null
        val uri = attribute(line, "URI") ?: return null
        val payload = runCatching { Base64.getDecoder().decode(uri.substringAfter(',')) }.getOrNull() ?: return null
        val keyId = when {
            payload.size == 16 -> payload
            payload.size > 32 -> keyIdOf(payload) // already a box
            else -> return null
        }
        return uri to keyId
    }

    /** a Widevine PSSH box (version 0) whose data names [keyId], AES-CTR */
    fun psshFor(keyId: ByteArray): ByteArray {
        val data = byteArrayOf(0x08, 0x01, 0x12, keyId.size.toByte()) + keyId
        val size = 32 + data.size
        return java.nio.ByteBuffer.allocate(size)
            .putInt(size)
            .put("pssh".toByteArray())
            .putInt(0)
            .put(WidevineSystemId)
            .putInt(data.size)
            .put(data)
            .array()
    }

    /** the playlist the player opens: absolute URIs, and only the Widevine key, as a whole box */
    fun rewriteLegacy(media: String, base: String, keyUri: String, pssh: ByteArray): String {
        val psshUri = "data:text/plain;base64," + Base64.getEncoder().encodeToString(pssh)
        return media.lines().mapNotNull { line ->
            when {
                line.startsWith("#EXT-X-KEY") -> {
                    if (attribute(line, "URI") != keyUri) null
                    else {
                        """#EXT-X-KEY:METHOD=${playerMethod(attribute(line, "METHOD"))},URI="$psshUri",KEYFORMAT="$WidevineKeyFormat",KEYFORMATVERSIONS="1""""
                    }
                }
                line.startsWith("#EXT-X-MAP") -> {
                    val uri = attribute(line, "URI")
                    if (uri == null) line else line.replace("URI=\"$uri\"", "URI=\"${resolve(base, uri)}\"")
                }
                line.isNotBlank() && !line.startsWith("#") -> resolve(base, line.trim())
                else -> line
            }
        }.joinToString("\n")
    }

    /**
     * The encryption method as the player's playlist parser spells it. It knows only NONE,
     * AES-128, SAMPLE-AES (cbcs), SAMPLE-AES-CENC and SAMPLE-AES-CTR (cenc); Apple writes the
     * standard's own name for its AES-CTR streams ("ISO-23001-7"), which made the player refuse the
     * whole playlist ("Couldn't match METHOD=…", on the user's phone 2026-10-12). Web playback's
     * streams are all "ctrp", AES-CTR, so anything else is that.
     */
    internal fun playerMethod(method: String?): String = when (method) {
        "SAMPLE-AES", "SAMPLE-AES-CENC", "SAMPLE-AES-CTR" -> method
        else -> "SAMPLE-AES-CTR"
    }

    /** the key id in a Widevine PSSH: its data is a protobuf whose field 2 (0x12, 16 bytes) is the id */
    internal fun keyIdOf(pssh: ByteArray): ByteArray {
        val dataStart = 32
        for (i in dataStart until pssh.size - 17) {
            if (pssh[i] == 0x12.toByte() && pssh[i + 1] == 0x10.toByte()) return pssh.copyOfRange(i + 2, i + 18)
        }
        return pssh.copyOfRange(minOf(dataStart, pssh.size), pssh.size)
    }

    internal fun attribute(line: String, name: String): String? {
        val match = Regex("""(?:^|[:,])$name=("([^"]*)"|[^,]*)""").find(line) ?: return null
        return match.groupValues[2].ifEmpty { match.groupValues[1] }
    }

    private fun resolve(base: String, uri: String): String =
        if (uri.startsWith("http")) uri else base.substringBeforeLast('/') + "/" + uri
}

internal fun ByteArray.indexOf(needle: ByteArray): Int {
    if (needle.isEmpty() || needle.size > size) return -1
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}
