// StreamFacts.kt

package com.mochyy.mochymusic.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.db.entities.FormatEntity

/** What a song is actually playing as, for the player's format badge. */
data class StreamFacts(
    /** "OPUS", "AAC", "FLAC"...: the decoder's own format once it's known, the provider's before */
    val codec: String? = null,
    /** samples per second, as the decoder is given them */
    val sampleRate: Int? = null,
    /** from a source module, so the song's YouTube format record doesn't describe it */
    val fromModule: Boolean = false,
) {
    /**
     * "OPUS · 160 kbps" for Discord's {quality}. The bitrate comes from YouTube's record of the
     * stream; without one (lossless files, modules) the sample rate stands in: "FLAC · 44.1 kHz".
     */
    fun describe(youtubeFormat: FormatEntity?): String? {
        val kbps = youtubeFormat?.bitrate?.takeIf { it > 0 }?.let { "${(it + 500) / 1000} kbps" }
        val rate = sampleRate?.let { String.format(java.util.Locale.US, "%.1f kHz", it / 1000f) }
        return listOfNotNull(codec, kbps ?: rate).joinToString(" · ").ifEmpty { null }
    }

    companion object {
        /** the badge's names for a decoder format */
        fun codecOf(mimeType: String?, codecs: String? = null): String? {
            val mime = mimeType?.lowercase().orEmpty()
            val codec = codecs?.lowercase().orEmpty()
            return when {
                mime == MimeTypes.AUDIO_OPUS || codec.startsWith("opus") -> "OPUS"
                mime == MimeTypes.AUDIO_FLAC || codec == "flac" -> "FLAC"
                mime == MimeTypes.AUDIO_ALAC || codec == "alac" -> "ALAC"
                mime == MimeTypes.AUDIO_E_AC3_JOC || codec == "ec+3" -> "DOLBY ATMOS"
                mime == MimeTypes.AUDIO_E_AC3 || codec == "ec-3" -> "E-AC-3"
                mime == MimeTypes.AUDIO_AC3 || codec == "ac-3" -> "AC-3"
                mime == MimeTypes.AUDIO_AC4 || codec.startsWith("ac-4") -> "AC-4"
                // mp4a.40.5 and .29 are HE-AAC, the rest plain AAC
                codec.startsWith("mp4a.40.5") || codec.startsWith("mp4a.40.29") -> "HE-AAC"
                mime == MimeTypes.AUDIO_AAC || codec.startsWith("mp4a") -> "AAC"
                mime == MimeTypes.AUDIO_VORBIS || codec == "vorbis" -> "VORBIS"
                mime == MimeTypes.AUDIO_MPEG || codec == "mp3" -> "MP3"
                mime == MimeTypes.AUDIO_RAW || mime == MimeTypes.AUDIO_WAV -> "PCM"
                // YouTube's container types, when its record has no codec
                mime == "audio/webm" -> "OPUS"
                mime == "audio/mp4" -> "AAC"
                else -> null
            }
        }

        /** from YouTube's record of the format it handed over */
        fun of(format: FormatEntity): StreamFacts = StreamFacts(
            codec = codecOf(format.mimeType, format.codecs),
            sampleRate = format.sampleRate?.takeIf { it > 0 },
        )

        /** the selected audio track's format, as the player has it */
        @OptIn(UnstableApi::class)
        fun selectedAudio(tracks: Tracks): Format? {
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (i in 0 until group.length) if (group.isTrackSelected(i)) return group.getTrackFormat(i)
            }
            return null
        }
    }
}
