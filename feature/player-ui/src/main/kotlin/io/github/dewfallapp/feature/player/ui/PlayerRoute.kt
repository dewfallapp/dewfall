package io.github.dewfallapp.feature.player.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerIntent
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.StartAction
import io.github.dewfallapp.feature.player.logic.startAction
import io.github.dewfallapp.feature.player.logic.videoAreaFailure
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The video screen for [videoId], with its ViewModel and its playback. [onMinimize] runs when the
 * user taps the minimize button, and [onOpenChannel] when the user taps the channel. The app's
 * navigation gives each screen its own ViewModelStoreOwner, so each video gets its own ViewModel.
 * See openspec/specs/player/spec.md.
 *
 * Playback starts the first time the screen opens, unless the player already plays this video,
 * which goes on as it is. Leaving the app keeps it playing in the background, unless it hasn't
 * played yet, as the connection decides, and minimizing leaves it to the mini player. When the
 * screen comes back after the system ended the process or stopped the player, it waits at the saved
 * position for the user to press play. The fullscreen button shows the fullscreen player, and the
 * back gesture leaves it.
 */
@Composable
fun PlayerRoute(
    videoId: VideoId,
    onMinimize: () -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerContent(
        onMinimize = onMinimize,
        onOpenChannel = onOpenChannel,
        viewModel = playerViewModel(videoId),
        modifier = modifier,
    )
}

/** Connects the screen to its ViewModel and to the player. */
@Composable
internal fun PlayerContent(
    onMinimize: () -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
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
    // Read again when the player connects or goes, because the session's end clears the IDs.
    val known = remember(player, playbackId) { playbackId?.let(playback::videoOf) != null }

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

    StartOnOpen(
        started = started,
        known = known,
        playbackId = playbackId,
        playbackOfThisVideo = viewModel::playbackOfThisVideo,
        onPlay = { start(C.TIME_UNSET) },
        onGoOn = { id -> recordStart(id, C.TIME_UNSET) },
        onFollow = viewModel::follow,
    )
    FollowPlayback(player, playbackId, onSave = { savedPositionMs = it })
    SaveOnStop(player, playbackId, onSave = { savedPositionMs = it })
    // Called every time, with enabled, as BackHandler's reference asks, so the order of the back
    // handlers stays the same. The entry composes after NavDisplay's handler, so this one runs.
    BackHandler(enabled = state.fullscreen) { viewModel.dispatch(PlayerIntent.ExitFullscreen) }
    // Try again, or play after the session was gone, goes on from the last position.
    val retryPositionMs = {
        player?.takeIf { it.currentMediaItem?.mediaId == playbackId }?.let(::resumePosition)
            ?: savedPositionMs
    }
    val waiting = started && !known
    if (state.fullscreen) {
        FullscreenPlayer(
            // Fullscreen has no details' message to share a failure with, so it shows the player's.
            content = videoAreaContent(failure, waiting, savedPositionMs, player),
            page = (state.details as? DetailsArea.Shown)?.page,
            onPlay = { start(retryPositionMs()) },
            onTryAgain = { start(retryPositionMs()) },
            onExit = { viewModel.dispatch(PlayerIntent.ExitFullscreen) },
            modifier = modifier,
        )
    } else {
        VideoScreen(
            state = state,
            content =
                videoAreaContent(
                    videoAreaFailure(state.details, failure),
                    waiting,
                    savedPositionMs,
                    player,
                ),
            onTryAgainDetails = {
                val positionMs = retryPositionMs()
                viewModel.tryAgainDetails(failure, positionMs)?.let { id ->
                    recordStart(id, positionMs)
                }
            },
            onPlay = { start(retryPositionMs()) },
            onIntent = viewModel::dispatch,
            onMinimize = onMinimize,
            onOpenChannel = onOpenChannel,
            modifier = modifier,
        )
    }
}

