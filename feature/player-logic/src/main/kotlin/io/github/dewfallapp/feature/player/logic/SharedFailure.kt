package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.PlaybackFailure

/**
 * Whether the details and playback failed with the same category, such as NETWORK or PRIVATE, with
 * or without saved details. Then the screen says it once, in the details' place or under the saved
 * details, and that message's "Try again" retries both.
 */
fun sharesFailure(details: DetailsArea, playback: PlaybackFailure?): Boolean {
    val error =
        when (details) {
            is DetailsArea.Failed -> details.error
            is DetailsArea.Saved -> details.error
            is DetailsArea.Loading,
            is DetailsArea.Shown -> return false
        }
    return PlaybackFailure.of(error) == playback
}

/** The playback failure that the video area shows: none when the details already say it. */
fun videoAreaFailure(details: DetailsArea, playback: PlaybackFailure?): PlaybackFailure? =
    if (sharesFailure(details, playback)) null else playback
