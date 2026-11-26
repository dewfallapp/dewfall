package io.github.dewfallapp.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.component.Banner
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.timeAgo
import io.github.dewfallapp.core.ui.ChannelAvatar
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerIntent
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.SavedWatch
import io.github.dewfallapp.feature.player.logic.VideoPage
import java.time.ZoneId
import kotlin.time.Instant

/**
 * The channel's emblem at the default font size, as in the "Player" frames and the design system's
 * ChannelHeader. It grows with the font size, so the initial always fits.
 */
private val EmblemSize = 36.dp

/** A chip's glyph, as the design system's Chip component sets it. */
private val ChipGlyphSize = 18.dp

/** The width of an unselected chip's border, as the design system's Chip component sets it. */
private val ChipBorderWidth: Dp = 1.dp

/**
 * The video screen: a minimize button, then [videoArea], then the video's details, or loading, or a
 * failure. It is stateless: it shows [state], with the time ago counted from [now] in [zone], and
 * reports what the user does through [onIntent], [onMinimize] and [onOpenChannel]. The video area
 * reads the player itself, so playback state never enters [state].
 *
 * Its background fills the window behind the system bars. Its content sits inside the safe drawing
 * area, in one column at most [ReadableWidth] wide, so nothing is under the system bars or the
 * display cutout.
 */
@Composable
internal fun PlayerScreen(
    state: PlayerState,
    now: Instant,
    zone: ZoneId,
    onIntent: (PlayerIntent) -> Unit,
    onMinimize: () -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
    videoArea: @Composable (Modifier) -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier.padding(horizontal = DewfallSpacing.xs),
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_keyboard_arrow_down),
                        contentDescription = stringResource(R.string.player_minimize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                videoArea(Modifier.padding(horizontal = DewfallSpacing.margin))
                val areaModifier = Modifier.weight(1f)
                when (val details = state.details) {
                    is DetailsArea.Loading -> Loading(modifier = areaModifier)
                    is DetailsArea.Shown -> {
                        Details(
                            page = details.page,
                            saved = state.saved,
                            now = now,
                            zone = zone,
                            onOpenChannel = { onOpenChannel(details.page.channelId) },
                            onIntent = onIntent,
                            modifier = areaModifier,
                        )
                    }
                    is DetailsArea.Failed -> {
                        Failure(
                            error = details.error,
                            onTryAgain = { onIntent(PlayerIntent.TryAgain) },
                            modifier = areaModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.player_loading)
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
 * The video's details, after the "Player" frames: the title, the publish line, the watched chip
 * once the database has answered with what it holds, [saved], the channel and the description,
 * which the user can select and copy. They scroll, so a long description or a large font never cuts
 * anything off.
 */
@Composable
private fun Details(
    page: VideoPage,
    saved: SavedWatch,
    now: Instant,
    zone: ZoneId,
    onOpenChannel: () -> Unit,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DewfallSpacing.margin)
                .padding(top = DewfallSpacing.sm, bottom = DewfallSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DewfallSpacing.xs)) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            publishLine(page, now, zone)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (saved is SavedWatch.Known) {
            // A row of chips, where Phase 6's "Downloaded" and Phase 7's "Keep" join it later.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
                val tap =
                    if (saved.watched) PlayerIntent.MarkUnwatched else PlayerIntent.MarkWatched
                WatchedChip(watched = saved.watched, onClick = { onIntent(tap) })
            }
        }
        Channel(name = page.channelName, avatars = page.channelAvatars, onOpen = onOpenChannel)
        if (page.description.isNotEmpty()) {
            SelectionContainer {
                Text(text = page.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * The design system's toggle chip for whether the video is [watched]: "Mark as watched" with the
 * done_all glyph while it isn't, and "Watched", selected, with the check glyph while it is. The
 * glyph sits next to its word, so it has no content description of its own, and FilterChip tells
 * screen readers whether it is selected. See the Chip component's README in docs/design/.
 */
@Composable
private fun WatchedChip(watched: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = watched,
        onClick = onClick,
        label = {
            Text(
                text =
                    stringResource(
                        if (watched) R.string.player_watched else R.string.player_mark_watched,
                    ),
            )
        },
        modifier = modifier,
        leadingIcon = {
            Icon(
                painter =
                    painterResource(
                        if (watched) DesignR.drawable.ic_check else DesignR.drawable.ic_done_all,
                    ),
                contentDescription = null,
                modifier = Modifier.size(ChipGlyphSize),
            )
        },
        shape = MaterialTheme.shapes.medium,
        colors =
            FilterChipDefaults.filterChipColors(
                labelColor = colors.onSurfaceVariant,
                iconColor = colors.onSurfaceVariant,
                selectedContainerColor = colors.secondaryContainer,
                selectedLabelColor = colors.onSecondaryContainer,
                selectedLeadingIconColor = colors.onSecondaryContainer,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = watched,
                borderColor = colors.outline,
                borderWidth = ChipBorderWidth,
            ),
    )
}

/** "Live now", how long ago the video was published, or null when YouTube gave no past time. */
@Composable
private fun publishLine(page: VideoPage, now: Instant, zone: ZoneId): String? =
    if (page.isLive) {
        stringResource(R.string.player_live_now)
    } else {
        timeAgo(page.published, now, zone)?.let { publishedText(it) }
    }

/**
 * The channel, in a rounded box: its picture from [avatars] over its emblem with the initial, its
 * name, and a chevron that shows that the box opens the channel's page through [onOpen], as in the
 * "Player" frames. The picture, the emblem and the chevron are hidden from screen readers, which
 * read the name and hear that a tap opens it.
 */
@Composable
private fun Channel(
    name: String,
    avatars: List<Thumbnail>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.player_open_channel, name)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClickLabel = openLabel, onClick = onOpen)
                .padding(DewfallSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(name = name, avatars = avatars, size = EmblemSize)
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        // The box's click label says what the chevron shows, so screen readers skip it.
        Icon(
            painter = painterResource(DesignR.drawable.ic_chevron_right),
            contentDescription = openLabel,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * A failure, in the design system's neutral Banner, with "Try again" only when trying again can
 * help. It scrolls, so a large font never cuts it off.
 */
@Composable
private fun Failure(error: YouTubeError, onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val offers = error.offersTryAgain
        Banner(
            message = stringResource(failureText(error)),
            glyph = painterResource(DesignR.drawable.ic_error),
            glyphDescription = stringResource(R.string.player_failed_icon),
            glyphTint = MaterialTheme.colorScheme.error,
            actionLabel = if (offers) stringResource(R.string.player_try_again) else null,
            onAction = if (offers) onTryAgain else null,
            modifier =
                Modifier.padding(horizontal = DewfallSpacing.margin, vertical = DewfallSpacing.sm),
        )
    }
}
