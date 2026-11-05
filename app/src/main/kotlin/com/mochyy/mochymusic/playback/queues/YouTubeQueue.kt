// YouTubeQueue.kt

package com.mochyy.mochymusic.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.NextResult
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.NotRecommended
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var failedPageLoads = 0

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null

            for (attempt in 0..MAX_RETRIES) {
                try {
                    var nextResult = YouTube.next(endpoint, continuation).getOrThrow()

                    // A bare videoId endpoint without personalization (fresh install, no account)
                    // often comes back as just the song itself, with no automix and no continuation.
                    // That's a dead-end queue, so switch to the song's radio mix instead.
                    if (nextResult.continuation == null && nextResult.items.size <= 1) {
                        radioEndpoint()?.let { radio ->
                            YouTube.next(radio).getOrNull()
                                ?.takeIf { it.items.size > nextResult.items.size }
                                ?.let { nextResult = it }
                        }
                    }

                    val mix = isMix()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    val items = nextResult.items.map { it.toMediaItem() }
                    val index = nextResult.currentIndexFor(preloadItem?.id ?: endpoint.videoId)
                    if (!mix) {
                        return@withContext Queue.Status(title = nextResult.title, items = items, mediaItemIndex = index)
                    }
                    // the song you started from still plays even if you hid it, only what youtube adds around it goes
                    val hidden = NotRecommended.songs
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = items.filterIndexed { i, item -> i == index || item.mediaId !in hidden },
                        mediaItemIndex = index - items.take(index).count { it.mediaId in hidden },
                    )
                } catch (e: Exception) {
                    lastException = e
                    radioEndpoint()?.let { endpoint = it }
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            val currentContinuation = continuation ?: return@withContext emptyList()
            var lastException: Throwable? = null

            for (attempt in 0..MAX_RETRIES) {
                try {
                    val nextResult = YouTube.next(endpoint, currentContinuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    failedPageLoads = 0
                    val items = nextResult.items.map { it.toMediaItem() }
                    return@withContext if (isMix()) items.filter { it.mediaId !in NotRecommended } else items
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
            // Only give up on the continuation after repeated failed loads, so one network
            // blip doesn't permanently end the queue.
            if (++failedPageLoads >= MAX_FAILED_PAGE_LOADS) {
                continuation = null
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    // a song on its own or a radio is youtube picking for you, curated (RDCLAK) and normal playlists and albums play as they are
    private fun isMix(): Boolean {
        val playlistId = endpoint.playlistId ?: return true
        return playlistId.startsWith("RD") && !playlistId.startsWith("RDCLAK")
    }

    private fun radioEndpoint(): WatchEndpoint? {
        val videoId = endpoint.videoId ?: preloadItem?.id ?: return null
        if (endpoint.playlistId != null) return null
        return WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")
    }

    private fun NextResult.currentIndexFor(videoId: String?): Int =
        currentIndex
            ?: videoId?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: 0

    companion object {
        private const val MAX_RETRIES = 2
        private const val MAX_FAILED_PAGE_LOADS = 3
        private const val RETRY_DELAY_MS = 750L

        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = song.id),
                song
            )
        }
    }
}
