package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LengthLabelTest {
    @Test
    fun `lengths drop seconds above a minute`() {
        assertEquals(LengthLabel.HoursAndMinutes(1, 6), lengthLabel(66.minutes, VideoKind.VIDEO))
        assertEquals(
            LengthLabel.HoursAndMinutes(1, 0),
            lengthLabel(60.minutes + 59.seconds, VideoKind.VIDEO),
        )
        assertEquals(LengthLabel.Minutes(59), lengthLabel(59.minutes + 59.seconds, VideoKind.VIDEO))
        assertEquals(LengthLabel.Minutes(1), lengthLabel(60.seconds, VideoKind.VIDEO))
        assertEquals(LengthLabel.Seconds(59), lengthLabel(59.seconds, VideoKind.VIDEO))
        assertEquals(LengthLabel.Seconds(45), lengthLabel(45.seconds, VideoKind.VIDEO))
        assertEquals(LengthLabel.HoursAndMinutes(12, 14), lengthLabel(734.minutes, VideoKind.VIDEO))
    }

    @Test
    fun `live and upcoming videos show their kind, and an unknown length shows nothing`() {
        assertEquals(LengthLabel.Live, lengthLabel(null, VideoKind.LIVE))
        assertEquals(LengthLabel.Live, lengthLabel(3.hours, VideoKind.LIVE))
        assertEquals(LengthLabel.Upcoming, lengthLabel(null, VideoKind.UPCOMING))
        assertNull(lengthLabel(null, VideoKind.VIDEO))
    }
}
