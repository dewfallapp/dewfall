package io.github.dewfallapp.feature.player.ui

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.video.VideoDetailsRepository
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerIntent
import io.github.dewfallapp.feature.player.logic.PlayerReducer
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.sharesFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the video screen's ViewModel needs from the screen that opens it. It is an ordinary class,
 * not the VideoId itself, because VideoId is a value class, and Dagger can't implement a factory
 * function that takes one. See docs/architecture.md#navigation.
 */
internal class PlayerArgs(val videoId: VideoId)

/**
 * Loads the video screen's details, one request at a time. It sends Load once when it is created,
 * and a request starts only when the reducer made a new one, so "Try again" with nothing to try
 * sends nothing. Leaving the screen cancels the running request.
 *
 * It takes no dispatcher, because the repository's call is main-safe. It also holds the screen's
 * PlaybackConnection and the playback ID of its latest start. It gives that playback the video's
 * title and channel name for the media notification once the details show, and stops it when it is
 * cleared. Player commands skip intents, as ADR 0002's note says.
 */
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
internal class PlayerViewModel
@AssistedInject
constructor(
    @Assisted private val args: PlayerArgs,
    private val repository: VideoDetailsRepository,
    /** The player, which the route reads and sends play, pause and seek to, never through state. */
    internal val playback: PlaybackConnection,
) : MviViewModel<PlayerState, PlayerIntent, Nothing>(PlayerState(), PlayerReducer) {

    /** The running request. Only the main thread touches it. */
    private var detailsJob: Job? = null

    /** The number of the latest request that a call started for. */
    private var startedNumber = 0L

    /** The playback ID of this screen's latest start. Only the main thread touches it. */
    private var playbackId: String? = null

    init {
        dispatch(PlayerIntent.Load)
    }

    override fun runSideEffects(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.Load,
            PlayerIntent.TryAgain -> startNewRequest()
            is PlayerIntent.DetailsFinished -> describePlayback()
        }
    }

    /** Starts a call for the state's request, if the reducer just made a new one. */
    private fun startNewRequest() {
        val number = (state.value.details as? DetailsArea.Loading)?.number
        if (number == null || number == startedNumber) return
        startedNumber = number
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            val result = repository.videoDetails(args.videoId)
            dispatch(PlayerIntent.DetailsFinished(number, result))
        }
    }

    /**
     * Starts playing the video from [positionMs], or from the start or live edge for C.TIME_UNSET,
     * under a new playback ID. It returns the ID for the route's saved state, and keeps it, so
     * leaving the screen stops that playback.
     */
    internal fun startPlayback(positionMs: Long): String {
        val id = playback.prepare(args.videoId, play = true, startPositionMs = positionMs)
        playbackId = id
        describePlayback()
        return id
    }

    /**
     * Gives the latest playback the title and channel name that the media notification shows, once
     * the details show. The reducer has already run, so the state holds this intent's answer.
     */
    private fun describePlayback() {
        val page = (state.value.details as? DetailsArea.Shown)?.page ?: return
        val id = playbackId ?: return
        playback.describe(id, page.title, page.channelName)
    }

    /**
     * "Try again" in the details' place. When playback failed the same way, the video area shows no
     * second message, so this retries playback too, from [positionMs], and returns its new playback
     * ID. Otherwise it returns null.
     */
    internal fun tryAgainDetails(playbackFailure: PlaybackFailure?, positionMs: Long): String? {
        val shared = sharesFailure(state.value.details, playbackFailure)
        dispatch(PlayerIntent.TryAgain)
        return if (shared) startPlayback(positionMs) else null
    }

    /**
     * Stops this screen's playback, unless another video started meanwhile. Navigation 3 clears the
     * ViewModel only after the screen's exit animation.
     */
    override fun onCleared() {
        playbackId?.let(playback::stop)
    }

    /** Makes the ViewModel for the video in [PlayerArgs]. Hilt implements it. */
    @AssistedFactory
    interface Factory {
        fun create(args: PlayerArgs): PlayerViewModel
    }
}
