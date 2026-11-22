package io.github.dewfallapp.feature.player.logic

import kotlin.time.Duration

/**
 * The position that the app's database stores for the video, as the route reads it while the screen
 * waits for play. It stays out of screen state, because the playback service updates it while the
 * video plays. See ADR 0002.
 */
sealed interface StoredPosition {
    /** The database hasn't answered yet. */
    data object Unknown : StoredPosition

    /** The database couldn't be read. */
    data object Unreadable : StoredPosition

    /**
     * The database's answer: the stored [position], or null when the video has no watch state. It
     * is user data, so never log it.
     */
    data class Known(val position: Duration?) : StoredPosition
}

/**
 * Where a video screen that waits for play stands, in milliseconds: the position its video area
 * [shownMs], and the one play and "Try again" [startMs], or null for either when there is none. A
 * null [startMs] before the database's first answer means a start waits for that answer.
 */
data class WaitingPosition(val shownMs: Long?, val startMs: Long?)

/**
 * Where a video screen that waits for play, after the system ended the app or stopped the player,
 * stands, from the database's [stored] position and the position the screen kept in its own saved
 * state, [screenPositionMs], or null. The stored position wins, because the video may have played
 * on in the background after the screen kept its own, and a stored zero after the end means "from
 * the start". With no watch state, or a database that can't be read, the screen's own counts.
 * Before the database's first answer, the screen shows its own, and a start waits for that answer,
 * which PlayerViewModel's withLastPosition does. See openspec/specs/player/spec.md.
 */
fun waitingPosition(stored: StoredPosition, screenPositionMs: Long?): WaitingPosition =
    when (stored) {
        StoredPosition.Unknown -> WaitingPosition(shownMs = screenPositionMs, startMs = null)
        StoredPosition.Unreadable -> WaitingPosition(screenPositionMs, screenPositionMs)
        is StoredPosition.Known -> {
            val position = stored.position?.inWholeMilliseconds ?: screenPositionMs
            WaitingPosition(position, position)
        }
    }
