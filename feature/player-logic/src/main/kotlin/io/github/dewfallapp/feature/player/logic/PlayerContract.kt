package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

/**
 * What the video screen shows about a video. The title, names and description are user data, so
 * never log them.
 */
data class VideoPage(
    val title: String,
    val channelName: String,
    /** The channel name's first user-perceived character, or null when the name has none. */
    val channelInitial: String?,
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

/** The video screen's state. Playback state never goes here. See ADR 0002. */
data class PlayerState(val details: DetailsArea = DetailsArea.Loading(number = 0)) : MviState

/** What happens on the video screen. */
sealed interface PlayerIntent : MviIntent {
    /** The screen opened. The ViewModel sends it once, when it is created. */
    data object Load : PlayerIntent

    /** The user tapped "Try again" after a failure. */
    data object TryAgain : PlayerIntent

    /** Request [number] answered with [result]. */
    data class DetailsFinished(val number: Long, val result: YouTubeResult<VideoDetails>) :
        PlayerIntent
}
