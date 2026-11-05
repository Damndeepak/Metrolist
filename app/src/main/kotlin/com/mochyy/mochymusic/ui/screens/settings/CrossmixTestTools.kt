package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.SettingsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


@Composable
fun crossmixTestItems(isVisible: Boolean = true): List<SettingsItem> {
    val playerConnection = LocalPlayerConnection.current
    val statusFlow: StateFlow<String> = remember(playerConnection) {
        playerConnection?.crossmixStatus ?: MutableStateFlow("")
    }
    val status by statusFlow.collectAsState()
    return listOf(
        SettingsItem(
            title = { Text("Crossmix status") },
            descriptionText = status.ifBlank { "Nothing yet. Play a song with another queued after it" },
            icon = painterResource(R.drawable.info),
            iconShape = androidx.compose.foundation.shape.CircleShape,
            isVisible = isVisible,
        ),
        SettingsItem(
            title = { Text("Test on this song") },
            highlightKey = "Test on this song",
            descriptionText = "Mixes into the next song",
            detail = "Plans the mix into the next song, then jumps to a few seconds before it",
            icon = painterResource(R.drawable.fast_forward),
            iconShape = androidx.compose.foundation.shape.CircleShape,
            enabled = playerConnection != null,
            isVisible = isVisible,
            onClick = { playerConnection?.testCrossmix() },
        ),
    )
}
