@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallOpacity
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.feature.player.logic.FullscreenControls
import io.github.dewfallapp.feature.player.logic.VideoPage
import kotlinx.coroutines.delay

/**
 * How long the controls stay after the last touch while the video plays, before the accessibility
 * settings ask for more.
 */
private const val CONTROLS_TIMEOUT_MS = 3000L

/** The widest the title's box gets, as the "Player full screen" frames show. */
private val TitleMaxWidth = 480.dp

/** The shape of the box that stands in for the picture before the first frame. */
private const val SHUTTER_ASPECT_RATIO = 16f / 9f

/**
 * The fullscreen player, after the "Player full screen" frames: the picture as large as the window
 * allows on black, the title and channel name of [page] at the top, once the details show, and the
 * controls at the bottom. It uses the dark scheme in light mode too. The title, the controls and
 * the system bars hide while the video plays, as [FullscreenControls] decides. [FullscreenWindow]
 * turns a phone to landscape while it shows.
 *
 * It shows [content] as the video area does: [onPlay] runs when the user presses play while it
 * waits, [onTryAgain] for a failure's "Try again", and [onExit] for the exit button. The back
 * gesture's handler is the caller's, because BackHandler must be called unconditionally.
 */
@Composable
internal fun FullscreenPlayer(
    content: VideoAreaContent,
    page: VideoPage?,
    onPlay: () -> Unit,
    onTryAgain: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = (content as? VideoAreaContent.Playing)?.player
    val status = player?.let { rememberPlaybackStatus(it) }
    val playing = status?.playing == true
    val controls = rememberControlsState(playing)
    val visible = controls.visible
    FullscreenWindow(barsShown = visible)
    val focus = remember { FocusRequester() }
    // Keys reach onPreviewKeyEvent only through a focused node, so fullscreen takes focus.
    LaunchedEffect(focus) { focus.requestFocus() }
    DewfallTheme(darkTheme = true) {
        // No Surface sits under the overlays, so their text takes the dark scheme's onSurface.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            FullscreenBox(
                content = content,
                page = page,
                controls = controls,
                playing = playing,
                loading =
                    status?.loading == true ||
                        (content is VideoAreaContent.Playing && player == null),
                focus = focus,
                onPlay = onPlay,
                onTryAgain = onTryAgain,
                onExit = onExit,
                modifier = modifier,
            )
        }
    }
}

/**
 * The black window with the picture, the [controls] state's taps, touches and keys, and the
 * overlays while they show. It takes focus through [focus], so keys reach it.
 */
@Composable
private fun FullscreenBox(
    content: VideoAreaContent,
    page: VideoPage?,
    controls: ControlsState,
    playing: Boolean,
    loading: Boolean,
    focus: FocusRequester,
    onPlay: () -> Unit,
    onTryAgain: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = controls.visible
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .then(if (playing) Modifier.keepScreenOn() else Modifier)
                .pointerInput(controls) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            controls.onTouch()
                        }
                    }
                }
                .onPreviewKeyEvent {
                    controls.onKey()
                    false
                }
                .focusRequester(focus)
                // A screen reader moves among the controls itself, and the controls always show
                // then, so the root takes no focus and gives TalkBack no unnamed stop.
                .focusable(enabled = !controls.screenReader),
    ) {
        val tapLabel =
            stringResource(
                if (visible) R.string.player_hide_controls else R.string.player_show_controls,
            )
        FullscreenPicture(
            content = content,
            loading = loading,
            onTryAgain = onTryAgain,
            modifier =
                Modifier.fillMaxSize().clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = tapLabel,
                ) {
                    controls.onTap()
                },
        )
        if (visible) {
            Overlays(content = content, page = page, onPlay = onPlay, onExit = onExit)
        }
    }
}

/**
 * Whether the fullscreen controls show, after [FullscreenControls], and what touches, keys and taps
 * do to them. See [rememberControlsState].
 */
@Stable
private class ControlsState {
    var controls by mutableStateOf(FullscreenControls())
    var playing by mutableStateOf(false)
    var screenReader by mutableStateOf(false)

    /** Counts touches and keys, so each one starts the wait again. */
    var touches by mutableIntStateOf(0)
        private set

    val visible: Boolean
        get() = controls.visible(playing, screenReader)

    /** A touch anywhere starts the wait again. A tap on the picture also calls [onTap]. */
    fun onTouch() {
        touches++
    }

