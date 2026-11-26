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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.UploadsCheckFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.ui.ChannelAvatar
import io.github.dewfallapp.core.ui.DownloadStatusControl
import io.github.dewfallapp.core.ui.DownloadStatusWords
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
 * [onOpenVideo], [onOpenChannel], [onOpenSearch], [onImportFromTakeout], [onCheck] for "Check for
 * new videos" and the banner's "Try again", and [downloads] for a row's download control. The check
 * button's glyph turns while a check runs only when [glyphTurns], which is false when the phone's
 * animations are off. Before the phone's first answer it shows only the top bar.
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
    onImportFromTakeout: () -> Unit,
    onCheck: () -> Unit,
    downloads: RowDownloads,
    glyphTurns: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                TopBar(
                    checking = state.checking,
                    glyphTurns = glyphTurns,
                    onOpenSearch = onOpenSearch,
                    onCheck = if (state.offersCheck) onCheck else null,
                )
                Content(
                    state = state,
                    now = now,
                    zone = zone,
                    onOpenVideo = onOpenVideo,
                    onOpenChannel = onOpenChannel,
                    onOpenSearch = onOpenSearch,
                    onImportFromTakeout = onImportFromTakeout,
                    onCheck = onCheck,
                    downloads = downloads,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * What the inbox shows under its top bar for [state]: nothing before the phone's first answer, the
 * cards, or an empty or failed state, each given [modifier].
 */
@Composable
private fun Content(
    state: InboxState,
    now: Instant,
    zone: ZoneId,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onOpenSearch: () -> Unit,
    onImportFromTakeout: () -> Unit,
    onCheck: () -> Unit,
    downloads: RowDownloads,
    modifier: Modifier = Modifier,
) {
    when (val content = state.content) {
        null -> Unit
        is InboxContent.Videos -> {
            Cards(
                groups = content.groups,
                banner = state.banner,
                now = now,
                zone = zone,
                onOpenVideo = onOpenVideo,
                onOpenChannel = onOpenChannel,
                onTryAgain = onCheck,
                downloads = downloads,
                modifier = modifier,
            )
        }
        InboxContent.NoChannels -> {
            NoChannels(
                onOpenSearch = onOpenSearch,
                onImportFromTakeout = onImportFromTakeout,
                modifier = modifier,
            )
        }
        is InboxContent.CaughtUp -> {
            CaughtUp(
                channelCount = content.channelCount,
                banner = state.banner,
                onTryAgain = onCheck,
                modifier = modifier,
            )
        }
        InboxContent.Unreadable -> {
            Message(
                glyph = DesignR.drawable.ic_error,
                glyphDescription = stringResource(R.string.inbox_unreadable_icon),
                glyphTint = MaterialTheme.colorScheme.error,
                title = stringResource(R.string.inbox_unreadable_title),
                body = stringResource(R.string.inbox_unreadable_body),
                modifier = modifier,
            )
        }
    }
}

/**
 * "Dewfall", "Inbox" under it, or "Checking for new videos" while [checking], the search button,
 * and the "Check for new videos" button when [onCheck] isn't null, as in the "Inbox" frame. The
 * title reads as one node, which a screen reader reads again when its second line changes.
 */
@Composable
private fun TopBar(
    checking: Boolean,
    glyphTurns: Boolean,
    onOpenSearch: () -> Unit,
    onCheck: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = DewfallSpacing.margin, end = DewfallSpacing.xs)
                .padding(vertical = DewfallSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Text(
                text = stringResource(R.string.inbox_app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    stringResource(if (checking) R.string.inbox_checking else R.string.inbox_title),
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
        if (onCheck != null) {
            CheckButton(checking = checking, glyphTurns = glyphTurns, onCheck = onCheck)
        }
    }
}

/**
 * The cards, one per channel, after the [banner] of a check that stopped early, if any, then the
 * line that ends the list. The list and its scroll state live only here, so after process death the
 * list is first laid out with its cards, and the saved scroll position holds. The banner's slot is
 * always the first item, empty and of no height when there is no banner, because the saved position
 * is an item's index: the banner is gone after process death, and as an item that comes and goes it
 * would move every card's index. So the gaps sit in the items, not in the list's arrangement, and
 * an empty slot adds none. The list's top padding keeps the empty slot the first visible item at
 * the top, so a banner that comes while the list is at the top shows there. The items hold no
 * saveable state, so the state Android saves holds no channel or video ID.
 */
@Composable
private fun Cards(
    groups: List<InboxGroup>,
    banner: UploadsCheckFailure?,
    now: Instant,
    zone: ZoneId,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onTryAgain: () -> Unit,
    downloads: RowDownloads,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(horizontal = DewfallSpacing.margin, vertical = DewfallSpacing.sm),
    ) {
        // Channel IDs start with UC, so this key never meets a card's.
        item(key = "banner") {
            if (banner != null) {
                CheckBanner(
                    failure = banner,
                    onTryAgain = onTryAgain,
                    modifier = Modifier.padding(bottom = DewfallSpacing.md),
                )
            }
        }
        items(items = groups, key = { it.channelId.value }) { group ->
            Card(
                group = group,
                now = now,
                zone = zone,
                onOpenVideo = onOpenVideo,
                onOpenChannel = onOpenChannel,
                downloads = downloads,
                modifier = Modifier.padding(bottom = DewfallSpacing.md),
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

/**
 * One channel's card: its channel row, which opens the channel, then its videos, each with its
 * download status from [downloads], or none while the statuses can't be read.
 */
@Composable
private fun Card(
    group: InboxGroup,
    now: Instant,
    zone: ZoneId,
    onOpenVideo: (VideoId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    downloads: RowDownloads,
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
            val status = downloads.statusOf(video.id)
            VideoRowItem(
                title = video.title,
                channelName = null,
                duration = video.duration,
                kind = video.kind,
                thumbnails = video.thumbnails,
                published = video.published,
                now = now,
                zone = zone,
                modifier =
                    Modifier.clip(MaterialTheme.shapes.medium).clickable(onClickLabel = openVideo) {
                        onOpenVideo(video.id)
                    },
                statusWords = status?.let { { DownloadStatusWords(it) } },
                statusControl =
                    status?.let {
                        {
                            DownloadStatusControl(
                                status = it,
                                onDownload = { downloads.onDownload(video) },
                                onPause = { downloads.onPause(video.id) },
                            )
                        }
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
        ChannelAvatar(name = group.channelName, avatars = group.channelAvatars, size = EmblemSize)
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

/**
 * "No channels yet", with its buttons: "Search YouTube", and under it "Import from Google Takeout",
 * as in the "Inbox, no channels yet" frame.
 */
@Composable
private fun NoChannels(
    onOpenSearch: () -> Unit,
    onImportFromTakeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Message(
        glyph = DesignR.drawable.ic_subscriptions,
        glyphDescription = stringResource(R.string.inbox_no_channels_icon),
        title = stringResource(R.string.inbox_no_channels_title),
        body = stringResource(R.string.inbox_no_channels_body),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(top = DewfallSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
        ) {
            SearchButton(onOpenSearch = onOpenSearch)
            TextButton(onClick = onImportFromTakeout) {
                Text(
                    text = stringResource(R.string.inbox_import_button),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * "You're all caught up" for [channelCount] channels, under the [banner] of a check that stopped
 * early, if any, whose "Try again" calls [onTryAgain].
 */
@Composable
private fun CaughtUp(
    channelCount: Int,
    banner: UploadsCheckFailure?,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (banner != null) {
            CheckBanner(
                failure = banner,
                onTryAgain = onTryAgain,
                modifier =
                    Modifier.padding(horizontal = DewfallSpacing.margin)
                        .padding(top = DewfallSpacing.sm),
            )
        }
        Message(
            glyph = DesignR.drawable.ic_done_all,
            glyphDescription = stringResource(R.string.inbox_caught_up_icon),
            title = stringResource(R.string.inbox_caught_up_title),
            body = pluralStringResource(R.plurals.inbox_caught_up_body, channelCount, channelCount),
            modifier = Modifier.weight(1f),
        )
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
