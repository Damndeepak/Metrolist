// TransferProgress.kt
//
// What the Library's progress card and progress sheet show, whichever job is running: songs being
// downloaded for offline, or files being imported from the phone.

package com.mochyy.mochymusic.models

import androidx.compose.runtime.Immutable

enum class TransferState { Waiting, Running, Done, Failed }

/** One song in a batch. */
@Immutable
data class TransferItem(
    /** The song's id, or for a file not imported yet, its uri. */
    val key: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val state: TransferState,
    /** 0..1 while [state] is [TransferState.Running]; a job that can't tell leaves it at 0. */
    val progress: Float = 0f,
    /** The song in the library once there is one, for "Create playlist". */
    val songId: String? = null,
)

/** Every song of one batch, in the order they were started. */
@Immutable
data class TransferBatch(val items: List<TransferItem> = emptyList()) {
    val isEmpty: Boolean get() = items.isEmpty()

    val isRunning: Boolean
        get() = items.any { it.state == TransferState.Waiting || it.state == TransferState.Running }

    val doneCount: Int get() = items.count { it.state == TransferState.Done }

    /** How far the whole batch is, 0..1. A failed song counts as finished, so the bar still fills. */
    val fraction: Float
        get() {
            if (items.isEmpty()) return 0f
            val sum = items.sumOf { item ->
                when (item.state) {
                    TransferState.Done, TransferState.Failed -> 1.0
                    TransferState.Running -> item.progress.coerceIn(0f, 1f).toDouble()
                    TransferState.Waiting -> 0.0
                }
            }
            return (sum / items.size).toFloat()
        }

    /** The songs that made it into the library, for saving the batch as a playlist. */
    val finishedSongIds: List<String>
        get() = items.mapNotNull { item -> item.songId.takeIf { item.state == TransferState.Done } }
}
