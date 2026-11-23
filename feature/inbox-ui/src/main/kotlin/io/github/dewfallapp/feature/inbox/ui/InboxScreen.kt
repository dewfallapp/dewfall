package io.github.dewfallapp.feature.inbox.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.component.ChannelEmblem
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.initialOf
import io.github.dewfallapp.core.ui.VideoRowItem
import io.github.dewfallapp.feature.inbox.logic.InboxContent
import io.github.dewfallapp.feature.inbox.logic.InboxGroup
import io.github.dewfallapp.feature.inbox.logic.InboxState
import java.time.ZoneId
import kotlin.time.Instant

/** The channel emblem's width on a card, at the default font size, as in the "Inbox" frame. */
private val EmblemSize = 40.dp

/** The round badge behind an empty state's glyph, as in the inbox's empty state frames. */
private val MessageBadgeSize = 64.dp

/**
 * The inbox: its top bar, then the cards of new videos, or an empty state. It is stateless: it
 * shows [state], with time ago counted from [now] in [zone], and reports taps through
 * [onOpenVideo], [onOpenChannel] and [onOpenSearch]. Before the phone's first answer it shows only
 * the top bar.
 *
 * Its background fills the window behind the system bars. Its content sits in one centered column
 * at most ReadableWidth wide, inside the safe drawing area, so nothing is under the system bars,
 * the display cutout or the mini player.
 */
@Composable
internal fun InboxScreen(
    state: InboxState,
    now: Instant,
    zone: ZoneId,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                TopBar(onOpenSearch = onOpenSearch)
                when (val content = state.content) {
                    null -> Unit
                    is InboxContent.Videos -> {
                        Cards(
                            groups = content.groups,
                            now = now,
                            zone = zone,
                            onOpenVideo = onOpenVideo,
                            onOpenChannel = onOpenChannel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    InboxContent.NoChannels -> {
                        Message(
                            glyph = DesignR.drawable.ic_subscriptions,
                            glyphDescription = stringResource(R.string.inbox_no_channels_icon),
                            title = stringResource(R.string.inbox_no_channels_title),
                            body = stringResource(R.string.inbox_no_channels_body),
                            modifier = Modifier.weight(1f),
                        ) {
                            SearchButton(onOpenSearch = onOpenSearch)
                        }
                    }
                    is InboxContent.CaughtUp -> {
                        Message(
                            glyph = DesignR.drawable.ic_done_all,
                            glyphDescription = stringResource(R.string.inbox_caught_up_icon),
                            title = stringResource(R.string.inbox_caught_up_title),
                            body =
                                pluralStringResource(
                                    R.plurals.inbox_caught_up_body,
                                    content.channelCount,
                                    content.channelCount,
                                ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    InboxContent.Unreadable -> {
                        Message(
                            glyph = DesignR.drawable.ic_error,
                            glyphDescription = stringResource(R.string.inbox_unreadable_icon),
                            glyphTint = MaterialTheme.colorScheme.error,
                            title = stringResource(R.string.inbox_unreadable_title),
                            body = stringResource(R.string.inbox_unreadable_body),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** "Dewfall", "Inbox" under it, and the search button, as in the "Inbox" frame. */
@Composable
private fun TopBar(onOpenSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = DewfallSpacing.margin, end = DewfallSpacing.xs)
                .padding(vertical = DewfallSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                text = stringResource(R.string.inbox_app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.inbox_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSearch) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_search),
                contentDescription = stringResource(R.string.inbox_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The cards, one per channel, then the line that ends the list. The list and its scroll state live
 * only here, so after process death the list is first laid out with its cards, and the saved scroll
 * position holds. The items hold no saveable state, so the state Android saves holds no channel or
 * video ID.
 */
@Composable
private fun Cards(
    groups: List<InboxGroup>,
    now: Instant,
    zone: ZoneId,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(horizontal = DewfallSpacing.margin, vertical = DewfallSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
    ) {
        items(items = groups, key = { it.channelId.value }) { group ->
            Card(
                group = group,
                now = now,
                zone = zone,
                onOpenVideo = onOpenVideo,
                onOpenChannel = onOpenChannel,
            )
        }
        item(key = "end") {
            Text(
                text = stringResource(R.string.inbox_end),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = DewfallSpacing.lg),
            )
        }
    }
}

/** One channel's card: its channel row, which opens the channel, then its videos. */
@Composable
private fun Card(
    group: InboxGroup,
    now: Instant,
    zone: ZoneId,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openVideo = stringResource(R.string.inbox_open_video)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(DewfallSpacing.sm),
    ) {
        ChannelRow(group = group, onOpenChannel = onOpenChannel)
        for (video in group.videos) {
            VideoRowItem(
                title = video.title,
                channelName = null,
                duration = video.duration,
                kind = video.kind,
                published = video.published,
                now = now,
                zone = zone,
                modifier =
                    Modifier.clip(MaterialTheme.shapes.medium).clickable(onClickLabel = openVideo) {
                        onOpenVideo(video.id)
                    },
            )
        }
    }
}

/** The card's channel emblem, name and count, which reads as one node and opens the channel. */
@Composable
private fun ChannelRow(
    group: InboxGroup,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = stringResource(R.string.inbox_open_channel)) {
                    onOpenChannel(group.channelId)
                }
                .padding(DewfallSpacing.sm)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelEmblem(initial = initialOf(group.channelName), size = EmblemSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = group.channelName, style = MaterialTheme.typography.titleSmall)
            Text(
                text =
                    pluralStringResource(
                        R.plurals.inbox_card_count,
                        group.videos.size,
                        group.videos.size,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * An empty or failed state, as in the "Inbox, no channels yet" and "Inbox, all caught up" frames: a
 * glyph in a round badge, tinted [glyphTint], a title, a body, and an optional [action] below them.
 * It scrolls, so a large font or a small window never cuts it off.
 */
@Composable
private fun Message(
    @DrawableRes glyph: Int,
    glyphDescription: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    glyphTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: @Composable () -> Unit = {},
) {
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
                painter = painterResource(glyph),
                contentDescription = glyphDescription,
                tint = glyphTint,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

/** The "Search YouTube" button of "No channels yet". */
@Composable
private fun SearchButton(onOpenSearch: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onOpenSearch,
        modifier = modifier,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        // The glyph sits next to its word, so it has no content description of its own, as
        // AGENTS.md allows, and TalkBack reads the button once.
        Icon(
            painter = painterResource(DesignR.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Text(
            text = stringResource(R.string.inbox_search_button),
            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
        )
    }
}
