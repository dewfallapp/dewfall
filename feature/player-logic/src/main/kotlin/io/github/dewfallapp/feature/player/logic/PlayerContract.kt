package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

/**
 * What the video screen shows about a video. The title, names, description and image links are user
 * data, so never log them.
 */
data class VideoPage(
    val title: String,
    /** The channel's ID, which opens its channel page. User data, so never log it. */
    val channelId: ChannelId,
    val channelName: String,
    /** Every size of the channel's picture, or none when YouTube gave none. */
    val channelAvatars: List<Thumbnail>,
    /** The description as plain text, or empty when the video has none. */
    val description: String,
    /** When it was published, or null if YouTube gave no time. */
    val published: PublishedTime?,
    /**
     * True for a live stream on air, or one that has just ended while YouTube still serves it as
     * live.
     */
    val isLive: Boolean,
)

/** What the screen shows under its top row. */
sealed interface DetailsArea {
    /**
     * Request [number] is running. Number 0 means the screen has just opened and no request has
     * started yet.
     */
    data class Loading(val number: Long) : DetailsArea

    /** The details arrived. */
    data class Shown(val page: VideoPage) : DetailsArea

    /** Request [number] failed with [error]. */
    data class Failed(val number: Long, val error: YouTubeError) : DetailsArea
}

/**
 * Whether the app's database marks the video watched, which the watched chip shows. It is the
 * database's data, not playback state, and it changes only when a mark lands. The stored position
 * never goes here: the route reads it only while the screen waits for play. See ADR 0002.
 */
sealed interface SavedWatch {
    /** The database hasn't answered yet. */
    data object Unknown : SavedWatch

    /** The database couldn't be read. */
    data object Unreadable : SavedWatch

    /** The database's latest answer: whether the video is [watched]. */
    data class Known(val watched: Boolean) : SavedWatch
}

/**
 * The video screen's state. Playback state never goes here. See ADR 0002. [fullscreen] says whether
 * the screen shows the fullscreen player. The ViewModel keeps it in its saved state too. [saved] is
 * whether the database marks the video watched, which isn't playback state. [download] is the
 * video's download as its chip shows it, or null while the database hasn't answered or can't be
 * read; [shownDownload] says whether the chip shows.
 */
data class PlayerState(
    val details: DetailsArea = DetailsArea.Loading(number = 0),
    val fullscreen: Boolean = false,
    val saved: SavedWatch = SavedWatch.Unknown,
    val download: DownloadChip? = null,
) : MviState

/** What happens on the video screen. */
sealed interface PlayerIntent : MviIntent {
    /** The screen opened. The ViewModel sends it once, when it is created. */
    data object Load : PlayerIntent

    /** The user tapped "Try again" after a failure. */
    data object TryAgain : PlayerIntent

    /** Request [number] answered with [result]. */
    data class DetailsFinished(val number: Long, val result: YouTubeResult<VideoDetails>) :
        PlayerIntent

    /** The user tapped the fullscreen button. */
    data object EnterFullscreen : PlayerIntent

    /** The user tapped the exit button, or made the back gesture, in fullscreen. */
    data object ExitFullscreen : PlayerIntent

    /** The database answered that the video is [watched], or not. */
    data class WatchStateChanged(val watched: Boolean) : PlayerIntent

    /** The database couldn't read the video's watch state. */
    data object WatchStateUnreadable : PlayerIntent

    /** The user tapped "Mark as watched". The answer comes back as WatchStateChanged. */
    data object MarkWatched : PlayerIntent

    /**
     * The user tapped "Watched" to take the mark away. The answer comes back as WatchStateChanged.
     */
    data object MarkUnwatched : PlayerIntent

    /** The database answered that the video's download stands at [chip]. */
    data class DownloadChanged(val chip: DownloadChip) : PlayerIntent

    /** The database couldn't read the video's download. */
    data object DownloadUnreadable : PlayerIntent

    /**
     * The user tapped "Download", "Paused" or a failure to start or go on with the download. The
     * answer comes back as DownloadChanged.
     */
    data object Download : PlayerIntent

    /** The user tapped "Downloading" to pause it. The answer comes back as DownloadChanged. */
    data object PauseDownload : PlayerIntent
}
