package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.component.Banner
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.ui.VideoRowItem
import io.github.dewfallapp.core.ui.rowGroupShape
import io.github.dewfallapp.feature.youtubesearch.logic.ChannelRow
import io.github.dewfallapp.feature.youtubesearch.logic.ResultsArea
import io.github.dewfallapp.feature.youtubesearch.logic.VideoRow
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent
import java.time.ZoneId
import kotlin.time.Instant

/** The round badge behind a message's glyph, as in the "Search YouTube" frames. */
private val MessageBadgeSize = 64.dp

/**
 * What the area under the search field shows, from [results], with each channel row's button from
 * [subscribedIds], which is null until the database answers.
 */
@Composable
internal fun SearchResultsArea(
    results: ResultsArea,
    subscribedIds: Set<ChannelId>?,
    now: Instant,
    zone: ZoneId,
    onIntent: (YouTubeSearchIntent) -> Unit,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (results) {
        ResultsArea.Idle -> {
            Message(
                title = stringResource(R.string.youtube_search_intro_title),
                body = stringResource(R.string.youtube_search_intro_body),
                modifier = modifier,
            )
        }
        is ResultsArea.Loading -> Loading(modifier = modifier)
        is ResultsArea.Results -> {
            ResultList(
                channels = results.channels,
                videos = results.videos,
                subscribedIds = subscribedIds,
                now = now,
                zone = zone,
                onIntent = onIntent,
                onOpenVideo = onOpenVideo,
                onOpenChannel = onOpenChannel,
                modifier = modifier,
            )
        }
        is ResultsArea.NoResults -> {
            Message(
                title = stringResource(R.string.youtube_search_no_results_title),
                body =
                    stringResource(R.string.youtube_search_no_results_body, results.request.text),
                modifier = modifier,
            )
        }
        is ResultsArea.Failed -> {
            FailureBanner(
                error = results.error,
                onTryAgain = { onIntent(YouTubeSearchIntent.TryAgain) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.youtube_search_loading)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        CircularProgressIndicator(
            modifier =
                Modifier.padding(top = DewfallSpacing.xl).semantics {
                    contentDescription = description
                },
        )
    }
}

/**
 * A message in the middle of the area, under the search glyph in a round badge. It scrolls, so a
 * large font or a small window never cuts it off.
 */
@Composable
private fun Message(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DewfallSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
    ) {
        Box(
            modifier =
                Modifier.size(MessageBadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_search),
                contentDescription = stringResource(R.string.youtube_search_field_icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A failed search, in the design system's neutral Banner: the error glyph, the message, and "Try
 * again". Only the glyph is in the error color, because a failure isn't the user's fault.
 */
@Composable
private fun FailureBanner(
    error: YouTubeError,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Banner(
            message = stringResource(failureText(error)),
            glyph = painterResource(DesignR.drawable.ic_error),
            glyphDescription = stringResource(R.string.youtube_search_failed_icon),
            glyphTint = MaterialTheme.colorScheme.error,
            actionLabel = stringResource(R.string.youtube_search_try_again),
            onAction = onTryAgain,
            modifier = Modifier.padding(horizontal = DewfallSpacing.margin),
        )
    }
}

/** NETWORK says to check the connection. Every other category says that YouTube isn't loading. */
private fun failureText(error: YouTubeError): Int =
    if (error == YouTubeError.NETWORK) {
        R.string.youtube_search_failed_network
    } else {
        R.string.youtube_search_failed_other
    }

/**
 * What a search found, in YouTube's order: the channels under a "Channels" heading, then the videos
 * under a "Videos" heading, each list in one rounded container, as in the "Search YouTube, results"
 * frames. A heading shows only above a list with rows. Tapping a channel row opens its page through
 * [onOpenChannel], and tapping a video opens it through [onOpenVideo].
 */
@Composable
private fun ResultList(
    channels: List<ChannelRow>,
    videos: List<VideoRow>,
    subscribedIds: Set<ChannelId>?,
    now: Instant,
    zone: ZoneId,
    onIntent: (YouTubeSearchIntent) -> Unit,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.youtube_search_open_video)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(horizontal = DewfallSpacing.margin, vertical = DewfallSpacing.sm),
    ) {
        if (channels.isNotEmpty()) {
            item(key = CHANNELS_HEADING_KEY) {
                Heading(text = stringResource(R.string.youtube_search_channels_heading))
            }
            itemsIndexed(items = channels, key = { _, row -> CHANNEL_KEY_PREFIX + row.id.value }) {
                index,
                row ->
                ChannelResultRow(
                    row = row,
                    subscribed = subscribedIds?.let { row.id in it },
                    onOpen = { onOpenChannel(row.id) },
                    onSubscribe = { onIntent(YouTubeSearchIntent.Subscribe(row.id)) },
                    onUnsubscribe = { onIntent(YouTubeSearchIntent.UnsubscribeTapped(row.id)) },
                    modifier =
                        Modifier.clip(rowGroupShape(index, channels.size))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = DewfallSpacing.sm),
                )
            }
        }
        if (videos.isNotEmpty()) {
            item(key = VIDEOS_HEADING_KEY) {
                Heading(
                    text = stringResource(R.string.youtube_search_videos_heading),
                    modifier =
                        if (channels.isNotEmpty()) {
                            Modifier.padding(top = DewfallSpacing.sm)
                        } else {
                            Modifier
                        },
                )
            }
            itemsIndexed(items = videos, key = { _, row -> row.id.value }) { index, row ->
                VideoRowItem(
                    title = row.title,
                    channelName = row.channelName,
                    duration = row.duration,
                    kind = row.kind,
                    published = row.published,
                    now = now,
                    zone = zone,
                    modifier =
                        Modifier.clip(rowGroupShape(index, videos.size))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable(onClickLabel = openLabel) { onOpenVideo(row.id) }
                            .padding(horizontal = DewfallSpacing.sm),
                )
            }
        }
    }
}

/**
 * The lazy list's keys for the two headings. A video's key is its ID, and a channel's has a prefix.
 */
private const val CHANNELS_HEADING_KEY = "heading:channels"
private const val VIDEOS_HEADING_KEY = "heading:videos"
private const val CHANNEL_KEY_PREFIX = "channel:"

/** A heading above a group of rows, as in the "Search YouTube, results" frames. */
@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .semantics { heading() }
                .padding(horizontal = DewfallSpacing.md, vertical = DewfallSpacing.sm),
    )
}
