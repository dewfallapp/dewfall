package io.github.dewfallapp.feature.channels.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.timeAgo
import io.github.dewfallapp.core.ui.ChannelAvatar
import io.github.dewfallapp.core.ui.rowGroupShape
import io.github.dewfallapp.feature.channels.logic.ChannelsList
import io.github.dewfallapp.feature.channels.logic.ChannelsState
import java.time.ZoneId
import kotlin.time.Instant

/** The channel emblem's width in a row, at the default font size, as in the "Channels" frame. */
private val EmblemSize = 40.dp

/** The round badge behind the empty state's glyph, as in the "Channels, no channels yet" frame. */
private val MessageBadgeSize = 64.dp

/**
 * The Channels screen: its top bar, then the count and the card of channels, or the empty state. It
 * is stateless: it shows [state], with the last upload counted from [now] in [zone], and reports
 * taps through [onOpenChannel], [onOpenSearch] and [onImportFromTakeout]. Before the phone's first
 * answer it shows only the top bar.
 *
 * Its background fills the window behind the system bars. Its content sits in one centered column
 * at most ReadableWidth wide, inside the safe drawing area, so nothing is under the system bars,
 * the display cutout, the mini player or the navigation bar.
 */
@Composable
internal fun ChannelsScreen(
    state: ChannelsState,
    now: Instant,
    zone: ZoneId,
    onOpenChannel: (ChannelId) -> Unit,
    onOpenSearch: () -> Unit,
    onImportFromTakeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                TopBar(onOpenSearch = onOpenSearch)
                when (val list = state.list) {
                    ChannelsList.Loading -> Unit
                    is ChannelsList.Shown -> {
                        ChannelList(
                            channels = list.channels,
                            now = now,
                            zone = zone,
                            onOpenChannel = onOpenChannel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ChannelsList.NoChannels -> {
                        NoChannels(
                            onOpenSearch = onOpenSearch,
                            onImportFromTakeout = onImportFromTakeout,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ChannelsList.Unreadable -> Unreadable(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** "Dewfall", "Channels" under it, and the search button, as in the "Channels" frame. */
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
                text = stringResource(R.string.channels_app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.channels_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSearch) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_search),
                contentDescription = stringResource(R.string.channels_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The count, then one card of rows. The list and its scroll state live only here, so after process
 * death the list is first laid out with its rows, and the saved scroll position holds instead of
 * being spent on the empty moment before the phone answers. The items hold no saveable state, so
 * the state Android saves holds no channel ID or name.
 */
@Composable
private fun ChannelList(
    channels: List<ChannelOverview>,
    now: Instant,
    zone: ZoneId,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(horizontal = DewfallSpacing.margin, vertical = DewfallSpacing.sm),
    ) {
        item(key = "count") {
            Text(
                text = pluralStringResource(R.plurals.channels_count, channels.size, channels.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(horizontal = DewfallSpacing.md)
                        .padding(bottom = DewfallSpacing.sm),
            )
        }
        itemsIndexed(items = channels, key = { _, item -> item.subscription.channel.id.value }) {
            index,
            item ->
            ChannelRow(
                overview = item,
                now = now,
                zone = zone,
                onOpenChannel = onOpenChannel,
                modifier =
                    Modifier.clip(rowGroupShape(index, channels.size))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }
}

/**
 * One channel: its emblem, name, last upload line if it has one, and a chevron, which reads as one
 * node and opens the channel. The emblem and the chevron are hidden from screen readers, which read
 * the name and the line and hear that a tap opens the channel.
 */
@Composable
private fun ChannelRow(
    overview: ChannelOverview,
    now: Instant,
    zone: ZoneId,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val channel = overview.subscription.channel
    val lastUpload = timeAgo(overview.lastUpload, now, zone)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.channels_open_channel)) {
                    onOpenChannel(channel.id)
                }
                .padding(horizontal = DewfallSpacing.md, vertical = DewfallSpacing.sm)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(name = channel.name, avatars = channel.avatars, size = EmblemSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = channel.name, style = MaterialTheme.typography.titleMedium)
            if (lastUpload != null) {
                Text(
                    text = lastUploadText(lastUpload),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The row's click label says what the chevron shows, so screen readers skip it.
        Icon(
            painter = painterResource(DesignR.drawable.ic_chevron_right),
            contentDescription = stringResource(R.string.channels_open_channel),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * "No channels yet", as in the "Channels, no channels yet" frame: the channels glyph in a round
 * badge, the title, the body, the "Search YouTube" button, and under it "Import from Google
 * Takeout".
 */
@Composable
private fun NoChannels(
    onOpenSearch: () -> Unit,
    onImportFromTakeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Message(
        glyph = DesignR.drawable.ic_subscriptions,
        glyphDescription = stringResource(R.string.channels_no_channels_icon),
        glyphTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(R.string.channels_no_channels_title),
        body = stringResource(R.string.channels_no_channels_body),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(top = DewfallSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
        ) {
            Button(
                onClick = onOpenSearch,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_search),
                    // The button's own text names it, so the glyph has no description of its own.
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(R.string.channels_search_button),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
            TextButton(onClick = onImportFromTakeout) {
                Text(
                    text = stringResource(R.string.channels_import_button),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The phone couldn't read what it keeps: the error glyph, "Your channels can't be shown" and why,
 * as the inbox says it. The top bar's search button still works.
 */
@Composable
private fun Unreadable(modifier: Modifier = Modifier) {
    Message(
        glyph = DesignR.drawable.ic_error,
        glyphDescription = stringResource(R.string.channels_unreadable_icon),
        glyphTint = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.channels_unreadable_title),
        body = stringResource(R.string.channels_unreadable_body),
        modifier = modifier,
    )
}

/**
 * A message in place of the list: a glyph in a round badge, a title, a body, and an optional
 * [action] below them, as in the "Channels, no channels yet" frame. It scrolls, so a large font or
 * a small window never cuts it off.
 */
@Composable
private fun Message(
    @DrawableRes glyph: Int,
    glyphDescription: String,
    glyphTint: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
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
