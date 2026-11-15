package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.VideoSummary

/**
 * Newest first, with no publish time last. Exact and approximate times are compared as they are. An
 * upcoming video's time is its scheduled start, which is in the future, so it comes first.
 */
private val NEWEST_FIRST: Comparator<VideoSummary> =
    compareBy(nullsLast(reverseOrder())) { it.published?.instant }

/**
 * A channel's latest uploads in the order that ChannelUploads states. [videos] holds the Videos
 * tab's videos, then the Live tab's, each in YouTube's order. A video in both tabs is kept once, as
 * the first copy. The sort is stable, so videos with the same time, and videos with no time, keep
 * that order.
 */
internal fun latestUploadsOrder(videos: List<VideoSummary>): List<VideoSummary> =
    videos.distinctBy { it.id }.sortedWith(NEWEST_FIRST)
