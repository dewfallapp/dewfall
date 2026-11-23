package io.github.dewfallapp.core.data.video

import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.VideoSummary
import kotlinx.coroutines.flow.Flow

/**
 * The videos Dewfall keeps on the phone. See docs/architecture.md#the-database-repositories.
 *
 * Main-safe. A write finishes even when the caller's coroutine is cancelled while it runs. The
 * videos are user data, so never log them.
 */
interface StoredVideosRepository {
    /**
     * Saves [uploads]' videos, merged with what is saved, as the subscriptions spec says: a later,
     * vaguer publish time, a missing length or missing thumbnails never replace saved ones, the
     * time Dewfall first saved a video stays, and YouTube's order is kept for videos with the same
     * time. If [uploads]' channel is subscribed, its name, avatars and description are refreshed. A
     * channel that isn't subscribed stays that way, and no saved video is removed.
     */
    suspend fun saveUploads(uploads: ChannelUploads)

    /**
     * The inbox's videos, as the inbox spec defines them and docs/architecture.md#database-tables
     * describes: videos that can be watched from start to end, of subscribed channels, not marked
     * watched, and new or started since subscribing. Newest first across every channel, with
     * YouTube's order for equal times. It emits again when a save, a subscription or a watch state
     * changes what it holds.
     */
    fun inboxVideos(): Flow<List<VideoSummary>>
}
