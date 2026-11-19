package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.feature.youtubesearch.logic.ResultsArea
import io.github.dewfallapp.feature.youtubesearch.logic.VideoRow
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent
import java.time.ZoneId
import kotlin.time.Instant

/** The round badge behind a message's glyph, as in the "Search YouTube" frames. */
private val MessageBadgeSize = 64.dp

/** The glyph in a banner, 20dp, as the design system's Banner says. */
private val BannerGlyphSize = 20.dp

/** What the area under the search field shows, from [results]. */
@Composable
internal fun SearchResultsArea(
    results: ResultsArea,
    now: Instant,
    zone: ZoneId,
    onIntent: (YouTubeSearchIntent) -> Unit,
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
            VideoList(rows = results.rows, now = now, zone = zone, modifier = modifier)
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
                }
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
 * again". Only the glyph is in the error color, because a failure isn't the user's fault. A screen
 * reader reads it when it appears.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FailureBanner(
    error: YouTubeError,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier =
                Modifier.padding(horizontal = DewfallSpacing.margin)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(DewfallSpacing.md)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_error),
                    contentDescription = stringResource(R.string.youtube_search_failed_icon),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(BannerGlyphSize),
                )
                Text(
                    text = stringResource(failureText(error)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onTryAgain) {
                    Text(stringResource(R.string.youtube_search_try_again))
                }
            }
        }
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
 * The videos found, in YouTube's order, under a "Videos" heading, in one rounded container as in
 * the "Search YouTube, results" frame.
 */
@Composable
private fun VideoList(
    rows: List<VideoRow>,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(horizontal = DewfallSpacing.margin, vertical = DewfallSpacing.sm),
    ) {
        item {
            Text(
                text = stringResource(R.string.youtube_search_videos_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(horizontal = DewfallSpacing.md, vertical = DewfallSpacing.sm),
            )
        }
        itemsIndexed(items = rows, key = { _, row -> row.id.value }) { index, row ->
            VideoRowItem(
                row = row,
                now = now,
                zone = zone,
                modifier =
                    Modifier.clip(containerShape(index, rows.size))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = DewfallSpacing.sm),
            )
        }
    }
}

/** The part of the container's rounded shape that row [index] of [count] draws. */
@Composable
private fun containerShape(index: Int, count: Int): Shape {
    val large = MaterialTheme.shapes.large
    val none = CornerSize(0)
    return when {
        count == 1 -> large
        index == 0 -> large.copy(bottomStart = none, bottomEnd = none)
        index == count - 1 -> large.copy(topStart = none, topEnd = none)
        else -> RectangleShape
    }
}
