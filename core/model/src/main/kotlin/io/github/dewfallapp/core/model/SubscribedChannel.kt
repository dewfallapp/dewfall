package io.github.dewfallapp.core.model

import kotlin.time.Instant

/**
 * A channel the user subscribes to, as Dewfall keeps it on the phone. The channel and the time are
 * user data, so never log them. See ADR 0007.
 */
data class SubscribedChannel(
    /**
     * The channel's ID, name, avatars and description. The avatars and description are empty until
     * a save of the channel's uploads fills them, such as after a subscription from a file.
     */
    val channel: Channel,
    /** When the user subscribed. Subscribing again keeps the first time. */
    val subscribedAt: Instant,
)
