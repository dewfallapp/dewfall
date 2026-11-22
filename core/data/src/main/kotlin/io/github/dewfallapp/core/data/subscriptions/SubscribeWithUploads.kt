package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelUploads

/**
 * Subscribes to a channel and saves the uploads that the caller already has, as one unit, so the
 * subscribed channel's videos reach the inbox with no request of their own. See
 * docs/architecture.md#the-database-repositories.
 *
 * Main-safe. The whole unit finishes even when the caller's coroutine is cancelled while it runs.
 * The channel and its videos are user data, so never log them.
 */
fun interface SubscribeWithUploads {
    /**
     * Subscribes to [channel], as SubscriptionsRepository.subscribe does, and only after that saves
     * [uploads], if there are any, as StoredVideosRepository.saveUploads does. The order matters to
     * the inbox: every video the save brings is first saved after the time of subscribing, so the
     * save counts as the first since subscribing.
     */
    suspend fun subscribe(channel: Channel, uploads: ChannelUploads?)
}
