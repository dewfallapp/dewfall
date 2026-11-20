package io.github.dewfallapp.feature.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.withCreationCallback
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.feature.player.logic.PlayerIntent
import io.github.dewfallapp.feature.player.logic.StartAction
import io.github.dewfallapp.feature.player.logic.pausesOnLeave
import io.github.dewfallapp.feature.player.logic.startAction
import io.github.dewfallapp.feature.player.logic.videoAreaFailure
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The video screen for [videoId], with its ViewModel and its playback. [onClose] runs when the user
 * taps the close button. The app's navigation gives each screen its own ViewModelStoreOwner, so
 * each video gets its own ViewModel. See openspec/specs/player/spec.md.
 *
 * Playback starts the first time the screen opens. Leaving the app keeps it playing in the
 * background, unless it hasn't played yet, and closing the screen stops it, through the ViewModel.
 * When the screen comes back after the system ended the process or stopped the player, it waits at
 * the saved position for the user to press play.
 */
@Composable
fun PlayerRoute(videoId: VideoId, onClose: () -> Unit, modifier: Modifier = Modifier) {
    PlayerContent(onClose = onClose, viewModel = playerViewModel(videoId), modifier = modifier)
}

/** Connects the screen to its ViewModel and to the player. */
@Composable
internal fun PlayerContent(
    onClose: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback = viewModel.playback
    val player by playback.player.collectAsStateWithLifecycle()
    val failure by playback.failure.collectAsStateWithLifecycle()
    // Android's saved state keeps these through process death, as the privacy requirement says.
    var started by rememberSaveable { mutableStateOf(false) }
    var playbackId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPositionMs by rememberSaveable { mutableLongStateOf(C.TIME_UNSET) }
    // The playback ID that has played, so leaving the app keeps it playing.
    var playedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Read again when the player connects or goes, because the session's end clears the IDs.
    val known = remember(player, playbackId) { playbackId?.let(playback::isKnown) == true }

    // Each start keeps its playback ID and the position it starts from, so a retry that fails
    // before the player has an item still goes on from that position.
    fun recordStart(id: String, positionMs: Long) {
        playbackId = id
        started = true
        if (positionMs != C.TIME_UNSET) savedPositionMs = positionMs
    }

    fun start(positionMs: Long) {
        recordStart(viewModel.startPlayback(positionMs), positionMs)
    }

    LaunchedEffect(Unit) {
        if (startAction(started, known) == StartAction.PLAY) start(C.TIME_UNSET)
    }
    LeavingRules(
        player = player,
        playbackId = playbackId,
        playedId = playedId,
        onPlaying = { playedId = it },
        onSave = { savedPositionMs = it },
        onPause = playback::pause,
    )
    // Try again, or play after the session was gone, goes on from the last position.
    val retryPositionMs = {
        player?.takeIf { it.currentMediaItem?.mediaId == playbackId }?.let(::resumePosition)
            ?: savedPositionMs
    }

    val content =
        videoAreaContent(
            failure = videoAreaFailure(state.details, failure),
            waiting = started && !known,
            savedPositionMs = savedPositionMs,
            player = player,
        )
    // The clock is read once per answer, so the screen itself stays a function of what it gets.
    val now = remember(state.details) { Clock.System.now() }
    val zone = remember(state.details) { ZoneId.systemDefault() }
    PlayerScreen(
        state = state,
        now = now,
        zone = zone,
        onIntent = { intent ->
            if (intent == PlayerIntent.TryAgain) {
                val positionMs = retryPositionMs()
                viewModel.tryAgainDetails(failure, positionMs)?.let { id ->
                    recordStart(id, positionMs)
                }
            } else {
                viewModel.dispatch(intent)
            }
        },
        onClose = onClose,
        modifier = modifier,
        videoArea = { areaModifier ->
            VideoArea(
                content = content,
                onPlay = { start(retryPositionMs()) },
                onTryAgain = { start(retryPositionMs()) },
                modifier = areaModifier,
            )
        },
    )
}

/**
 * What leaving the app does with this screen's playback, [playbackId]: [FollowPlayback] keeps the
 * position and which ID has played, [playedId], up to date, and [SaveAndPauseOnStop] acts when the
 * screen stops.
 */
