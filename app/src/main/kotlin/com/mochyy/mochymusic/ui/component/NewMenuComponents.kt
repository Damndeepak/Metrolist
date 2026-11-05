// NewMenuComponents.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The actions at the top of a menu. The first two are the ones people use every time they open
 * this menu, so they're drawn as two big cards side by side, as in the sheet mock. Any more
 * follow as ordinary rows, the same as the rest of the menu (see Material3MenuGroup). Put the two
 * that matter most first. [columns] is accepted and ignored.
 */
@Composable
fun NewActionGrid(
    actions: List<NewAction>,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") columns: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val cards = actions.take(2)
        if (cards.isNotEmpty()) {
            SheetActionCardRow {
                cards.forEach { action ->
                    SheetActionCard(
                        icon = { WhiteIcon(24.dp, action.icon) },
                        title = action.text,
                        onClick = rememberTapTrigger(action),
                        enabled = action.enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        actions.drop(2).forEach { action ->
            MenuItemRow(
                icon = { WhiteIcon(SheetIconSize, action.icon) },
                enabled = action.enabled,
                onClick = rememberTapTrigger(action),
            ) {
                Text(text = action.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * [NewAction.onClick] is a composable, so a tap can't call it directly. The tap raises a flag,
 * the next composition runs the action once, and the flag drops again.
 */
@Composable
private fun rememberTapTrigger(action: NewAction): () -> Unit {
    var tapped by remember { mutableStateOf(false) }
    if (tapped) {
        action.onClick()
        LaunchedEffect(Unit) { tapped = false }
    }
    return { tapped = true }
}

/** The actions bring icons tinted for the old grey buttons; this draws them white whatever they ask for. */
@Composable
private fun WhiteIcon(size: Dp, icon: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer { colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn) },
    ) {
        icon()
    }
}

data class NewAction(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: @Composable () -> Unit,
    val enabled: Boolean = true,
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified
)