/**
 * Starts playback the first time the screen opens, or goes on with the playback of this screen's
 * video that [playbackOfThisVideo] finds, as [startAction] decides from whether playback [started],
 * whether its [playbackId] is still [known], and that playback. [onPlay] starts it, and [onGoOn]
 * keeps the playback it goes on with. [onFollow] tells the ViewModel which playback is its own: the
 * one it goes on with, or its own after it was made again, as after "Don't keep activities".
 */
@Composable
private fun StartOnOpen(
    started: Boolean,
    known: Boolean,
    playbackId: String?,
    playbackOfThisVideo: () -> String?,
    onPlay: () -> Unit,
    onGoOn: (String) -> Unit,
    onFollow: (String) -> Unit,
) {
    val find by rememberUpdatedState(playbackOfThisVideo)
    val play by rememberUpdatedState(onPlay)
    val goOn by rememberUpdatedState(onGoOn)
    val follow by rememberUpdatedState(onFollow)
    LaunchedEffect(Unit) {
        val playing = find()
        when (startAction(started, known, playsThisVideo = playing != null)) {
            StartAction.PLAY -> play()
            StartAction.GO_ON -> {
                if (playing != null) {
                    goOn(playing)
                    follow(playing)
                }
            }
            StartAction.NOTHING -> playbackId?.let(follow)
            StartAction.WAIT_FOR_PLAY -> Unit
        }
    }
}

/**
 * The video screen with its video area showing [content]. [onTryAgainDetails] runs for the details'
 * "Try again", [onPlay] for play while it waits and the playback's "Try again", and the fullscreen
 * button and the other intents go to [onIntent].
 */
@Composable
private fun VideoScreen(
    state: PlayerState,
    content: VideoAreaContent,
    onTryAgainDetails: () -> Unit,
    onPlay: () -> Unit,
    onIntent: (PlayerIntent) -> Unit,
    onMinimize: () -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The clock is read once per answer, so the screen itself stays a function of what it gets.
    val now = remember(state.details) { Clock.System.now() }
    val zone = remember(state.details) { ZoneId.systemDefault() }
    PlayerScreen(
        state = state,
        now = now,
        zone = zone,
        onIntent = { intent ->
            if (intent == PlayerIntent.TryAgain) onTryAgainDetails() else onIntent(intent)
        },
        onMinimize = onMinimize,
        onOpenChannel = onOpenChannel,
        modifier = modifier,
        videoArea = { areaModifier ->
            VideoArea(
                content = content,
                onPlay = onPlay,
                onTryAgain = onPlay,
                onFullscreen = { onIntent(PlayerIntent.EnterFullscreen) },
                modifier = areaModifier,
            )
        },
    )
}

/**
 * While the screen exists, even with the app in the background, hands [onSave] the position
 * whenever this screen's item pauses or ends. So when Android stops a player that paused in the
 * background, the screen waits at the position where it paused. Like ON_STOP, it never saves from
 * an idle player, whose position after a failure may not be where the video was.
 */
@Composable
private fun FollowPlayback(player: Player?, playbackId: String?, onSave: (Long) -> Unit) {
    val save by rememberUpdatedState(onSave)
    DisposableEffect(player, playbackId) {
        if (player == null || playbackId == null) return@DisposableEffect onDispose {}
        fun follow() {
            if (player.currentMediaItem?.mediaId != playbackId) return
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
 * position to keep in the saved state. An idle player failed or never loaded, so its position may
 * not be where the video was: the position its playback started from stays saved instead. Whether a
 * playback that hasn't played pauses is the connection's rule, which MainActivity's onStop runs.
 */
@Composable
private fun SaveOnStop(player: Player?, playbackId: String?, onSave: (Long) -> Unit) {
    val activity = LocalActivity.current
    val save by rememberUpdatedState(onSave)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val changing = activity?.isChangingConfigurations == true
        val current = player?.takeIf { it.currentMediaItem?.mediaId == playbackId }
        if (!changing && current != null && current.playbackState != Player.STATE_IDLE) {
            save(resumePosition(current))
        }
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
