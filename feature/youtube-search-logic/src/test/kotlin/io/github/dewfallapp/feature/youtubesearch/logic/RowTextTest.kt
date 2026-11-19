package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoKind
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val LONDON = ZoneId.of("Europe/London")

private fun instant(at: LocalDateTime, zone: ZoneId = LONDON): Instant {
    val java = at.atZone(zone).toInstant()
    return Instant.fromEpochSeconds(java.epochSecond, java.nano)
}

/**
 * The time that NewPipeExtractor v0.27.1's TimeAgoParser.getResultFor makes from YouTube's
 * "[amount] [unit] ago": it goes back that many calendar units from now, takes a year as one more
 * day, and moves date-based units to the start of the day, marked approximate.
 */
private fun youTubeTime(amount: Long, unit: ChronoUnit, now: LocalDateTime): PublishedTime {
    val back =
        if (unit == ChronoUnit.YEARS) {
            now.minusYears(amount).minusDays(1)
        } else {
            now.minus(amount, unit)
        }
    val approximate = unit.isDateBased
    return PublishedTime(
        instant(if (approximate) back.truncatedTo(ChronoUnit.DAYS) else back),
        isApproximate = approximate,
    )
}

private fun exact(at: LocalDateTime) = PublishedTime(instant(at), isApproximate = false)

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

class TimeAgoTest {
    /** A Monday in March 2027, a year that isn't a leap year. */
    private val march = LocalDateTime.of(2027, 3, 15, 10, 30)

    private fun ago(published: PublishedTime?, now: LocalDateTime = march) =
        timeAgo(published, instant(now), LONDON)

    @Test
    fun `no time, or a time in the future, shows nothing`() {
        assertNull(ago(null))
        assertNull(ago(exact(march.plusDays(1))))
    }

    @Test
    fun `exact times under a day show minutes or hours`() {
        assertEquals(TimeAgo.Minutes(1), ago(exact(march.minusSeconds(20))))
        assertEquals(TimeAgo.Minutes(59), ago(exact(march.minusMinutes(59))))
        assertEquals(TimeAgo.Hours(1), ago(exact(march.minusMinutes(60))))
        assertEquals(TimeAgo.Hours(23), ago(exact(march.minusHours(23))))
        assertEquals(TimeAgo.Yesterday, ago(exact(march.minusHours(24))))
    }

    @Test
    fun `calendar days, then weeks from 14 days`() {
        assertEquals(TimeAgo.Yesterday, ago(exact(march.minusDays(1))))
        assertEquals(TimeAgo.Days(2), ago(exact(march.minusDays(2))))
        assertEquals(TimeAgo.Days(13), ago(exact(march.minusDays(13))))
        assertEquals(TimeAgo.Weeks(2), ago(exact(march.minusDays(14))))
        assertEquals(TimeAgo.Weeks(3), ago(exact(march.minusDays(22))))
    }

    @Test
    fun `calendar months and years`() {
        assertEquals(TimeAgo.Weeks(3), ago(exact(LocalDateTime.of(2027, 2, 16, 10, 30))))
        assertEquals(TimeAgo.Months(1), ago(exact(LocalDateTime.of(2027, 2, 15, 10, 30))))
        assertEquals(TimeAgo.Months(11), ago(exact(LocalDateTime.of(2026, 3, 16, 10, 30))))
        assertEquals(TimeAgo.Years(1), ago(exact(LocalDateTime.of(2026, 3, 15, 10, 30))))
        assertEquals(TimeAgo.Years(2), ago(exact(LocalDateTime.of(2025, 3, 1, 10, 30))))
    }

    /** The relative times in the saved set SEARCH_NASA, recorded on November 18, 2026. */
    private val savedTimes =
        listOf(
            1L to ChronoUnit.DAYS,
            4L to ChronoUnit.DAYS,
            5L to ChronoUnit.DAYS,
            8L to ChronoUnit.DAYS,
            10L to ChronoUnit.DAYS,
            11L to ChronoUnit.DAYS,
            12L to ChronoUnit.DAYS,
            13L to ChronoUnit.DAYS,
            2L to ChronoUnit.WEEKS,
            3L to ChronoUnit.MONTHS,
            5L to ChronoUnit.MONTHS,
            1L to ChronoUnit.YEARS,
            3L to ChronoUnit.YEARS,
            8L to ChronoUnit.YEARS,
            17L to ChronoUnit.HOURS,
        )

    private fun expected(amount: Long, unit: ChronoUnit): TimeAgo =
        when (unit) {
            ChronoUnit.HOURS -> TimeAgo.Hours(amount)
            ChronoUnit.DAYS -> if (amount == 1L) TimeAgo.Yesterday else TimeAgo.Days(amount)
            ChronoUnit.WEEKS -> TimeAgo.Weeks(amount)
            ChronoUnit.MONTHS -> TimeAgo.Months(amount)
            else -> TimeAgo.Years(amount)
        }

    @Test
    fun `YouTube's times read back as YouTube wrote them, on hard dates`() {
        val nows =
            listOf(
                march,
                // February 29 of a leap year.
                LocalDateTime.of(2028, 2, 29, 8, 0),
                // The last day of a 31-day month, after a 30-day one and after February.
                LocalDateTime.of(2027, 5, 31, 23, 0),
                LocalDateTime.of(2027, 3, 31, 0, 30),
            )
        for (now in nows) {
            for ((amount, unit) in savedTimes) {
                assertEquals(
                    expected(amount, unit),
                    ago(youTubeTime(amount, unit, now), now),
                    "$amount $unit ago, read on $now",
                )
            }
            for (months in 1L..11L) {
                assertEquals(
                    TimeAgo.Months(months),
                    ago(youTubeTime(months, ChronoUnit.MONTHS, now), now),
                    "$months months ago, read on $now",
                )
            }
            for (weeks in 2L..3L) {
                assertEquals(
                    TimeAgo.Weeks(weeks),
                    ago(youTubeTime(weeks, ChronoUnit.WEEKS, now), now),
                    "$weeks weeks ago, read on $now",
                )
            }
        }
    }

    @Test
    fun `four weeks in February can read as a month, as design md accepts`() {
        val now = LocalDateTime.of(2027, 3, 1, 9, 0)

        assertEquals(TimeAgo.Months(1), ago(youTubeTime(4, ChronoUnit.WEEKS, now), now))
    }

    @Test
    fun `an approximate day never shows hours, even when the clocks went forward`() {
        // The clocks in London went forward on March 28, 2027, so that day had 23 hours, and
        // "1 day ago" read soon after midnight on the 29th is under 24 real hours old.
        val now = LocalDateTime.of(2027, 3, 29, 0, 30)

        assertEquals(TimeAgo.Yesterday, ago(youTubeTime(1, ChronoUnit.DAYS, now), now))
    }
}
