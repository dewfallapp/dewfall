package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.SubscribedChannel
import kotlinx.coroutines.flow.Flow

/**
 * The channels the user subscribes to, kept on the phone. Features reach the database only through
 * repositories like this one. See docs/architecture.md#the-database-repositories.
 *
 * Main-safe. A write finishes even when the caller's coroutine is cancelled while it runs. The
 * channels and their IDs are user data, so never log them.
 */
interface SubscriptionsRepository {
    /** Every subscribed channel, by name. It emits again when one is added, changed or removed. */
    fun subscribedChannels(): Flow<List<SubscribedChannel>>

    /**
     * Every subscribed channel with the publish time of its last upload, by name as the phone's
     * language sorts it, ignoring case, then by channel ID. It emits again when a subscription or a
     * saved video changes, and reads the phone's language at each emission.
     */
    fun channelOverviews(): Flow<List<ChannelOverview>>

    /** The subscribed channel with [channelId], or null while it isn't subscribed. */
    fun subscribedChannel(channelId: ChannelId): Flow<SubscribedChannel?>

    /** Whether the channel with [channelId] is subscribed. */
    fun isSubscribed(channelId: ChannelId): Flow<Boolean>

    /**
     * Subscribes to [channel] now, with its name, avatars and description. If it is already
     * subscribed, nothing changes, and the first time of subscribing stays.
     */
    suspend fun subscribe(channel: Channel)

    /**
     * Subscribes to every one of [channels] in one step, all with the same time of subscribing,
     * now, and returns how many weren't subscribed before. A channel that is already subscribed
     * keeps its details and its first time. Used by the Takeout import.
     */
    suspend fun subscribeAll(channels: List<Channel>): Int

    /**
     * Unsubscribes from the channel with [channelId]. Its saved videos and what the user watched of
     * them stay. A channel that isn't subscribed stays that way.
     */
    suspend fun unsubscribe(channelId: ChannelId)
}
