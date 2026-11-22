package io.github.dewfallapp.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val HOUR_MS = 3_600_000L
private const val MINUTE_MS = 60_000L

class WatchProgressRulesTest {
    @Test
    fun `a look below 90 percent remembers it and marks nothing`() {
        assertEquals(
            ProgressStep(belowSeen = true, marks = false),
            progressStep(false, 50 * MINUTE_MS, HOUR_MS, ended = false),
        )
    }

    @Test
    fun `crossing 90 percent from below marks once`() {
        val crossed = progressStep(true, 54 * MINUTE_MS, HOUR_MS, ended = false)
        assertEquals(ProgressStep(belowSeen = false, marks = true), crossed)
        val later = progressStep(crossed.belowSeen, 55 * MINUTE_MS, HOUR_MS, ended = false)
        assertEquals(ProgressStep(belowSeen = false, marks = false), later)
    }

    @Test
    fun `a playback that starts past 90 percent marks nothing before its end`() {
        // Such as one the user marked unwatched at 57 minutes.
        assertEquals(
            ProgressStep(belowSeen = false, marks = false),
            progressStep(false, 57 * MINUTE_MS, HOUR_MS, ended = false),
        )
        assertTrue(progressStep(false, 58 * MINUTE_MS, HOUR_MS, ended = true).marks)
    }

    @Test
    fun `a seek back below 90 percent and forward again marks again`() {
        val back = progressStep(false, 30 * MINUTE_MS, HOUR_MS, ended = false)
        assertTrue(back.belowSeen)
        assertTrue(progressStep(back.belowSeen, 57 * MINUTE_MS, HOUR_MS, ended = false).marks)
    }

    @Test
    fun `an unknown length keeps the flag and marks nothing`() {
        assertEquals(
            ProgressStep(belowSeen = true, marks = false),
            progressStep(true, 57 * MINUTE_MS, C.TIME_UNSET, ended = false),
        )
        assertEquals(
            ProgressStep(belowSeen = false, marks = false),
            progressStep(false, 57 * MINUTE_MS, 0, ended = false),
        )
    }

    @Test
    fun `the end marks and clears the flag`() {
        assertEquals(
            ProgressStep(belowSeen = false, marks = true),
            progressStep(true, 10 * MINUTE_MS, C.TIME_UNSET, ended = true),
        )
    }

    @Test
    fun `a save writes the position, or zero at the end`() {
        assertEquals(12.minutes + 30.seconds, progressSave(750_000, atEnd = false))
        assertEquals(Duration.ZERO, progressSave(HOUR_MS, atEnd = true))
        assertEquals(54.minutes, progressSave(54 * MINUTE_MS, atEnd = false))
    }

    @Test
    fun `a negative or unknown position saves zero`() {
        assertEquals(Duration.ZERO, progressSave(-5, atEnd = false))
        assertEquals(Duration.ZERO, progressSave(C.TIME_UNSET, atEnd = false))
    }

    @Test
    fun `a start that names a position keeps it`() {
        assertEquals(10 * MINUTE_MS, startPositionFor(10 * MINUTE_MS, 40.minutes, live = false))
        assertEquals(0, startPositionFor(0, 40.minutes, live = false))
    }

    @Test
    fun `a start with no position goes on from the saved one`() {
        assertEquals(40 * MINUTE_MS, startPositionFor(C.TIME_UNSET, 40.minutes, live = false))
    }

    @Test
    fun `a start with no saved position, a zero one or a live stream uses the default`() {
        assertEquals(C.TIME_UNSET, startPositionFor(C.TIME_UNSET, null, live = false))
        assertEquals(C.TIME_UNSET, startPositionFor(C.TIME_UNSET, Duration.ZERO, live = false))
        assertEquals(C.TIME_UNSET, startPositionFor(C.TIME_UNSET, 40.minutes, live = true))
    }

    @Test
    fun `play turning off saves, unless the player is idle`() {
        assertTrue(savesOn(ProgressTrigger.PLAY_OFF, moment(Player.STATE_READY, false)))
        assertTrue(savesOn(ProgressTrigger.PLAY_OFF, moment(Player.STATE_BUFFERING, false)))
        assertTrue(savesOn(ProgressTrigger.PLAY_OFF, moment(Player.STATE_ENDED, false)))
        assertFalse(savesOn(ProgressTrigger.PLAY_OFF, moment(Player.STATE_IDLE, false)))
    }

    @Test
    fun `a jump saves only while play is off`() {
        assertTrue(savesOn(ProgressTrigger.POSITION_JUMP, moment(Player.STATE_READY, false)))
        assertFalse(savesOn(ProgressTrigger.POSITION_JUMP, moment(Player.STATE_READY, true)))
    }

    @Test
    fun `becoming idle saves only as a stop, with no error`() {
        assertTrue(savesOn(ProgressTrigger.IDLE, moment(Player.STATE_IDLE, true)))
        assertFalse(savesOn(ProgressTrigger.IDLE, moment(Player.STATE_IDLE, true, hasError = true)))
    }

    @Test
    fun `a replace and the service's end save unless the player is idle`() {
        for (trigger in listOf(ProgressTrigger.REPLACED, ProgressTrigger.SERVICE_ENDING)) {
            assertTrue(savesOn(trigger, moment(Player.STATE_READY, true)))
            assertTrue(savesOn(trigger, moment(Player.STATE_ENDED, false)))
            assertFalse(savesOn(trigger, moment(Player.STATE_IDLE, false)))
        }
    }

    @Test
    fun `the tick saves while play is on, even while buffering`() {
        assertTrue(savesOn(ProgressTrigger.TICK, moment(Player.STATE_READY, true)))
        // Buffering keeps play on, and its position is where the video is.
        assertTrue(savesOn(ProgressTrigger.TICK, moment(Player.STATE_BUFFERING, true)))
        assertFalse(savesOn(ProgressTrigger.TICK, moment(Player.STATE_READY, false)))
        assertFalse(savesOn(ProgressTrigger.TICK, moment(Player.STATE_ENDED, true)))
        assertFalse(savesOn(ProgressTrigger.TICK, moment(Player.STATE_IDLE, true)))
    }

    @Test
    fun `nothing saves with an error or no item`() {
        for (trigger in ProgressTrigger.entries) {
            assertFalse(savesOn(trigger, moment(Player.STATE_READY, false, hasError = true)))
            assertFalse(savesOn(trigger, moment(Player.STATE_READY, true, hasError = true)))
            assertFalse(savesOn(trigger, moment(Player.STATE_READY, false, hasItem = false)))
            assertFalse(savesOn(trigger, moment(Player.STATE_IDLE, true, hasItem = false)))
        }
    }

    private fun moment(
        state: Int,
        playWhenReady: Boolean,
        hasError: Boolean = false,
        hasItem: Boolean = true,
    ) = PlayerMoment(state, playWhenReady, hasError, hasItem)
}
