package io.github.dewfallapp.feature.channelpage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.dewfallapp.core.designsystem.component.ChannelEmblem
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.initialOf
import io.github.dewfallapp.core.ui.UnsubscribeDialog
import io.github.dewfallapp.core.ui.VideoRowItem
import io.github.dewfallapp.core.ui.rowGroupShape
import io.github.dewfallapp.feature.channelpage.logic.ChannelIntent
import io.github.dewfallapp.feature.channelpage.logic.ChannelState
import io.github.dewfallapp.feature.channelpage.logic.LoadArea
import io.github.dewfallapp.feature.channelpage.logic.Subscription
import io.github.dewfallapp.feature.channelpage.logic.UploadRow
import java.time.ZoneId
import kotlin.time.Instant

/**
 * The channel's emblem at the default font size, as in the "Channel page" frames. It grows with the
 * font size.
 */
private val EmblemSize = 64.dp

/** How many lines of the description show while it is folded. */
private const val FOLDED_DESCRIPTION_LINES = 3

/**
 * The channel page, after the "Channel page" frames: a back arrow, then the channel, the
 * subscription card, and the latest uploads, or loading, or a failure. It is stateless: it shows
 * [state], with the time ago counted from [now] in [zone], and reports what the user does through
 * [onIntent], a tap on an upload through [onOpenVideo], and the back arrow through [onBack].
 *
 * Its background fills the window behind the system bars. Its content sits inside the safe drawing
 * area, in one column at most [ReadableWidth] wide, so nothing is under the system bars or the
 * display cutout.
 */
@Composable
internal fun ChannelScreen(
    state: ChannelState,
    now: Instant,
    zone: ZoneId,
    onIntent: (ChannelIntent) -> Unit,
    onOpenVideo: (VideoId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = DewfallSpacing.xs),
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.channel_page_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding =
                        PaddingValues(
                            start = DewfallSpacing.margin,
                            end = DewfallSpacing.margin,
                            bottom = DewfallSpacing.lg,
                        ),
                ) {
                    page(state, now, zone, onIntent, onOpenVideo)
                }
            }
        }
    }
    val channel = state.shownChannel
    if (state.confirmingUnsubscribe && channel != null) {
        UnsubscribeDialog(
            channelName = channel.name,
            onConfirm = { onIntent(ChannelIntent.UnsubscribeConfirmed) },
            onDismiss = { onIntent(ChannelIntent.UnsubscribeCancelled) },
        )
    }
}

/** The page's items: the channel and its card when there is one, then the load's area. */
private fun LazyListScope.page(
    state: ChannelState,
    now: Instant,
    zone: ZoneId,
    onIntent: (ChannelIntent) -> Unit,
    onOpenVideo: (VideoId) -> Unit,
) {
    val channel = state.shownChannel
    if (channel != null) {
        item(key = "header") {
            Header(
                channel = channel,
                expanded = state.descriptionExpanded,
                onToggle = { onIntent(ChannelIntent.DescriptionToggled) },
            )
        }
    }
    val subscription = state.subscription
    if (channel != null && state.showsCard) {
        item(key = "card") {
            SubscriptionCard(
                channelName = channel.name,
                subscribed = subscription is Subscription.Subscribed,
                onIntent = onIntent,
                modifier = Modifier.padding(top = DewfallSpacing.lg),
            )
        }
    }
    when (val load = state.load) {
        is LoadArea.Loading -> item(key = "loading") { Loading() }
        is LoadArea.Failed -> {
            item(key = "failure") {
                Failure(
                    error = load.error,
                    onTryAgain = { onIntent(ChannelIntent.TryAgain) },
                    modifier = Modifier.padding(top = DewfallSpacing.lg),
                )
            }
        }
        is LoadArea.Loaded -> uploads(load.rows, now, zone, onOpenVideo)
    }
}

/** The emblem, the name as a heading, and the description, folded to three lines with "More". */
@Composable
private fun Header(
    channel: Channel,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = DewfallSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
    ) {
        ChannelEmblem(
            initial = initialOf(channel.name),
            size = EmblemSize,
            textStyle = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = channel.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (channel.description.isNotEmpty()) {
            Description(text = channel.description, expanded = expanded, onToggle = onToggle)
        }
    }
}

