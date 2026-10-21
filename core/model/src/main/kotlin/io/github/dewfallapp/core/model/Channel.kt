package io.github.dewfallapp.core.model

/**
 * A YouTube channel, as search results and the channel page show it. The name, description and ID
 * are user data, so never log them. See ADR 0007.
 */
data class Channel(
    val id: ChannelId,
    val name: String,
    /** Every size of the channel's avatar. Empty if YouTube gave none. */
    val avatars: List<Thumbnail>,
    /** The channel's description as plain text, or empty if it has none. */
    val description: String,
)
