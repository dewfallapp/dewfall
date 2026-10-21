package io.github.dewfallapp.core.model

import kotlin.time.Duration

/**
 * A video as a list shows it, such as in search results or in a channel's latest uploads. The
 * titles, names and IDs are user data, so never log them. See ADR 0007.
 */
data class VideoSummary(
    val id: VideoId,
    val title: String,
    val channelId: ChannelId,
    val channelName: String,
    /** The length, or null for a live stream, a video that hasn't started, or if unknown. */
    val duration: Duration?,
    /** When it was published or is scheduled to start, or null if unknown. */
    val published: PublishedTime?,
    /** Every size of the video's thumbnail. Empty if YouTube gave none. */
    val thumbnails: List<Thumbnail>,
    val kind: VideoKind,
)
