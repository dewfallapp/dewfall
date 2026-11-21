@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing

/**
 * The play and pause button, the times, the seek bar and the exit button. A live stream says "Live"
 * in place of the times and the seek bar. A failure keeps only the exit button.
 */
@Composable
internal fun ControlRow(content: VideoAreaContent, onPlay: () -> Unit, onExit: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(overlayColor())
                .padding(DewfallSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
    ) {
        when (content) {
            is VideoAreaContent.Failed -> Spacer(Modifier.weight(1f))
            is VideoAreaContent.Waiting -> {
                PlayPauseButton(showPlay = true, ended = false, enabled = true, onClick = onPlay)
                TimesAndSeekBar(Modifier.weight(1f)) {
                    content.positionMs?.let { TimesText(positionMs = it, durationMs = null) }
                    IdleSeekBar(Modifier.weight(1f))
                }
            }
            is VideoAreaContent.Playing -> {
                val player = content.player
                if (player == null) {
                    PlayPauseButton(showPlay = true, ended = false, enabled = false, onClick = {})
                    IdleSeekBar(Modifier.weight(1f))
                } else {
                    PlayingControls(player)
                }
            }
        }
        IconButton(onClick = onExit) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_fullscreen_exit),
                contentDescription = stringResource(R.string.player_exit_fullscreen),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The button, and the times and the seek bar, or "Live", for a connected [player]. */
@Composable
private fun RowScope.PlayingControls(player: Player) {
    val playPause = rememberPlayPauseButtonState(player)
    val status = rememberPlaybackStatus(player)
    PlayPauseButton(
        showPlay = playPause.showPlay,
        ended = status.ended,
        enabled = playPause.isEnabled,
        onClick = playPause::onClick,
    )
    if (status.live) {
        Text(
            text = stringResource(R.string.player_video_live),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
    } else {
        TimesAndSeekBar(Modifier.weight(1f)) {
            Times(player)
            SeekBar(player, Modifier.weight(1f))
        }
    }
}

/**
 * The times and the seek bar in a row of their own, which takes only the width the buttons leave,
 * so in a narrow window at a large font the times can't squeeze the exit button out.
 */
@Composable
private fun TimesAndSeekBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
        content = content,
    )
}

/** The seek bar while no player is connected, turned off. */
@Composable
private fun IdleSeekBar(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.player_video_position)
    Slider(
        value = 0f,
        onValueChange = {},
        enabled = false,
        modifier = modifier.semantics { contentDescription = description },
    )
}
