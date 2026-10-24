package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.constants.WordstormEnabledKey
import com.metrolist.music.constants.VerciEmojiKey
import com.metrolist.music.constants.VerciLyricsKey
import com.metrolist.music.constants.VerciNeonKey
import com.metrolist.music.constants.VerciStyle
import com.metrolist.music.constants.VerciStyleKey
import com.metrolist.music.constants.VerciTextSizeKey
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
fun verciLyricsSettingsItems(): List<Material3SettingsItem> {
    val (enabled, setEnabled) = rememberPreference(VerciLyricsKey, false)
    val (_, setWordstorm) = rememberPreference(WordstormEnabledKey, true)
    val enable: (Boolean) -> Unit = { value ->
        if (value) setWordstorm(true)
        setEnabled(value)
    }
    val (style, setStyle) = rememberEnumPreference(VerciStyleKey, VerciStyle.FISHEYE)
    val (neon, setNeon) = rememberPreference(com.metrolist.music.constants.deeplistNeonKey(style), style != VerciStyle.APPLE)
    val (emojis, setEmojis) = rememberPreference(VerciEmojiKey, true)
    val (legacySize, _) = rememberPreference(VerciTextSizeKey, 52f)
    val (fontSize, setFontSize) = rememberPreference(com.metrolist.music.constants.deeplistSizeKey(style), if (style == VerciStyle.APPLE) 32f else legacySize)
    var styleDialog by remember { mutableStateOf(false) }
    var sizeDialog by remember { mutableStateOf(false) }
    if (styleDialog) {
        EnumDialog(
            onDismiss = { styleDialog = false },
            onSelect = { setStyle(it); styleDialog = false },
            title = stringResource(R.string.verci_style),
            current = style,
            values = VerciStyle.entries,
            valueText = { verciStyleName(it) },
        )
    }
    if (sizeDialog) {
        var size by remember { mutableFloatStateOf(fontSize) }
        DefaultDialog(
            onDismiss = { sizeDialog = false },
            buttons = {
                TextButton(onClick = { size = if (style == VerciStyle.APPLE) 32f else 52f }) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = { sizeDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { setFontSize(size); sizeDialog = false }) { Text(stringResource(android.R.string.ok)) }
            },
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.verci_text_size))
                Text("${size.roundToInt()} sp")
                Slider(value = size, onValueChange = { size = it }, valueRange = if (style == VerciStyle.APPLE) 24f..96f else 28f..200f)
            }
        }
    }
    val studio = verciStudioItems()
    val icon = painterResource(R.drawable.lyrics)
    return buildList {
        if (enabled) add(Material3SettingsItem(title = { VerciStylePreview() }))
        add(Material3SettingsItem(
            icon = icon,
            title = { Text(stringResource(R.string.verci_lyrics)) },
            description = { Text(stringResource(R.string.verci_lyrics_desc)) },
            trailingContent = { Switch(checked = enabled, onCheckedChange = enable) },
            onClick = { enable(!enabled) },
        ))
        addAll(studio)
        if (enabled) {
            add(Material3SettingsItem(
                icon = icon,
                title = { Text(stringResource(R.string.verci_style)) },
                description = { Text(verciStyleName(style)) },
                onClick = { styleDialog = true },
            ))
            add(Material3SettingsItem(
                icon = icon,
                title = { Text(stringResource(R.string.verci_text_size)) },
                description = { Text("${fontSize.roundToInt()} sp") },
                onClick = { sizeDialog = true },
            ))
            add(Material3SettingsItem(
                icon = icon,
                title = { Text(stringResource(R.string.verci_neon)) },
                description = { Text(stringResource(R.string.verci_neon_desc)) },
                trailingContent = { Switch(checked = neon, onCheckedChange = setNeon) },
                onClick = { setNeon(!neon) },
            ))
            add(Material3SettingsItem(
                icon = icon,
                title = { Text(stringResource(R.string.verci_emojis)) },
                trailingContent = { Switch(checked = emojis, onCheckedChange = setEmojis) },
                onClick = { setEmojis(!emojis) },
            ))
        }
    }
}

@Composable
private fun verciStyleName(style: VerciStyle): String = stringResource(when (style) {
    VerciStyle.FISHEYE -> R.string.verci_fisheye
    VerciStyle.SHIP -> R.string.verci_ship
    VerciStyle.VISUAL -> R.string.verci_visual
    VerciStyle.FISHEYE_VISUAL -> R.string.verci_fisheye_visual
    VerciStyle.APPLE -> R.string.deeplist_apple_style
    VerciStyle.NEON_STACK -> R.string.deeplist_neon_stack
})
