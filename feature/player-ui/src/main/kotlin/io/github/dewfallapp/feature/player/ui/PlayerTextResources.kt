package io.github.dewfallapp.feature.player.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.TimeAgo
import io.github.dewfallapp.core.model.YouTubeError

/** The line under the title, such as "Published 7 hours ago" or "Published yesterday". */
@Composable
internal fun publishedText(timeAgo: TimeAgo): String =
    when (timeAgo) {
        is TimeAgo.Minutes -> plural(R.plurals.player_published_minutes_ago, timeAgo.count)
        is TimeAgo.Hours -> plural(R.plurals.player_published_hours_ago, timeAgo.count)
        TimeAgo.Today -> stringResource(R.string.player_published_today)
        TimeAgo.Yesterday -> stringResource(R.string.player_published_yesterday)
        is TimeAgo.Days -> plural(R.plurals.player_published_days_ago, timeAgo.count)
        is TimeAgo.Weeks -> plural(R.plurals.player_published_weeks_ago, timeAgo.count)
        is TimeAgo.Months -> plural(R.plurals.player_published_months_ago, timeAgo.count)
        is TimeAgo.Years -> plural(R.plurals.player_published_years_ago, timeAgo.count)
    }

@Composable
private fun plural(id: Int, count: Long): String = pluralStringResource(id, count.toInt(), count)

/** What a failure with [error] says, about this one video, or about YouTube as a whole. */
@StringRes
internal fun failureText(error: YouTubeError): Int =
    when (error) {
        YouTubeError.NETWORK -> R.string.player_failed_network
        YouTubeError.BLOCKED,
        YouTubeError.BROKEN -> R.string.player_failed_youtube
        YouTubeError.PRIVATE -> R.string.player_failed_private
        YouTubeError.AGE_RESTRICTED -> R.string.player_failed_age_restricted
        YouTubeError.COUNTRY_BLOCKED -> R.string.player_failed_country
        YouTubeError.MEMBERS_OR_PAID -> R.string.player_failed_members_or_paid
        YouTubeError.CHANNEL_CLOSED -> R.string.player_failed_channel_closed
        YouTubeError.UNAVAILABLE -> R.string.player_failed_unavailable
    }

/**
 * What a playback failure with [failure] says. The categories that the details share use the
 * details' words, so a failure reads the same in both places.
 */
@StringRes
internal fun playbackFailureText(failure: PlaybackFailure): Int =
    when (failure) {
        PlaybackFailure.NETWORK -> R.string.player_failed_network
        PlaybackFailure.YOUTUBE -> R.string.player_failed_youtube
        PlaybackFailure.PRIVATE -> R.string.player_failed_private
        PlaybackFailure.AGE_RESTRICTED -> R.string.player_failed_age_restricted
        PlaybackFailure.COUNTRY_BLOCKED -> R.string.player_failed_country
        PlaybackFailure.MEMBERS_OR_PAID -> R.string.player_failed_members_or_paid
        PlaybackFailure.CHANNEL_CLOSED -> R.string.player_failed_channel_closed
        PlaybackFailure.UNAVAILABLE -> R.string.player_failed_unavailable
        PlaybackFailure.NOT_DECODABLE -> R.string.player_failed_not_decodable
        PlaybackFailure.OTHER -> R.string.player_failed_stopped
    }
