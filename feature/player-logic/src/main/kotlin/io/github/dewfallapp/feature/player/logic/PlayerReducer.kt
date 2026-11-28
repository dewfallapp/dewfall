package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The video screen's reducer. Load starts the first request, and "Try again" starts the next one
 * only after a failure that offers it. An answer counts only while its own request is running. The
 * fullscreen intents turn fullscreen on and off, and leave the details alone. The database's
 * answers set what it holds about the video, its watch state, its download and the summary saved
 * with it, and the chips' taps change nothing until the answer comes. After every intent, the
 * details area settles with the saved summary, so the order in which a failure and the summary
 * arrive doesn't matter. The screen has no effects, so its effect type is Nothing.
 */
object PlayerReducer : Reducer<PlayerState, PlayerIntent, Nothing> {
    override fun reduce(state: PlayerState, intent: PlayerIntent): Next<PlayerState, Nothing> =
        Next(changed(state, intent).settled())

    private fun changed(state: PlayerState, intent: PlayerIntent): PlayerState =
        when (intent) {
            PlayerIntent.Load -> state.loaded()
            PlayerIntent.TryAgain -> state.triedAgain()
            is PlayerIntent.DetailsFinished -> state.finished(intent)
            PlayerIntent.EnterFullscreen -> state.copy(fullscreen = true)
            PlayerIntent.ExitFullscreen -> state.copy(fullscreen = false)
            is PlayerIntent.WatchStateChanged -> {
                state.copy(saved = SavedWatch.Known(watched = intent.watched))
            }
            PlayerIntent.WatchStateUnreadable -> state.copy(saved = SavedWatch.Unreadable)
            is PlayerIntent.DownloadChanged -> state.copy(download = intent.chip)
            PlayerIntent.DownloadUnreadable -> state.copy(download = null)
            is PlayerIntent.SavedVideoChanged -> state.copy(savedVideo = intent.video)
            // The ViewModel writes the mark or the download, and the database's answer
            // changes the state.
            PlayerIntent.MarkWatched,
            PlayerIntent.MarkUnwatched,
            PlayerIntent.Download,
            PlayerIntent.PauseDownload -> state
        }
}

/** Starts the first request, or changes nothing once one has started. */
private fun PlayerState.loaded(): PlayerState =
    if (details == DetailsArea.Loading(number = 0)) {
        copy(details = DetailsArea.Loading(number = 1))
    } else {
        this
    }

/**
 * Starts the next request after a failure that offers "Try again", with or without saved details,
 * or changes nothing.
 */
private fun PlayerState.triedAgain(): PlayerState {
    val (number, error) =
        when (val area = details) {
            is DetailsArea.Failed -> area.number to area.error
            is DetailsArea.Saved -> area.number to area.error
            is DetailsArea.Loading,
            is DetailsArea.Shown -> return this
        }
    return if (error.offersTryAgain) copy(details = DetailsArea.Loading(number + 1)) else this
}

/**
 * Whether a failure with this error shows the details saved with the video's download: only when
 * YouTube can't be reached. For another error, YouTube's answer is newer than the saved summary, or
 * the maintainer chose the failure alone. See openspec/specs/player/spec.md.
 */
private val YouTubeError.showsSavedDetails: Boolean
    get() = this == YouTubeError.NETWORK

/**
 * The details area with the saved summary: a failure that shows saved details becomes Saved while
 * there is a summary, and Saved goes back to the same failure once there is none, or takes the
 * summary's new page.
 */
private fun PlayerState.settled(): PlayerState {
    val summary = savedVideo
    val area =
        when (val current = details) {
            is DetailsArea.Failed -> {
                if (summary != null && current.error.showsSavedDetails) {
                    DetailsArea.Saved(current.number, current.error, pageOf(summary))
                } else {
                    current
                }
            }
            is DetailsArea.Saved -> {
                if (summary == null) {
                    DetailsArea.Failed(current.number, current.error)
                } else {
                    current.copy(page = pageOf(summary))
                }
            }
            is DetailsArea.Loading,
            is DetailsArea.Shown -> current
        }
    return if (area == details) this else copy(details = area)
}

/** Shows the answer in [intent], if it is for the request that is running. */
private fun PlayerState.finished(intent: PlayerIntent.DetailsFinished): PlayerState {
    if (intent.number < 1 || details != DetailsArea.Loading(intent.number)) return this
    val area =
        when (val result = intent.result) {
            is YouTubeResult.Success -> DetailsArea.Shown(pageOf(result.value))
            is YouTubeResult.Failure -> DetailsArea.Failed(intent.number, result.error)
        }
    return copy(details = area)
}

/**
 * The page for [details], with every size of the channel's picture, for the channel row. It leaves
 * out the video's thumbnails, which the player covers, and the length, which the player shows.
 */
private fun pageOf(details: VideoDetails): VideoPage =
    VideoPage(
        title = details.title,
        channelId = details.channelId,
        channelName = details.channelName,
        channelAvatars = details.channelAvatars,
        description = details.description,
        published = details.published,
        isLive = details.isLive,
    )

/**
 * The page for a [summary] saved with a download. A download keeps no description and no channel
 * picture, so the page has neither, and a summary can't tell that a stream is on air, so it isn't
 * live.
 */
private fun pageOf(summary: VideoSummary): VideoPage =
    VideoPage(
        title = summary.title,
        channelId = summary.channelId,
        channelName = summary.channelName,
        channelAvatars = emptyList(),
        description = "",
        published = summary.published,
        isLive = false,
    )