@Composable
private fun LeavingRules(
    player: Player?,
    playbackId: String?,
    playedId: String?,
    onPlaying: (String) -> Unit,
    onSave: (Long) -> Unit,
    onPause: (String) -> Unit,
) {
    FollowPlayback(player, playbackId, onPlaying = onPlaying, onSave = onSave)
    SaveAndPauseOnStop(
        player,
        playbackId,
        played = playbackId != null && playedId == playbackId,
        onSave = onSave,
        onPause = onPause,
    )
}

/**
 * While the screen exists, even with the app in the background, tells [onPlaying] the playback ID
 * once its item plays, and hands [onSave] the position whenever this screen's item pauses or ends.
 * So when Android stops a player that paused in the background, the screen waits at the position
 * where it paused. Like ON_STOP, it never saves from an idle player, whose position after a failure
 * may not be where the video was.
 */
@Composable
private fun FollowPlayback(
    player: Player?,
    playbackId: String?,
    onPlaying: (String) -> Unit,
    onSave: (Long) -> Unit,
) {
    val played by rememberUpdatedState(onPlaying)
    val save by rememberUpdatedState(onSave)
    DisposableEffect(player, playbackId) {
        if (player == null || playbackId == null) return@DisposableEffect onDispose {}
        fun follow() {
            if (player.currentMediaItem?.mediaId != playbackId) return
            if (player.isPlaying) played(playbackId)
            val state = player.playbackState
            if (
                state != Player.STATE_IDLE && (!player.playWhenReady || state == Player.STATE_ENDED)
            ) {
                save(resumePosition(player))
            }
        }
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    follow()
                }
            }
        follow()
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}

/**
 * When the screen stops, unless the activity is only changing configurations, hands [onSave] the
 * position to keep in the saved state, and [onPause] this screen's playback ID if it hasn't
 * [played] yet, as [pausesOnLeave] decides. A video that has played keeps playing in the
 * background. The connection pauses a start that still waits for the player too. An idle player
 * failed or never loaded, so its position may not be where the video was: the position its playback
 * started from stays saved instead.
 */
@Composable
private fun SaveAndPauseOnStop(
    player: Player?,
    playbackId: String?,
    played: Boolean,
    onSave: (Long) -> Unit,
    onPause: (String) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val save by rememberUpdatedState(onSave)
    val pause by rememberUpdatedState(onPause)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val changing = activity?.isChangingConfigurations == true
        val current = player?.takeIf { it.currentMediaItem?.mediaId == playbackId }
        if (!changing && current != null && current.playbackState != Player.STATE_IDLE) {
            save(resumePosition(current))
        }
        if (playbackId != null && pausesOnLeave(changing, played)) pause(playbackId)
    }
}

/**
 * What the video area shows: the [failure] if there is one, the play button at the saved position
 * while [waiting] after the session was gone, or else the [player].
 */
private fun videoAreaContent(
    failure: PlaybackFailure?,
    waiting: Boolean,
    savedPositionMs: Long,
    player: Player?,
): VideoAreaContent =
    when {
        failure != null -> VideoAreaContent.Failed(failure)
        waiting -> VideoAreaContent.Waiting(savedPositionMs.takeIf { it != C.TIME_UNSET })
        else -> VideoAreaContent.Playing(player)
    }

/** Where playback goes on from: the position, or the live edge for a live stream. */
private fun resumePosition(player: Player): Long =
    if (player.isCurrentMediaItemLive) C.TIME_UNSET else player.currentPosition

/** The activity that [this] context belongs to, if any. */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * The ViewModel for [videoId], from the current ViewModelStoreOwner's default factory, which is
 * Hilt's. Hilt's creation callback hands the ID to the ViewModel's assisted factory.
 */
@Composable
private fun playerViewModel(videoId: VideoId): PlayerViewModel {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    val defaults =
        (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
            ?: CreationExtras.Empty
    return viewModel(
        viewModelStoreOwner = owner,
        extras =
            defaults.withCreationCallback<PlayerViewModel.Factory> { factory ->
                factory.create(PlayerArgs(videoId))
            },
    )
}
