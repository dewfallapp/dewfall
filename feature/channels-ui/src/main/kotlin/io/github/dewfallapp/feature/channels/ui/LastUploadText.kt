package io.github.dewfallapp.feature.channels.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.model.TimeAgo

/**
 * A row's last upload line, such as "Last upload 7 hours ago" or "Last upload yesterday", with the
 * units spelled out as the "Channels" frame writes them. The rows' short forms, such as "7h ago",
 * read badly in a sentence.
 */
@Composable
internal fun lastUploadText(timeAgo: TimeAgo): String =
    when (timeAgo) {
        is TimeAgo.Minutes -> plural(R.plurals.channels_last_upload_minutes, timeAgo.count)
        is TimeAgo.Hours -> plural(R.plurals.channels_last_upload_hours, timeAgo.count)
        TimeAgo.Today -> stringResource(R.string.channels_last_upload_today)
        TimeAgo.Yesterday -> stringResource(R.string.channels_last_upload_yesterday)
        is TimeAgo.Days -> plural(R.plurals.channels_last_upload_days, timeAgo.count)
        is TimeAgo.Weeks -> plural(R.plurals.channels_last_upload_weeks, timeAgo.count)
        is TimeAgo.Months -> plural(R.plurals.channels_last_upload_months, timeAgo.count)
        is TimeAgo.Years -> plural(R.plurals.channels_last_upload_years, timeAgo.count)
    }

@Composable
private fun plural(id: Int, count: Long): String = pluralStringResource(id, count.toInt(), count)