    /** A key, such as from a hardware keyboard or Switch Access, shows them and starts the wait. */
    fun onKey() {
        controls = controls.touched()
        touches++
    }

    fun onTap() {
        controls = controls.tapped(playing, screenReader)
    }
}

/**
 * The fullscreen controls' state while the video is [playing] or not. Pausing, the end, a failure
 * and playing again bring them back. While they show, the video plays and no screen reader is on,
 * they hide after the recommended timeout, which the accessibility settings may lengthen.
 */
@Composable
private fun rememberControlsState(playing: Boolean): ControlsState {
    val state = remember { ControlsState() }
    state.playing = playing
    state.screenReader = rememberTouchExploration()
    val timeoutMs =
        LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
            CONTROLS_TIMEOUT_MS,
            containsIcons = true,
            containsText = true,
            containsControls = true,
        ) ?: CONTROLS_TIMEOUT_MS
    LaunchedEffect(playing) { state.controls = state.controls.playingChanged() }
    LaunchedEffect(state.controls, playing, state.screenReader, state.touches, timeoutMs) {
        if (state.controls.hidesAfterTimeout(playing, state.screenReader)) {
            delay(timeoutMs)
            state.controls = state.controls.timedOut()
        }
    }
    return state
}

/**
 * The picture, fitted to the window and kept clear of the display cutout, so showing or hiding the
 * system bars never moves it. Before the first frame a 16:9 box in `surfaceContainerHighest` stands
 * in for it, as in the frames. A failure shows its Banner in the picture's place.
 */
@Composable
private fun FullscreenPicture(
    content: VideoAreaContent,
    loading: Boolean,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shutter = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier.windowInsetsPadding(WindowInsets.displayCutout),
        contentAlignment = Alignment.Center,
    ) {
        val player = (content as? VideoAreaContent.Playing)?.player
        when {
            content is VideoAreaContent.Failed -> {
                PlaybackFailureBanner(
                    failure = content.failure,
                    onTryAgain = onTryAgain,
                    modifier = Modifier.widthIn(max = ReadableWidth).padding(DewfallSpacing.margin),
                )
            }
            player != null -> {
                ContentFrame(
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                    shutter = { Shutter(shutter) },
                )
            }
            else -> Shutter(shutter)
        }
        if (loading) {
            val description = stringResource(R.string.player_loading)
            CircularProgressIndicator(Modifier.semantics { contentDescription = description })
        }
    }
}

@Composable
private fun Shutter(color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(SHUTTER_ASPECT_RATIO).background(color))
    }
}

/**
 * The title's box at the top and the control row at the bottom, 16dp inside the system bars, even
 * while they are hidden, and the display cutout, so no bar ever covers them, and showing the bars
 * moves nothing.
 */
// systemBarsIgnoringVisibility is still marked experimental in the Compose version the app uses.
// Kotlin's OptIn, because androidx.annotation.OptIn in this file is for Media3's UnstableApi.
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overlays(
    content: VideoAreaContent,
    page: VideoPage?,
    onPlay: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout),
                )
                .padding(DewfallSpacing.md),
    ) {
        // The row is measured before the weighted title, so a long title at a large font never
        // pushes the controls off: the title scrolls in the space left, and is never cut off.
        if (page != null) {
            TitleBox(page, Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
        }
        Spacer(Modifier.weight(1f))
        ControlRow(content = content, onPlay = onPlay, onExit = onExit)
    }
}

/** The title and the channel name on `surface` at the duration-scrim opacity. */
@Composable
private fun TitleBox(page: VideoPage, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .widthIn(max = TitleMaxWidth)
                .clip(MaterialTheme.shapes.large)
                .background(overlayColor())
                .padding(horizontal = DewfallSpacing.md, vertical = DewfallSpacing.sm),
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = page.channelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The play and pause button, the times, the seek bar and the exit button. A live stream says "Live"
 * in place of the times and the seek bar. A failure keeps only the exit button.
 */
@Composable
private fun ControlRow(content: VideoAreaContent, onPlay: () -> Unit, onExit: () -> Unit) {
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
                content.positionMs?.let { TimesText(positionMs = it, durationMs = null) }
                IdleSeekBar(Modifier.weight(1f))
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
        Times(player)
        SeekBar(player, Modifier.weight(1f))
    }
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

/** `surface` at the design system's duration-scrim opacity, behind text and controls over video. */
@Composable
private fun overlayColor() =
    MaterialTheme.colorScheme.surface.copy(alpha = DewfallOpacity.DURATION_SCRIM)
