package io.github.dewfallapp.core.model

/**
 * A subscribed channel with the publish time of its last upload, as the Channels screen lists it.
 * The subscription and the time are user data, so never log them. See ADR 0007.
 */
data class ChannelOverview(
    val subscription: SubscribedChannel,
    /**
     * When the newest saved video of the channel that has come out was published, Shorts and live
     * streams included, premieres and live streams that haven't started left out. Null when no
     * saved video of the channel counts, such as before any save of its uploads.
     */
    val lastUpload: PublishedTime?,
)
