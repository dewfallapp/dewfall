package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoKind
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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

/** How long ago a video was published, in the unit a row shows. The UI turns it into text. */
sealed interface TimeAgo {
    data class Minutes(val count: Long) : TimeAgo

    data class Hours(val count: Long) : TimeAgo

    data object Yesterday : TimeAgo

    data class Days(val count: Long) : TimeAgo

    data class Weeks(val count: Long) : TimeAgo

    data class Months(val count: Long) : TimeAgo

    data class Years(val count: Long) : TimeAgo
}

/** The last day count that a row shows in days. From 14 days on, it shows weeks. */
private const val LAST_DAY_COUNT = 13L
private const val DAYS_IN_A_WEEK = 7L

/**
 * How long before [now] a video was [published], counted by the calendar in [zone], or null when
 * YouTube gave no time or the time is in the future.
 *
 * YouTube's lists give times such as "10 days ago" or "1 month ago", which NewPipeExtractor turns
 * into an approximate time by going back that many calendar units from now. Counting the same
 * calendar units reads each of them back as YouTube wrote it. An approximate time never shows
 * minutes or hours. See openspec/changes/archive/2026-11-19-youtube-search/design.md.
 */
fun timeAgo(published: PublishedTime?, now: Instant, zone: ZoneId): TimeAgo? {
    if (published == null || published.instant > now) return null
    val elapsed = now - published.instant
    return when {
        !published.isApproximate && elapsed < 1.hours -> {
            TimeAgo.Minutes(maxOf(1L, elapsed.inWholeMinutes))
        }
        !published.isApproximate && elapsed < 24.hours -> TimeAgo.Hours(elapsed.inWholeHours)
        else -> calendarTimeAgo(published.instant.toDate(zone), now.toDate(zone))
    }
}

private fun calendarTimeAgo(published: LocalDate, today: LocalDate): TimeAgo {
    val days = ChronoUnit.DAYS.between(published, today)
    val months = ChronoUnit.MONTHS.between(published, today)
    val years = ChronoUnit.YEARS.between(published, today)
    return when {
        days <= 1 -> TimeAgo.Yesterday
        days <= LAST_DAY_COUNT -> TimeAgo.Days(days)
        months < 1 -> TimeAgo.Weeks(days / DAYS_IN_A_WEEK)
        years < 1 -> TimeAgo.Months(months)
        else -> TimeAgo.Years(years)
    }
}

private fun Instant.toDate(zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
        .atZone(zone)
        .toLocalDate()
