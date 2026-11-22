package io.github.dewfallapp.core.data.watch

import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.WatchState
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/**
 * What the user did with each video: the position to resume from, and whether and when they marked
 * it watched. It works for any video ID, whether or not the video or its channel is saved. See
 * docs/architecture.md#the-database-repositories.
 *
 * Main-safe. A write finishes even when the caller's coroutine is cancelled while it runs. The IDs
 * and watch states are user data, so never log them.
 */
interface WatchStateRepository {
    /** The watch state of the video with [videoId], or null if it was never played or marked. */
    fun watchState(videoId: VideoId): Flow<WatchState?>

    /**
     * Saves [position] as where the video with [videoId] was left, now. A position below zero is
     * saved as zero. Whether it is marked watched stays as it was.
     */
    suspend fun savePosition(videoId: VideoId, position: Duration)

    /**
     * Marks the video with [videoId] watched now, or keeps the first time if it is already marked.
     * Its position stays as it was.
     */
    suspend fun markWatched(videoId: VideoId)

    /** Marks the video with [videoId] unwatched, and keeps its position. */
    suspend fun markUnwatched(videoId: VideoId)
}
