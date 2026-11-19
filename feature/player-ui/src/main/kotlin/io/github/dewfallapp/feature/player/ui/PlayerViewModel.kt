package io.github.dewfallapp.feature.player.ui

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.video.VideoDetailsRepository
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerIntent
import io.github.dewfallapp.feature.player.logic.PlayerReducer
import io.github.dewfallapp.feature.player.logic.PlayerState
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
 * It takes no dispatcher, because the repository's call is main-safe.
 */
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
internal class PlayerViewModel
@AssistedInject
constructor(
    @Assisted private val args: PlayerArgs,
    private val repository: VideoDetailsRepository,
) : MviViewModel<PlayerState, PlayerIntent, Nothing>(PlayerState(), PlayerReducer) {

    /** The running request. Only the main thread touches it. */
    private var detailsJob: Job? = null

    /** The number of the latest request that a call started for. */
    private var startedNumber = 0L

    init {
        dispatch(PlayerIntent.Load)
    }

    override fun runSideEffects(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.Load,
            PlayerIntent.TryAgain -> startNewRequest()
            is PlayerIntent.DetailsFinished -> Unit
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

    /** Makes the ViewModel for the video in [PlayerArgs]. Hilt implements it. */
    @AssistedFactory
    interface Factory {
        fun create(args: PlayerArgs): PlayerViewModel
    }
}