/**
 * The description, at most three lines while folded. "More" shows while the folded text doesn't
 * fit, as its layout reports, and "Less" while it is unfolded.
 */
@Composable
private fun Description(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflows by remember(text) { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else FOLDED_DESCRIPTION_LINES,
            onTextLayout = { layout -> if (!expanded) overflows = layout.hasVisualOverflow },
        )
        if (expanded || overflows) {
            TextButton(onClick = onToggle) {
                Text(
                    text =
                        stringResource(
                            if (expanded) {
                                R.string.channel_page_less
                            } else {
                                R.string.channel_page_more
                            },
                        ),
                )
            }
        }
    }
}

/**
 * The latest uploads under their heading, in one rounded group, as in the "Channel page" frames, or
 * a message when the channel has none. Tapping a row opens its video through [onOpenVideo].
 */
private fun LazyListScope.uploads(
    rows: List<UploadRow>,
    now: Instant,
    zone: ZoneId,
    onOpenVideo: (VideoId) -> Unit,
) {
    if (rows.isEmpty()) {
        item(key = "no-videos") { NoVideos(modifier = Modifier.padding(top = DewfallSpacing.lg)) }
        return
    }
    item(key = "uploads-heading") {
        Text(
            text = stringResource(R.string.channel_page_uploads_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                        start = DewfallSpacing.md,
                        top = DewfallSpacing.lg,
                        bottom = DewfallSpacing.sm,
                    )
                    .semantics { heading() },
        )
    }
    itemsIndexed(items = rows, key = { _, row -> row.id.value }) { index, row ->
        val openLabel = stringResource(R.string.channel_page_open_video)
        VideoRowItem(
            title = row.title,
            channelName = null,
            duration = row.duration,
            kind = row.kind,
            published = row.published,
            now = now,
            zone = zone,
            modifier =
                Modifier.clip(rowGroupShape(index, rows.size))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(onClickLabel = openLabel) { onOpenVideo(row.id) }
                    .padding(horizontal = DewfallSpacing.sm),
        )
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.channel_page_loading)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        CircularProgressIndicator(
            modifier =
                Modifier.padding(top = DewfallSpacing.xl).semantics {
                    contentDescription = description
                },
        )
    }
}

/** The message for a channel with no videos to show. */
@Composable
private fun NoVideos(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(DewfallSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.channel_page_no_videos_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.channel_page_no_videos_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A failure, in the design system's neutral Banner, with "Try again" only when trying again can
 * help. Only the glyph is in the error color, because a failure isn't the user's fault.
 */
@Composable
private fun Failure(error: YouTubeError, onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    val offers = error.offersTryAgain
    Banner(
        message = stringResource(failureText(error)),
        glyph = painterResource(DesignR.drawable.ic_error),
        glyphDescription = stringResource(R.string.channel_page_failed_icon),
        glyphTint = MaterialTheme.colorScheme.error,
        actionLabel = if (offers) stringResource(R.string.channel_page_try_again) else null,
        onAction = if (offers) onTryAgain else null,
        modifier = modifier,
    )
}

/**
 * NETWORK says to check the connection, BLOCKED and BROKEN that YouTube isn't loading,
 * CHANNEL_CLOSED that the channel was closed, and every other category that the channel isn't
 * available.
 */
private fun failureText(error: YouTubeError): Int =
    when (error) {
        YouTubeError.NETWORK -> R.string.channel_page_failed_network
        YouTubeError.BLOCKED,
        YouTubeError.BROKEN -> R.string.channel_page_failed_youtube
        YouTubeError.CHANNEL_CLOSED -> R.string.channel_page_failed_closed
        YouTubeError.PRIVATE,
        YouTubeError.AGE_RESTRICTED,
        YouTubeError.COUNTRY_BLOCKED,
        YouTubeError.MEMBERS_OR_PAID,
        YouTubeError.UNAVAILABLE -> R.string.channel_page_failed_unavailable
    }
