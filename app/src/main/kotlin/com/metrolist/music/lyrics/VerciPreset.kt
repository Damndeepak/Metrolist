package com.metrolist.music.lyrics

import com.metrolist.music.constants.VerciFont
import com.metrolist.music.constants.VerciStyle

internal data class VerciPreset(
    val style: VerciStyle, val font: VerciFont, val size: Float, val spacing: Float,
    val brightness: Float, val color: Int, val neon: Boolean, val emojis: Boolean, val reactive: Boolean,
    val advanced: Boolean = false, val ring: Boolean = false, val particles: Boolean = false, val clock: Boolean = false, val beatLift: Boolean = true, val mega: Boolean = true,
) {
    fun encode() = listOf(style.name, font.name, size, spacing, brightness, color, neon, emojis, reactive, advanced, ring, particles, clock, beatLift, mega).joinToString("|")
    companion object {
        fun decode(value: String): VerciPreset? = runCatching {
            val v = value.split('|')
            require(v.size == 9 || v.size == 13 || v.size == 15)
            VerciPreset(VerciStyle.valueOf(v[0]), VerciFont.valueOf(v[1]), v[2].toFloat(), v[3].toFloat(),
                v[4].toFloat(), v[5].toInt(), v[6].toBooleanStrict(), v[7].toBooleanStrict(), v[8].toBooleanStrict(),
                v.getOrNull(9)?.toBooleanStrict() ?: false, v.getOrNull(10)?.toBooleanStrict() ?: false,
                v.getOrNull(11)?.toBooleanStrict() ?: false, v.getOrNull(12)?.toBooleanStrict() ?: false,
                v.getOrNull(13)?.toBooleanStrict() ?: true, v.getOrNull(14)?.toBooleanStrict() ?: true).also {
                require(it.size in 28f..160f && it.spacing in .75f..1.35f && it.brightness in 0f..1f && it.color in 0..4)
            }
        }.getOrNull()
    }
}
