package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.VideoKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** What a row's thumbnail box says about the video's length. The UI turns it into text. */
sealed interface LengthLabel {
    /** An hour or more, such as 1h 06m. Seconds are dropped. */
    data class HoursAndMinutes(val hours: Long, val minutes: Int) : LengthLabel

    /** At least a minute and under an hour, such as 54m. Seconds are dropped. */
    data class Minutes(val minutes: Int) : LengthLabel

    /** Under a minute, such as 45s. */
    data class Seconds(val seconds: Int) : LengthLabel

    data object Live : LengthLabel

    data object Upcoming : LengthLabel
}

/**
 * The label for a video of [kind] and [duration], or null when there is nothing to say, because
 * YouTube gave no length for a video that is neither live nor upcoming.
 */
fun lengthLabel(duration: Duration?, kind: VideoKind): LengthLabel? =
    when {
        kind == VideoKind.LIVE -> LengthLabel.Live
        kind == VideoKind.UPCOMING -> LengthLabel.Upcoming
        duration == null -> null
        duration >= 1.hours -> {
            duration.toComponents { hours, minutes, _, _ ->
                LengthLabel.HoursAndMinutes(hours, minutes)
            }
        }
        duration >= 1.minutes -> LengthLabel.Minutes(duration.inWholeMinutes.toInt())
        else -> LengthLabel.Seconds(duration.inWholeSeconds.toInt())
    }
