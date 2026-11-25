package io.github.dewfallapp.feature.inbox.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.UploadsCheck
import io.github.dewfallapp.core.model.UploadsCheckFailure
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

/**
 * One card of the inbox: a channel and its videos in the inbox, newest first. The name is the one
 * its newest video was saved with, which every save refreshes. The IDs, name and titles are user
 * data, so never log them.
 */
data class InboxGroup(
    val channelId: ChannelId,
    val channelName: String,
    val videos: List<VideoSummary>,
)

/** What the inbox shows, once the phone has answered. */
sealed interface InboxContent {
    /** One card per channel, the card with the newest video first. Never empty. */
    data class Videos(val groups: List<InboxGroup>) : InboxContent

    /** The user subscribes to no channel. */
    data object NoChannels : InboxContent

    /**
     * The user subscribes to [channelCount] channels, at least one, and nothing is in the inbox.
     */
    data class CaughtUp(val channelCount: Int) : InboxContent

    /** The phone couldn't read the subscriptions or the saved videos. */
    data object Unreadable : InboxContent
}

/**
 * The inbox's state. [content] is null until the phone's first answer, for the moment before Room
 * answers, so the screen draws nothing yet. [check] is the app's check for new videos, as the
 * ViewModel last read it.
 */
data class InboxState(
    val content: InboxContent? = null,
    val check: UploadsCheck = UploadsCheck(),
) : MviState {
    /**
     * Whether the top bar shows "Check for new videos": only while the user subscribes to a channel
     * and the inbox could be read.
     */
    val offersCheck: Boolean
        get() = content is InboxContent.Videos || content is InboxContent.CaughtUp

    /** Whether the line under "Dewfall" reads "Checking for new videos" instead of "Inbox". */
    val checking: Boolean
        get() = check.running

    /**
     * Why the last check stopped early, for the banner, or null when no banner shows: while a check
     * runs, after one that didn't stop early, or when the inbox offers no check.
     */
    val banner: UploadsCheckFailure?
        get() = if (offersCheck && !check.running) check.failure else null
}

/**
 * What happens to the inbox. [CheckForNewVideos] comes from the user, and the others are results of
 * the ViewModel's reads.
 */
sealed interface InboxIntent : MviIntent {
    /** The user tapped "Check for new videos", or "Try again" on the banner. */
    data object CheckForNewVideos : InboxIntent

    /** The app's check for new videos is now [check]. */
    data class CheckChanged(val check: UploadsCheck) : InboxIntent

    /**
     * The phone answered: the user subscribes to [subscribedCount] channels, and [videos] are the
     * inbox's videos, newest first across every channel.
     */
    data class Loaded(val subscribedCount: Int, val videos: List<VideoSummary>) : InboxIntent

    /** One of the two reads threw, such as on a damaged database. */
    data object ReadFailed : InboxIntent
}
