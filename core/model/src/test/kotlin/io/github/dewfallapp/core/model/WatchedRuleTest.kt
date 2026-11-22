package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class WatchedRuleTest {
    @Test
    fun `a position below 90 percent doesn't count`() {
        assertFalse(countsAsWatched(53.minutes, 60.minutes))
        assertFalse(countsAsWatched(54.minutes - 1.milliseconds, 60.minutes))
        assertFalse(countsAsWatched(Duration.ZERO, 60.minutes))
    }

    @Test
    fun `a position at 90 percent counts`() {
        assertTrue(countsAsWatched(54.minutes, 60.minutes))
        assertTrue(countsAsWatched(9.milliseconds, 10.milliseconds))
    }

    @Test
    fun `a position above 90 percent counts, up to and past the length`() {
        assertTrue(countsAsWatched(2.hours + 45.minutes, 3.hours))
        assertTrue(countsAsWatched(60.minutes, 60.minutes))
        assertTrue(countsAsWatched(61.minutes, 60.minutes))
    }

    @Test
    fun `an unknown length never counts`() {
        assertFalse(countsAsWatched(54.minutes, null))
        assertFalse(countsAsWatched(54.minutes, Duration.INFINITE))
    }

    @Test
    fun `a length of zero or below never counts`() {
        assertFalse(countsAsWatched(Duration.ZERO, Duration.ZERO))
        assertFalse(countsAsWatched(1.minutes, Duration.ZERO))
        assertFalse(countsAsWatched(1.minutes, (-1).minutes))
    }
}
