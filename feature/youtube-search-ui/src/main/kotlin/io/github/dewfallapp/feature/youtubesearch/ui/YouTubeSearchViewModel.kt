package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.search.YouTubeSearchRepository
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.youtubesearch.logic.ResultsArea
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchReducer
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs the Search YouTube screen's searches, one at a time. A search starts only when the reducer
 * made a new request, so a blank search, or "Try again" with nothing to try, sends nothing and
 * leaves a running search alone. Clearing, a new search and leaving the screen cancel the running
 * one.
 *
 * It takes no dispatcher, because the repository's call is main-safe.
 */
@HiltViewModel
internal class YouTubeSearchViewModel
@Inject
constructor(private val repository: YouTubeSearchRepository) :
    MviViewModel<YouTubeSearchState, YouTubeSearchIntent, Nothing>(
        YouTubeSearchState(),
        YouTubeSearchReducer,
    ) {

    /** The running search. Only the main thread touches it. */
    private var searchJob: Job? = null

    /** The number of the latest request that a search started for. */
    private var startedNumber = 0L

    override fun runSideEffects(intent: YouTubeSearchIntent) {
        when (intent) {
            is YouTubeSearchIntent.Submit,
            YouTubeSearchIntent.TryAgain -> startNewRequest()
            YouTubeSearchIntent.Clear -> stopSearch()
            is YouTubeSearchIntent.SearchFinished -> Unit
        }
    }

    private fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
    }

    /** Starts a search for the state's request, if the reducer just made a new one. */
    private fun startNewRequest() {
        val request = (state.value.results as? ResultsArea.Loading)?.request
        if (request == null || request.number == startedNumber) return
        startedNumber = request.number
        stopSearch()
        searchJob = viewModelScope.launch {
            val result = repository.search(request.text)
            dispatch(YouTubeSearchIntent.SearchFinished(request, result))
        }
    }
}
