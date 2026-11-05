package com.mochyy.mochymusic.playback.custom

data class CustomStreamResult(
    val streamUrl: String,
    val decryptionKey: String? = null,
    val keyId: String? = null,
    val isDash: Boolean = false,
    val source: String? = null,
    /** an HLS playlist rather than a file (source modules) */
    val isHls: Boolean = false,
    /** Widevine-protected: trades the device CDM's challenge for a license (source modules) */
    val widevineLicense: com.mochyy.mochymusic.module.LicenseServer? = null,
    /**
     * The key the stream is cached under, when it isn't the song's media id: a source module's
     * stream has one of its own, so it never shares bytes with the song's YouTube stream.
     */
    val cacheKey: String? = null,
)
