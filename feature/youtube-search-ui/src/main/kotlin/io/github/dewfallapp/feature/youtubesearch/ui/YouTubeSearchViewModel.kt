package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.search.YouTubeSearchRepository
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.youtubesearch.logic.ResultsArea
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchReducer
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs the Search YouTube screen's searches, one at a time. A search starts only when the reducer
 * made a new request, so a blank search, or "Try again" with nothing to try, sends nothing and
 * leaves a running search alone. Clearing, a new search and leaving the screen cancel the running
 * one.
 *
 * It also reads which channels the user subscribes to, for its whole life, so each channel row's
 * button follows the database whichever screen changed it. Subscribe keeps a row's channel, and
 * Unsubscribe removes the confirmed one. The repository finishes a write even when the user leaves
 * the screen right after the tap.
 *
 * It takes no dispatcher, because the repositories' calls are main-safe.
 */
@HiltViewModel
internal class YouTubeSearchViewModel
@Inject
constructor(
    private val repository: YouTubeSearchRepository,
    private val subscriptions: SubscriptionsRepository,
) :
    MviViewModel<YouTubeSearchState, YouTubeSearchIntent, Nothing>(
        YouTubeSearchState(),
        YouTubeSearchReducer,
    ) {

    /** The running search. Only the main thread touches it. */
    private var searchJob: Job? = null

    /** The number of the latest request that a search started for. */
    private var startedNumber = 0L

    init {
        viewModelScope.launch {
            subscriptions
                .subscribedChannels()
                .map { channels -> channels.mapTo(mutableSetOf()) { it.channel.id } }
                .distinctUntilChanged()
                .collect { ids -> dispatch(YouTubeSearchIntent.SubscriptionsRead(ids)) }
        }
    }

    override fun runSideEffects(intent: YouTubeSearchIntent) {
        when (intent) {
            is YouTubeSearchIntent.Submit,
            YouTubeSearchIntent.TryAgain -> startNewRequest()
            YouTubeSearchIntent.Clear -> stopSearch()
            is YouTubeSearchIntent.Subscribe -> subscribe(intent.channelId)
            is YouTubeSearchIntent.UnsubscribeConfirmed -> unsubscribe(intent.channelId)
            is YouTubeSearchIntent.SearchFinished,
            is YouTubeSearchIntent.SubscriptionsRead,
            is YouTubeSearchIntent.UnsubscribeTapped,
            YouTubeSearchIntent.UnsubscribeCancelled -> Unit
        }
    }

    /**
     * Keeps the channel of the row with [channelId], if its button offers "Subscribe": the results
     * hold it, and the database has answered without it. It keeps no description, because the row
     * holds only YouTube's excerpt of it. A later save of the channel's uploads brings the whole
     * one.
     */
    private fun subscribe(channelId: ChannelId) {
        val current = state.value
        val row =
            (current.results as? ResultsArea.Results)?.channels?.firstOrNull { it.id == channelId }
        val subscribed = current.subscribedIds ?: return
        if (row == null || channelId in subscribed) return
        val channel = Channel(id = row.id, name = row.name, avatars = row.avatars, description = "")
        viewModelScope.launch { subscriptions.subscribe(channel) }
    }

    /** Removes the subscription the user confirmed in the dialog. */
    private fun unsubscribe(channelId: ChannelId) {
        viewModelScope.launch { subscriptions.unsubscribe(channelId) }
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
