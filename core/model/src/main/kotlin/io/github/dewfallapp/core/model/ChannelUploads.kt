package io.github.dewfallapp.core.model

/**
 * A channel and its latest uploads, as the channel page and the inbox need them.
 *
 * [videos] holds the channel's latest long-form videos and live streams, newest first, and never
 * Shorts. A premiere or live stream that hasn't started is marked [VideoKind.UPCOMING]. The list
 * holds only the latest ones, not the whole channel.
 *
 * [channel]'s ID can differ from the one that was asked for, because YouTube sends some channels on
 * to another channel.
 */
data class ChannelUploads(val channel: Channel, val videos: List<VideoSummary>)
