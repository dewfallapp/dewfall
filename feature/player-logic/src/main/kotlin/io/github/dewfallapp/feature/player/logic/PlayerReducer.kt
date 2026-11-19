package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The video screen's reducer. Load starts the first request, and "Try again" starts the next one
 * only after a failure that offers it. An answer counts only while its own request is running. The
 * screen has no effects, so its effect type is Nothing.
 */
object PlayerReducer : Reducer<PlayerState, PlayerIntent, Nothing> {
    override fun reduce(state: PlayerState, intent: PlayerIntent): Next<PlayerState, Nothing> =
        Next(
            when (intent) {
                PlayerIntent.Load -> state.loaded()
                PlayerIntent.TryAgain -> state.triedAgain()
                is PlayerIntent.DetailsFinished -> state.finished(intent)
            }
        )
}

/** Starts the first request, or changes nothing once one has started. */
private fun PlayerState.loaded(): PlayerState =
    if (details == DetailsArea.Loading(number = 0)) {
        copy(details = DetailsArea.Loading(number = 1))
    } else {
        this
    }

/** Starts the next request after a failure that offers "Try again", or changes nothing. */
private fun PlayerState.triedAgain(): PlayerState {
    val failed = details as? DetailsArea.Failed
    return if (failed != null && offersTryAgain(failed.error)) {
        copy(details = DetailsArea.Loading(failed.number + 1))
    } else {
        this
    }
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
 * The page for [details]. It leaves out the thumbnails and avatars, which no image shows yet, the
 * channel's ID, which nothing uses yet, and the length, which the player shows in #42.
 */
private fun pageOf(details: VideoDetails): VideoPage =
    VideoPage(
        title = details.title,
        channelName = details.channelName,
        channelInitial = initialOf(details.channelName),
        description = details.description,
        published = details.published,
        isLive = details.isLive,
    )
