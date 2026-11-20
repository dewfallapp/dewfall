package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.PlaybackFailure

/**
 * Whether the details and playback failed with the same category, such as NETWORK or PRIVATE. Then
 * the screen says it once, in the details' place, and that message's "Try again" retries both.
 */
fun sharesFailure(details: DetailsArea, playback: PlaybackFailure?): Boolean =
    details is DetailsArea.Failed && PlaybackFailure.of(details.error) == playback

/** The playback failure that the video area shows: none when the details already say it. */
fun videoAreaFailure(details: DetailsArea, playback: PlaybackFailure?): PlaybackFailure? =
    if (sharesFailure(details, playback)) null else playback
