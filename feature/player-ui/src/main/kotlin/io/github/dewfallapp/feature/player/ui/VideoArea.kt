@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.component.Banner
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.feature.player.logic.clockText

/** How often the times tick: once a second, the smallest step they show. */
private const val TIMES_TICK_MS = 1000L

/** The 16:9 shape of the video's box. */
private const val VIDEO_ASPECT_RATIO = 16f / 9f

/**
 * The most of the window's height that the box takes, so a short window, such as split screen,
 * keeps room for the controls and the details.
 */
private const val MAX_WINDOW_HEIGHT_SHARE = 0.45f

/** What the video area shows. */
internal sealed interface VideoAreaContent {
    /** The [player] while it is connected, or null while it connects. */
    data class Playing(val player: Player?) : VideoAreaContent

    /**
     * Nothing plays, because the session was gone. It waits at [positionMs], or at the start or
     * live edge when null, for the user to press play.
     */
    data class Waiting(val positionMs: Long?) : VideoAreaContent

    /** Playback failed, for the reason that [failure] names. */
    data class Failed(val failure: PlaybackFailure) : VideoAreaContent
}

/**
 * The video area at the top of the video screen, as the "Player" frames show it: the picture in a
 * 16:9 box with rounded corners, the seek bar, then the play and pause button and the times. A live
 * stream says "Live" in place of the seek bar and the times. A failure shows a Banner in the box's
 * place, with "Try again" where it can help. The screen stays on while the video plays.
 *
 * It reads the player through media3-ui-compose's state holders and [rememberPlaybackStatus], at
 * the rate the visible controls need, and sends play, pause and seek straight to it. [onPlay] runs
 * when the user presses play while it waits, and [onTryAgain] for the failure's "Try again".
 */
@Composable
internal fun VideoArea(
    content: VideoAreaContent,
    onPlay: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
    ) {
        when (content) {
            is VideoAreaContent.Failed -> PlaybackFailureBanner(content.failure, onTryAgain)
            is VideoAreaContent.Waiting -> {
                Picture(player = null, playing = false, loading = false)
                IdleControls(positionMs = content.positionMs, onPlay = onPlay)
            }
            is VideoAreaContent.Playing -> {
                val player = content.player
                if (player == null) {
                    Picture(player = null, playing = false, loading = true)
                    IdleControls(positionMs = null, onPlay = null)
                } else {
                    PlayerArea(player)
                }
            }
        }
    }
}

@Composable
private fun PlayerArea(player: Player) {
    val playPause = rememberPlayPauseButtonState(player)
    val status = rememberPlaybackStatus(player)
    Column(verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        Picture(player = player, playing = !playPause.showPlay, loading = status.loading)
        if (status.live) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
            ) {
                PlayPauseButton(
                    showPlay = playPause.showPlay,
                    ended = status.ended,
                    enabled = playPause.isEnabled,
                    onClick = playPause::onClick,
                )
                Text(
                    text = stringResource(R.string.player_video_live),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        } else {
            SeekBar(player)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
            ) {
                PlayPauseButton(
                    showPlay = playPause.showPlay,
                    ended = status.ended,
                    enabled = playPause.isEnabled,
                    onClick = playPause::onClick,
                )
                Times(player)
            }
        }
    }
}

/**
 * The 16:9 box with the picture, drawn by ContentFrame on a SurfaceView. A picture of another shape
 * fits inside it. Compose's clip doesn't round a SurfaceView, so the corners are drawn over it in
 * the screen's background role. The shutter before the first frame is a color role too, not
 * ContentFrame's black.
 */
@Composable
private fun Picture(player: Player?, playing: Boolean, loading: Boolean) {
    val shutter = MaterialTheme.colorScheme.surfaceContainerHighest
    val maxHeight =
        with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * MAX_WINDOW_HEIGHT_SHARE).toDp()
        }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(max = maxHeight)
                .aspectRatio(VIDEO_ASPECT_RATIO)
                .then(if (playing) Modifier.keepScreenOn() else Modifier)
                .cornersOver(MaterialTheme.shapes.large, MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (player == null) {
            Box(Modifier.fillMaxSize().background(shutter))
        } else {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                shutter = { Box(Modifier.fillMaxSize().background(shutter)) },
            )
        }
        if (loading) {
            val description = stringResource(R.string.player_loading)
            CircularProgressIndicator(Modifier.semantics { contentDescription = description })
        }
    }
}

