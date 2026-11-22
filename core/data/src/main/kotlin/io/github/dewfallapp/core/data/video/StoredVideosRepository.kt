package io.github.dewfallapp.core.data.video

import io.github.dewfallapp.core.model.ChannelUploads

/**
 * The videos Dewfall keeps on the phone. Reads come with the tasks that need them, such as the
 * inbox (#49). See docs/architecture.md#the-database-repositories.
 *
 * Main-safe. A write finishes even when the caller's coroutine is cancelled while it runs. The
 * videos are user data, so never log them.
 */
fun interface StoredVideosRepository {
    /**
     * Saves [uploads]' videos, merged with what is saved, as the subscriptions spec says: a later,
     * vaguer publish time, a missing length or missing thumbnails never replace saved ones, the
     * time Dewfall first saved a video stays, and YouTube's order is kept for videos with the same
     * time. If [uploads]' channel is subscribed, its name, avatars and description are refreshed. A
     * channel that isn't subscribed stays that way, and no saved video is removed.
     */
    suspend fun saveUploads(uploads: ChannelUploads)
}
