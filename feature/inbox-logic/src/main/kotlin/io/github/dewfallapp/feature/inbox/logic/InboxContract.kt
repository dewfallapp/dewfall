package io.github.dewfallapp.feature.inbox.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.UploadsCheck
import io.github.dewfallapp.core.model.UploadsCheckFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

/**
 * One card of the inbox: a channel and its videos in the inbox, newest first. The name is the one
 * its newest video was saved with, which every save refreshes. The avatars are the subscribed
 * channel's saved ones, every size, or none before a save of its uploads brings them. The IDs,
 * name, titles and image links are user data, so never log them.
 */
data class InboxGroup(
    val channelId: ChannelId,
    val channelName: String,
    val channelAvatars: List<Thumbnail>,
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
 * ViewModel last read it. [downloads] is every video's download status, or null while the phone
 * hasn't answered or can't read them, when the rows show no download control.
 */
data class InboxState(
    val content: InboxContent? = null,
    val check: UploadsCheck = UploadsCheck(),
    val downloads: Map<VideoId, DownloadStatus>? = null,
) : MviState {
    /**
     * The download status that the row of the video with [videoId] shows, or null for none while
     * the statuses can't be read. A video with no download is NotDownloaded.
     */
    fun downloadOf(videoId: VideoId): DownloadStatus? = downloads?.let {
        it[videoId] ?: DownloadStatus.NotDownloaded
    }

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
 * What happens to the inbox. [CheckForNewVideos], [Download] and [PauseDownload] come from the
 * user, and the others are results of the ViewModel's reads.
 */
sealed interface InboxIntent : MviIntent {
    /** The user tapped "Check for new videos", or "Try again" on the banner. */
    data object CheckForNewVideos : InboxIntent

    /** The app's check for new videos is now [check]. */
    data class CheckChanged(val check: UploadsCheck) : InboxIntent

    /**
     * The phone answered: the user subscribes to [subscribedCount] channels, whose saved avatars
     * are [avatars] by channel ID, and [videos] are the inbox's videos, newest first across every
     * channel.
     */
    data class Loaded(
        val subscribedCount: Int,
        val avatars: Map<ChannelId, List<Thumbnail>>,
        val videos: List<VideoSummary>,
    ) : InboxIntent

    /** One of the two reads threw, such as on a damaged database. */
    data object ReadFailed : InboxIntent

    /** The phone answered that the videos' downloads stand at [statuses]. */
    data class DownloadsChanged(val statuses: Map<VideoId, DownloadStatus>) : InboxIntent

    /** The read of the downloads threw. The rows show no download control. */
    data object DownloadsUnreadable : InboxIntent

    /**
     * The user tapped a row's download button, to start, go on with or try again the download of
     * [video]. The answer comes back as DownloadsChanged.
     */
    data class Download(val video: VideoSummary) : InboxIntent

    /**
     * The user tapped the progress ring of the video with [videoId], to pause its download. The
     * answer comes back as DownloadsChanged.
     */
    data class PauseDownload(val videoId: VideoId) : InboxIntent
}
