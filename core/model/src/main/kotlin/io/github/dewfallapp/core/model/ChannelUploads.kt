package io.github.dewfallapp.core.model

/**
 * A channel and its latest uploads, as the channel page and the inbox need them.
 *
 * [videos] holds the channel's latest long-form videos and live streams, and never Shorts. A
 * premiere or live stream that hasn't started is marked [VideoKind.UPCOMING]. The list holds only
 * the latest ones, not the whole channel.
 *
 * The videos are sorted by [VideoSummary.published], newest first. Exact and approximate times are
 * compared as they are. An upcoming video's time is its scheduled start, which is in the future, so
 * upcoming videos with a scheduled start come first. Videos with no publish time come last. Videos
 * with the same time, and videos with no time, keep the order YouTube gave, with the channel's
 * Videos tab before its Live tab. A caller that wants another order, such as the inbox, sorts its
 * own copy.
 *
 * [channel]'s ID can differ from the one that was asked for, because YouTube sends some channels on
 * to another channel.
 */
data class ChannelUploads(val channel: Channel, val videos: List<VideoSummary>)
