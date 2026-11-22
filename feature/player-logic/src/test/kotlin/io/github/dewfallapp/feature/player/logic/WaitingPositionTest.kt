package io.github.dewfallapp.feature.player.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val MINUTE_MS = 60_000L

class WaitingPositionTest {
    @Test
    fun `before the database answers, the screen shows its own, and the player reads it`() {
        assertEquals(
            WaitingPosition(shownMs = 10 * MINUTE_MS, startMs = null),
            waitingPosition(StoredPosition.Unknown, 10 * MINUTE_MS),
        )
    }

    @Test
    fun `with no watch state, the screen's own position counts`() {
        val noState = StoredPosition.Known(null)
        assertEquals(
            WaitingPosition(10 * MINUTE_MS, 10 * MINUTE_MS),
            waitingPosition(noState, 10 * MINUTE_MS),
        )
        assertEquals(WaitingPosition(null, null), waitingPosition(noState, null))
    }

    @Test
    fun `a database that can't be read leaves the screen's own position`() {
        assertEquals(
            WaitingPosition(10 * MINUTE_MS, 10 * MINUTE_MS),
            waitingPosition(StoredPosition.Unreadable, 10 * MINUTE_MS),
        )
    }

    @Test
    fun `a stored zero after the end wins over the screen's length`() {
        val ended = StoredPosition.Known(Duration.ZERO)
        assertEquals(WaitingPosition(0, 0), waitingPosition(ended, 60 * MINUTE_MS))
    }

    @Test
    fun `the latest stored position wins after playing on in the background`() {
        // The screen kept 10:00 when the app left; the video resumed at 12:30 and played to 40:00.
        val resumed = StoredPosition.Known(12.minutes + 30.seconds)
        val later = StoredPosition.Known(40.minutes)

        assertEquals(750_000L, waitingPosition(resumed, 10 * MINUTE_MS).startMs)
        assertEquals(
            WaitingPosition(40 * MINUTE_MS, 40 * MINUTE_MS),
            waitingPosition(later, 10 * MINUTE_MS),
        )
    }
}
