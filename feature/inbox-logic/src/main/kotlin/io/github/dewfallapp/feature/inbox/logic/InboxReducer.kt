package io.github.dewfallapp.feature.inbox.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The inbox's reducer. It groups the videos by channel, in the order each channel first appears, so
 * the card with the newest video comes first, and each card keeps the videos' order. The count of
 * channels and the videos come from two reads, which can disagree for a moment, such as a count of
 * 0 with the last videos while the user unsubscribes. The videos win: any video shows the cards,
 * and only an empty list looks at the count. The check for new videos is copied as it comes, and a
 * tap on "Check for new videos" changes nothing here: the ViewModel starts the check. The inbox has
 * no effects, so its effect type is Nothing.
 */
object InboxReducer : Reducer<InboxState, InboxIntent, Nothing> {
    override fun reduce(state: InboxState, intent: InboxIntent): Next<InboxState, Nothing> =
        Next(
            when (intent) {
                is InboxIntent.Loaded -> state.copy(content = content(intent))
                InboxIntent.ReadFailed -> state.copy(content = InboxContent.Unreadable)
                // The ViewModel starts the check. The state follows from the check's own state.
                InboxIntent.CheckForNewVideos -> state
                is InboxIntent.CheckChanged -> state.copy(check = intent.check)
            },
        )
}

private fun content(loaded: InboxIntent.Loaded): InboxContent =
    when {
        loaded.videos.isNotEmpty() -> InboxContent.Videos(grouped(loaded.videos))
        loaded.subscribedCount <= 0 -> InboxContent.NoChannels
        else -> InboxContent.CaughtUp(loaded.subscribedCount)
    }

/** [videos] grouped by channel, each group where its channel's first video was. */
private fun grouped(videos: List<VideoSummary>): List<InboxGroup> {
    val groups = LinkedHashMap<ChannelId, MutableList<VideoSummary>>()
    for (video in videos) {
        groups.getOrPut(video.channelId) { mutableListOf() } += video
    }
    return groups.map { (channelId, channelVideos) ->
        InboxGroup(channelId, channelVideos.first().channelName, channelVideos)
    }
}
