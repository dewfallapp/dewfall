package io.github.dewfallapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.model.LengthLabel
import io.github.dewfallapp.core.model.TimeAgo

/** The length tag's text, such as "1h 06m", "54m", "45s", "Live" or "Upcoming". */
@Composable
internal fun lengthText(label: LengthLabel): String =
    when (label) {
        is LengthLabel.HoursAndMinutes -> {
            stringResource(R.string.core_ui_length_hours, label.hours, label.minutes)
        }
        is LengthLabel.Minutes -> {
            stringResource(R.string.core_ui_length_minutes, label.minutes)
        }
        is LengthLabel.Seconds -> {
            stringResource(R.string.core_ui_length_seconds, label.seconds)
        }
        LengthLabel.Live -> stringResource(R.string.core_ui_live)
        LengthLabel.Upcoming -> stringResource(R.string.core_ui_upcoming)
    }

/** The length as a screen reader says it, such as "1 hour 6 minutes", or "1 hour" with none. */
@Composable
internal fun spokenLength(label: LengthLabel): String =
    when (label) {
        is LengthLabel.HoursAndMinutes -> {
            val hours =
                pluralStringResource(
                    R.plurals.core_ui_spoken_hours,
                    label.hours.toInt(),
                    label.hours,
                )
            if (label.minutes == 0) {
                hours
            } else {
                stringResource(
                    R.string.core_ui_spoken_hours_and_minutes,
                    hours,
                    pluralStringResource(
                        R.plurals.core_ui_spoken_minutes,
                        label.minutes,
                        label.minutes,
                    ),
                )
            }
        }
        is LengthLabel.Minutes -> {
            pluralStringResource(
                R.plurals.core_ui_spoken_minutes,
                label.minutes,
                label.minutes,
            )
        }
        is LengthLabel.Seconds -> {
            pluralStringResource(
                R.plurals.core_ui_spoken_seconds,
                label.seconds,
                label.seconds,
            )
        }
        LengthLabel.Live -> stringResource(R.string.core_ui_live)
        LengthLabel.Upcoming -> stringResource(R.string.core_ui_upcoming)
    }

/** The time ago as a row shows it, such as "7h ago", "Today", "Yesterday" or "3 weeks ago". */
@Composable
internal fun timeAgoText(timeAgo: TimeAgo): String =
    when (timeAgo) {
        is TimeAgo.Minutes -> stringResource(R.string.core_ui_minutes_ago, timeAgo.count)
        is TimeAgo.Hours -> stringResource(R.string.core_ui_hours_ago, timeAgo.count)
        TimeAgo.Today -> stringResource(R.string.core_ui_today)
        TimeAgo.Yesterday -> stringResource(R.string.core_ui_yesterday)
        is TimeAgo.Days -> plural(R.plurals.core_ui_days_ago, timeAgo.count)
        is TimeAgo.Weeks -> plural(R.plurals.core_ui_weeks_ago, timeAgo.count)
        is TimeAgo.Months -> plural(R.plurals.core_ui_months_ago, timeAgo.count)
        is TimeAgo.Years -> plural(R.plurals.core_ui_years_ago, timeAgo.count)
    }

@Composable
private fun plural(id: Int, count: Long): String = pluralStringResource(id, count.toInt(), count)
