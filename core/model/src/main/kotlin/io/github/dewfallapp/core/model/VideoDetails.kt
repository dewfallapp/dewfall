package io.github.dewfallapp.core.model

import kotlin.time.Duration

/**
 * What the video screen shows about one video. The titles, names, description and IDs are user
 * data, so never log them. See ADR 0007.
 *
 * It has no [VideoKind]. The YouTube layer can't read the details of a premiere or live stream that
 * hasn't started, and can't tell from a video's own page whether it is a Short. So those come from
 * the list the video was found in.
 */
data class VideoDetails(
    val id: VideoId,
    val title: String,
    val channelId: ChannelId,
    val channelName: String,
    /** Every size of the channel's avatar. Empty if YouTube gave none. */
    val channelAvatars: List<Thumbnail>,
    /** The description as plain text, or empty if it has none. */
    val description: String,
    /** The length, or null for a live stream or if unknown. */
    val duration: Duration?,
    /** When it was published, or null if unknown. */
    val published: PublishedTime?,
    /** Every size of the video's thumbnail. Empty if YouTube gave none. */
    val thumbnails: List<Thumbnail>,
    /**
     * True for a live stream that is on air, or that has just ended while YouTube still serves it
     * as a live stream.
     */
    val isLive: Boolean,
)
