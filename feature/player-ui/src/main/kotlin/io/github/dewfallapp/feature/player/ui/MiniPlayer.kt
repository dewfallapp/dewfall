@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.feature.player.logic.miniPlayerContent

/** The progress line along the mini player's top edge, as the frames draw it. */
private val ProgressLineHeight = 2.dp

/** The smallest touch target, which the title's tap area keeps at any font size. */
private val MinTouchTarget = 48.dp

/**
 * Hands the mini player the app's PlaybackConnection. A composable can't take an injected
 * constructor, and the public composable takes only plain values and actions.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MiniPlayerEntryPoint {
    fun playbackConnection(): PlaybackConnection
}

/**
 * The mini player, after the "Mini player on the inbox" frames: the video that plays, while no
 * video screen shows. `:app` places it at the bottom of the screen. It draws nothing while the
 * player has no video. Tapping its title calls [onOpenVideo] with the video, and its play, pause
 * and close go straight to the player, as ADR 0002's note of November 21, 2026 allows. It reads the
 * player through media3-ui-compose's state holders, never through screen state.
 */
@Composable
fun MiniPlayer(onOpenVideo: (VideoId) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val connection =
        remember(context) {
            EntryPointAccessors.fromApplication(context, MiniPlayerEntryPoint::class.java)
                .playbackConnection()
        }
    val player by connection.player.collectAsStateWithLifecycle()
    player?.let { MiniPlayerFor(it, connection, onOpenVideo, modifier) }
}

/** The mini player for the connected [player], or nothing while it has no item. */
@Composable
private fun MiniPlayerFor(
    player: Player,
    connection: PlaybackConnection,
    onOpenVideo: (VideoId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = rememberCurrentMediaItemState(player)
    val content =
        miniPlayerContent(
            currentItemId = item.mediaItem?.mediaId,
            title = item.mediaMetadata.title,
            channelName = item.mediaMetadata.artist,
            live = item.isLive,
        ) ?: return
    val playPause = rememberPlayPauseButtonState(player)
    val status = rememberPlaybackStatus(player)
    var ticks by remember { mutableIntStateOf(1) }
    val progress = rememberProgressStateWithTickCount(player, ticks)
    LaunchedEffect(progress, ticks) { progress.updateTotalTickCount(ticks) }
    MiniPlayerBar(
        title = content.title ?: stringResource(R.string.mini_player_now_playing),
        channelName = content.channelName,
        progress = if (content.showsProgress) ({ progress.currentPositionProgress }) else null,
        showPlay = playPause.showPlay,
        ended = status.ended,
        playEnabled = playPause.isEnabled,
        onPlayPause = playPause::onClick,
        onOpen = { connection.videoOf(content.playbackId)?.let(onOpenVideo) },
        onClose = { connection.stop(content.playbackId) },
        modifier = modifier.onSizeChanged { ticks = it.width.coerceAtLeast(1) },
    )
}

/**
 * The mini player's bar, with plain values, for the previews and screenshot tests: a line along the
 * top that [progress] fills, or none for a live stream, then the [title] and [channelName], which
 * [onOpen] opens, the play and pause button, and the close button. Its background reaches under the
 * navigation bar, and its content stays clear of the system bars and the display cutout, in a
 * column of readable width.
 */
@Composable
internal fun MiniPlayerBar(
    title: String,
    channelName: String?,
    progress: (() -> Float)?,
    showPlay: Boolean,
    ended: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (progress != null) {
            ProgressLine(progress)
        } else {
            Spacer(Modifier.height(ProgressLineHeight))
        }
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ),
            contentAlignment = Alignment.TopCenter,
        ) {
            MiniPlayerRow(
                title = title,
                channelName = channelName,
                showPlay = showPlay,
                ended = ended,
                playEnabled = playEnabled,
                onPlayPause = onPlayPause,
                onOpen = onOpen,
                onClose = onClose,
            )
        }
    }
}

/** The title and channel name, which [onOpen] opens, the play and pause button and the close. */
@Composable
private fun MiniPlayerRow(
    title: String,
    channelName: String?,
    showPlay: Boolean,
    ended: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier.widthIn(max = ReadableWidth)
                .fillMaxWidth()
                .padding(
                    start = DewfallSpacing.md,
                    end = DewfallSpacing.xs,
                    top = DewfallSpacing.sm,
                    bottom = DewfallSpacing.sm,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
    ) {
        Column(
            modifier =
                Modifier.weight(1f)
                    .heightIn(min = MinTouchTarget)
                    .clickable(
                        onClickLabel = stringResource(R.string.mini_player_open),
                        onClick = onOpen,
                    ),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (channelName != null) {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlayPauseButton(
            showPlay = showPlay,
            ended = ended,
            enabled = playEnabled,
            onClick = onPlayPause,
        )
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_close),
                contentDescription = stringResource(R.string.mini_player_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The line that shows how much has played: `primary` on `surfaceContainerHighest`. It reads
 * [progress] while drawing, so its ticks redraw only the line. Screen readers skip it.
 */
@Composable
private fun ProgressLine(progress: () -> Float) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val indicator = MaterialTheme.colorScheme.primary
    Spacer(
        modifier =
            Modifier.fillMaxWidth()
                .height(ProgressLineHeight)
                .clearAndSetSemantics {}
                .drawBehind {
                    drawRect(track)
                    val width = size.width * progress().coerceIn(0f, 1f)
                    // It fills from the start edge, as the seek bar does in either direction.
                    val left =
                        if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                    drawRect(indicator, topLeft = Offset(left, 0f), size = Size(width, size.height))
                },
    )
}
