package io.github.dewfallapp.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import io.github.dewfallapp.core.model.countsAsWatched
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What one look at a playback's position decides: whether the playback has now [belowSeen] a
 * position below 90 percent since it last marked the video, and whether this look [marks] the video
 * watched.
 */
internal data class ProgressStep(val belowSeen: Boolean, val marks: Boolean)

/**
 * One look at a playback at [positionMs] of [durationMs], which may be C.TIME_UNSET, when it has
 * [belowSeen] a position below 90 percent, or [ended]. Playback marks a video only when it crosses
 * 90 percent from below during one playback, as [countsAsWatched] counts it, or ends, so a video
 * the user unmarked past 90 percent stays unmarked until its end. A length that isn't known changes
 * nothing. See openspec/specs/player/spec.md.
 */
internal fun progressStep(
    belowSeen: Boolean,
    positionMs: Long,
    durationMs: Long,
    ended: Boolean,
): ProgressStep {
    if (ended) return ProgressStep(belowSeen = false, marks = true)
    val length = durationMs.takeIf { it != C.TIME_UNSET && it > 0 }?.milliseconds
    val position = positionMs.coerceAtLeast(0).milliseconds
    return when {
        length == null -> ProgressStep(belowSeen, marks = false)
        !countsAsWatched(position, length) -> ProgressStep(belowSeen = true, marks = false)
        else -> ProgressStep(belowSeen = false, marks = belowSeen)
    }
}

/**
 * The position that a save at [positionMs] writes: zero while the playback is [atEnd], so a video
 * played to its end opens from the start next time, otherwise the position, with a negative or
 * unknown one as zero.
 */
internal fun progressSave(positionMs: Long, atEnd: Boolean): Duration =
    if (atEnd || positionMs == C.TIME_UNSET || positionMs < 0) {
        Duration.ZERO
    } else {
        positionMs.milliseconds
    }

/**
 * Where a start that asked for [requestedMs] begins: the requested position when it names one, the
 * live edge for a [live] stream, the [saved] position when it is above zero, or otherwise the start
 * (C.TIME_UNSET, the default position).
 */
internal fun startPositionFor(requestedMs: Long, saved: Duration?, live: Boolean): Long =
    when {
        requestedMs != C.TIME_UNSET -> requestedMs
        live -> C.TIME_UNSET
        saved != null && saved.isPositive() -> saved.inWholeMilliseconds
        else -> C.TIME_UNSET
    }

/** What made the playback service think of saving the current item's position. */
internal enum class ProgressTrigger {
    /** Play turned off: a pause from any screen, the notification, a headset or audio focus. */
    PLAY_OFF,

    /** The position jumped, such as a seek. */
    POSITION_JUMP,

    /** The player became idle, which is a stop when it has no error. */
    IDLE,

    /** Another video is about to take the current item's place. */
    REPLACED,

    /** The service is ending. */
    SERVICE_ENDING,

    /** The 30-second tick while play is on, even while the player waits for data. */
    TICK,
}

/** What the player is like at a trigger: its [playbackState], play, an error and an item. */
internal data class PlayerMoment(
    val playbackState: Int,
    val playWhenReady: Boolean,
    val hasError: Boolean,
    val hasItem: Boolean,
)

/**
 * Whether [trigger] saves the current item's position, given the player's [moment]. Nothing saves
 * from a player with no item, or one with an error, whose position after a failure may not be where
 * the video was. A jump saves only while play is off: one while playing is only looked at. The end
 * saves through its own path, which marks the video too. See
 * docs/architecture.md#saving-where-a-video-was-left.
 */
internal fun savesOn(trigger: ProgressTrigger, moment: PlayerMoment): Boolean {
    if (!moment.hasItem || moment.hasError) return false
    val idle = moment.playbackState == Player.STATE_IDLE
    return when (trigger) {
        ProgressTrigger.PLAY_OFF,
        ProgressTrigger.POSITION_JUMP -> !moment.playWhenReady && !idle
        ProgressTrigger.IDLE -> idle
        ProgressTrigger.REPLACED,
        ProgressTrigger.SERVICE_ENDING -> !idle
        ProgressTrigger.TICK -> {
            moment.playWhenReady && !idle && moment.playbackState != Player.STATE_ENDED
        }
    }
}