/** Draws [color] over the parts of the box outside [shape], after the box's content. */
private fun Modifier.cornersOver(shape: Shape, color: Color): Modifier = drawWithCache {
    val rounded =
        Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val whole = Path().apply { addRect(Rect(Offset.Zero, size)) }
    val corners = Path().apply { op(whole, rounded, PathOperation.Difference) }
    onDrawWithContent {
        drawContent()
        drawPath(corners, color)
    }
}

/**
 * The seek bar, which moves on as many ticks as it has pixels. Dragging it shows the new point, and
 * letting go moves playback there.
 */
@Composable
private fun SeekBar(player: Player) {
    var ticks by remember { mutableIntStateOf(1) }
    val progress = rememberProgressStateWithTickCount(player, ticks)
    LaunchedEffect(progress, ticks) { progress.updateTotalTickCount(ticks) }
    var dragged by remember { mutableStateOf<Float?>(null) }
    val description = stringResource(R.string.player_video_position)
    Slider(
        value = dragged ?: progress.currentPositionProgress,
        onValueChange = { dragged = it },
        onValueChangeFinished = {
            dragged?.let { player.seekTo(progress.progressToPosition(it)) }
            dragged = null
        },
        enabled = progress.changingProgressEnabled,
        modifier =
            Modifier.fillMaxWidth()
                .onSizeChanged { ticks = it.width.coerceAtLeast(1) }
                .semantics { contentDescription = description },
    )
}

/** The position and the length, such as "18:24 / 1:14:02", ticking once a second. */
@Composable
private fun Times(player: Player) {
    val progress = rememberProgressStateWithTickInterval(player, TIMES_TICK_MS)
    TimesText(positionMs = progress.currentPositionMs, durationMs = progress.durationMs)
}

@Composable
private fun TimesText(positionMs: Long, durationMs: Long?) {
    val text =
        if (durationMs == null || durationMs == C.TIME_UNSET) {
            clockText(positionMs)
        } else {
            stringResource(
                R.string.player_video_times,
                clockText(positionMs),
                clockText(durationMs),
            )
        }
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

/**
 * The controls while no player is connected: the seek bar at the start, turned off, and the play
 * button, which runs [onPlay], or is turned off when it is null. [positionMs] is the saved
 * position, shown with no length, or none when it is null.
 */
@Composable
private fun IdleControls(positionMs: Long?, onPlay: (() -> Unit)?) {
    val description = stringResource(R.string.player_video_position)
    Column(verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        Slider(
            value = 0f,
            onValueChange = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
        ) {
            PlayPauseButton(
                showPlay = true,
                ended = false,
                enabled = onPlay != null,
                onClick = { onPlay?.invoke() },
            )
            if (positionMs != null) TimesText(positionMs = positionMs, durationMs = null)
        }
    }
}

/**
 * The play and pause button, filled in the primary role as the frames show. At the end of a video
 * it offers to play it again from the start.
 */
@Composable
private fun PlayPauseButton(
    showPlay: Boolean,
    ended: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description =
        when {
            !showPlay -> R.string.player_video_pause
            ended -> R.string.player_video_play_again
            else -> R.string.player_video_play
        }
    val glyph = if (showPlay) DesignR.drawable.ic_play_arrow else DesignR.drawable.ic_pause_filled
    FilledIconButton(onClick = onClick, enabled = enabled) {
        Icon(painter = painterResource(glyph), contentDescription = stringResource(description))
    }
}

/**
 * A playback failure, in the design system's Banner, in the box's place, with "Try again" only when
 * trying again can help.
 */
@Composable
private fun PlaybackFailureBanner(failure: PlaybackFailure, onTryAgain: () -> Unit) {
    val offers = failure.offersTryAgain
    Banner(
        message = stringResource(playbackFailureText(failure)),
        glyph = painterResource(DesignR.drawable.ic_error),
        glyphDescription = stringResource(R.string.player_video_failed_icon),
        glyphTint = MaterialTheme.colorScheme.error,
        actionLabel = if (offers) stringResource(R.string.player_try_again) else null,
        onAction = if (offers) onTryAgain else null,
    )
}
