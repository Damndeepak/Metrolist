// settings → storage: how much of the phone mochy takes, the cache limit, clearing downloads or the cache, and manage downloads

package com.mochyy.mochymusic.ui.screens.settings.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.MaxSongCacheSizeKey
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.library.showConfirm
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.StorageUsage
import com.mochyy.mochymusic.viewmodels.StorageViewModel

const val StorageRoute = "storage_settings"

/** song cache limits on offer, in mb, -1 is no limit, the player's default is 1 gb */
private val CacheLimitSteps = listOf(0, 256, 512, 1024, 2048, 4096, 8192, -1)

@Composable
fun StorageScreen(navController: NavController, viewModel: StorageViewModel = hiltViewModel()) {
    val usage by viewModel.usage.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val (cacheLimit, setCacheLimit) = rememberPreference(MaxSongCacheSizeKey, 1024)
    // the player picked its limit when the app started; a new one only applies from the next start
    val startingLimit = remember { cacheLimit }
    val menuState = LocalMenuState.current

    // songs removed from the Manage downloads sheet change the numbers here too
    LaunchedEffect(downloads?.size) { viewModel.measure() }

    SubSettingsScaffold(title = "Storage", onBack = { navController.navigateUp() }) {
        OverallCard(usage)

        Spacer(Modifier.height(22.dp))
        Text(
            usage?.let { "Mochy uses ${percentOf(it.mochy, it.deviceTotal)} of your storage (${formatSize(it.mochy)}), which is split into" }
                ?: "Working out how much room Mochy takes…",
            style = storageText(17f, FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 2.dp),
        )

        Spacer(Modifier.height(14.dp))
        BreakdownCard(usage)

        Spacer(Modifier.height(26.dp))
        Text(
            "So, here's a list of things you can do to cut the junk",
            style = storageText(17f, FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        usage?.let { SmartHint(it, downloadCount = downloads?.size ?: 0) }

        Spacer(Modifier.height(14.dp))
        CacheLimitCard(
            limit = cacheLimit,
            onLimitChange = setCacheLimit,
            appliesLater = cacheLimit != startingLimit,
        )

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val downloadCount = downloads?.size ?: 0
            StorageActionButton(
                label = "Remove all downloads",
                detail = if (downloadCount > 0) "$downloadCount ${if (downloadCount == 1) "song" else "songs"} · ${formatSize(usage?.downloads ?: 0)}" else "Nothing downloaded",
                danger = true,
                enabled = !busy && downloadCount > 0,
                onClick = {
                    menuState.showConfirm(
                        title = "Remove all downloads?",
                        message = "Every downloaded song is deleted from this phone. They stay in your library " +
                            "and stream again when you play them, so they'll need a connection.",
                        action = "Remove all downloads",
                        onConfirm = viewModel::removeAllDownloads,
                    )
                },
            )
            StorageActionButton(
                label = "Remove all music & cover cache",
                detail = usage?.let { formatSize(it.cache) },
                danger = true,
                enabled = !busy,
                onClick = {
                    menuState.showConfirm(
                        title = "Clear the cache?",
                        message = "Songs kept from streaming and saved cover pictures are deleted. They come back " +
                            "as you play and browse. Your library and your downloads stay as they are.",
                        action = "Clear cache",
                        onConfirm = viewModel::clearCaches,
                    )
                },
            )
            StorageActionButton(
                label = "Manage downloads",
                detail = if (downloadCount > 0) "See the biggest and the ones you barely play" else null,
                enabled = downloadCount > 0,
                onClick = { menuState.showManageDownloadsSheet(viewModel) },
            )
        }

        Spacer(Modifier.height(26.dp))
        TransferSection()
    }
}

/** like 1.4%, or <1% for a sliver, so a small app never reads as using nothing */
private fun percentOf(part: Long, total: Long): String {
    if (total <= 0L) return "0%"
    val p = part * 100.0 / total
    return when {
        p < 0.1 -> "<0.1%"
        p < 10 -> String.format("%.1f%%", p)
        else -> "${p.toInt()}%"
    }
}

@Composable
private fun OverallCard(usage: StorageUsage?) {
    StorageCardColumn {
        Text("Overall", style = storageText(17f))
        Spacer(Modifier.height(14.dp))
        UsageBar(
            parts = listOf(
                BarPart(MochyGreen, usage?.mochy ?: 0),
                BarPart(OthersGrey, usage?.othersUsed ?: 0),
            ),
            total = usage?.deviceTotal ?: 0,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LegendSwatch(MochyGreen)
            Spacer(Modifier.width(8.dp))
            Text("Mochy", style = storageText(16f, FontWeight.SemiBold))
            Spacer(Modifier.width(16.dp))
            LegendSwatch(OthersGrey)
            Spacer(Modifier.width(8.dp))
            Text("Your storage", style = storageText(16f, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            if (usage != null) {
                Text("${formatSize(usage.deviceFree)} free", style = storageText(12f, FontWeight.Medium, StorageMuted, 0f))
            }
        }
    }
}

@Composable
private fun BreakdownCard(usage: StorageUsage?) {
    StorageCardColumn {
        Text("Mochy app data usage", style = storageText(17f))
        Spacer(Modifier.height(14.dp))
        UsageBar(
            parts = listOf(
                BarPart(CacheOrange, usage?.cache ?: 0),
                BarPart(MochyGreen, usage?.downloads ?: 0),
                BarPart(DatabaseRed, usage?.database ?: 0),
                BarPart(AppDataBlue, usage?.appData ?: 0),
            ),
            total = usage?.mochy ?: 0,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendRow(CacheOrange, "Cache", usage?.let { formatSize(it.cache) })
            LegendRow(MochyGreen, "Downloaded music & local", usage?.let { formatSize(it.downloads) })
            LegendRow(DatabaseRed, "Database", usage?.let { formatSize(it.database) })
            LegendRow(AppDataBlue, "App data", usage?.let { formatSize(it.appData) })
        }
    }
}

/** one line pointing at the biggest easy win: the cache, the downloads sheet, or a nudge when the phone is nearly full */
@Composable
private fun SmartHint(usage: StorageUsage, downloadCount: Int) {
    val nearlyFull = usage.deviceTotal > 0 && usage.deviceFree * 10 < usage.deviceTotal
    val hint = when {
        usage.cache >= usage.downloads && usage.cache > 50_000_000L ->
            "The cache is the easy one: clearing it frees ${formatSize(usage.cache)} and songs simply stream again."
        downloadCount > 0 && usage.downloads > 50_000_000L ->
            "Downloads take the most room. Manage downloads shows the biggest ones and the ones you barely play."
        else -> null
    }
    val text = listOfNotNull(if (nearlyFull) "Your phone is nearly full." else null, hint).joinToString(" ")
    if (text.isEmpty()) return
    Text(
        text,
        style = storageText(13f, FontWeight.Medium, StorageMuted, -0.2f),
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp),
    )
}

@Composable
private fun CacheLimitCard(limit: Int, onLimitChange: (Int) -> Unit, appliesLater: Boolean) {
    StorageCardColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDADADA)),
            ) {
                Icon(painterResource(R.drawable.cached), contentDescription = null, tint = StorageCard, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Set music cache limit", style = storageText(15f, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(cacheLimitLabel(limit), style = storageText(15f, FontWeight.Bold))
        }
        Spacer(Modifier.height(10.dp))
        StepSlider(steps = CacheLimitSteps, value = limit, onValueChange = onLimitChange)
        Row {
            Text("No cache", style = storageText(10f, FontWeight.Medium, StorageMuted, 0f), modifier = Modifier.weight(1f))
            Text("Unlimited", style = storageText(10f, FontWeight.Medium, StorageMuted, 0f))
        }
        AnimatedVisibility(
            visible = appliesLater,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                "The new limit starts next time you open Mochy.",
                style = storageText(12f, FontWeight.Medium, StorageMuted, -0.1f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun cacheLimitLabel(megabytes: Int): String = when {
    megabytes < 0 -> "Unlimited"
    megabytes == 0 -> "Off"
    megabytes < 1024 -> "$megabytes MB"
    else -> "${megabytes / 1024} GB"
}
