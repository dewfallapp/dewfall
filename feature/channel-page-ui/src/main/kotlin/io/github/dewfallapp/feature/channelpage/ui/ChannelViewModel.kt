package io.github.dewfallapp.feature.channelpage.ui

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.channel.ChannelUploadsRepository
import io.github.dewfallapp.core.data.subscriptions.SubscribeWithUploads
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.channelpage.logic.ChannelIntent
import io.github.dewfallapp.feature.channelpage.logic.ChannelReducer
import io.github.dewfallapp.feature.channelpage.logic.ChannelState
import io.github.dewfallapp.feature.channelpage.logic.LoadArea
import io.github.dewfallapp.feature.channelpage.logic.Subscription
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * What the channel page's ViewModel needs from the screen that opens it. It is an ordinary class,
 * not the ChannelId itself, because ChannelId is a value class, and Dagger can't implement a
 * factory function that takes one, as PlayerArgs explains.
 */
internal class ChannelArgs(val channelId: ChannelId)

/**
 * Loads the channel page, one load at a time, and keeps the subscription. Load 1 starts when it is
 * created, and a load starts only when the reducer made a new number, so "Try again" with nothing
 * to try sends nothing. Leaving the page cancels the running load.
 *
 * It reads the database's answer for each of the page's IDs, the opened one and, after a redirect,
 * the one YouTube answered for. Subscribe keeps the channel the page shows, and Unsubscribe removes
 * the subscription under every ID the user is subscribed under. The repository finishes a write
 * even when the user leaves the page right after the tap.
 *
 * While the user subscribes to the channel, it saves the uploads the page shows, so they reach the
 * inbox with no request of their own: through SubscribeWithUploads when Subscribe is tapped, and
 * once for each load that finishes while the channel is subscribed. A page whose card doesn't say
 * Subscribed saves nothing. See the subscriptions spec's "The channel page saves the uploads of a
 * subscribed channel".
 *
 * It takes no dispatcher, because the repositories' calls are main-safe.
 */
@HiltViewModel(assistedFactory = ChannelViewModel.Factory::class)
internal class ChannelViewModel
@AssistedInject
constructor(
    @Assisted args: ChannelArgs,
    private val uploads: ChannelUploadsRepository,
    private val subscriptions: SubscriptionsRepository,
    private val storedVideos: StoredVideosRepository,
    private val subscribeWithUploads: SubscribeWithUploads,
) :
    MviViewModel<ChannelState, ChannelIntent, Nothing>(
        ChannelState(openedId = args.channelId),
        ChannelReducer,
    ) {

    /**
     * Swallows a save or a subscribe with uploads that throws, such as on a full disk: the card
     * shows what the database holds, which didn't change, and a save runs on every load of a
     * subscribed channel's page, so it mustn't crash the app.
     */
    private val writeFailed = CoroutineExceptionHandler { _, _ ->
        // Nothing to undo.
    }

    /** The running load. Only the main thread touches it. */
    private var loadJob: Job? = null

    /** The number of the latest load that a call started for. */
    private var startedNumber = 0L

    /**
     * The uploads that the page shows, with the number of their load. The state keeps only rows,
     * and a save needs the channel and its videos as YouTube gave them. Only the main thread
     * touches it.
     */
    private var shownUploads: Pair<Long, ChannelUploads>? = null

    /** The number of the latest load whose uploads a save started for. */
    private var savedNumber = 0L

    init {
        startNewLoad()
        readSubscriptions()
    }

    override fun runSideEffects(intent: ChannelIntent) {
        when (intent) {
            ChannelIntent.TryAgain -> startNewLoad()
            ChannelIntent.Subscribe -> subscribe()
            ChannelIntent.UnsubscribeConfirmed -> unsubscribe()
            is ChannelIntent.LoadFinished -> {
                keepShownUploads(intent)
                saveIfSubscribed()
            }
            is ChannelIntent.SubscriptionRead -> saveIfSubscribed()
            ChannelIntent.UnsubscribeTapped,
            ChannelIntent.UnsubscribeCancelled,
            ChannelIntent.DescriptionToggled -> Unit
        }
    }

    /**
     * Reads the subscription under each of the page's IDs, and reads again for the new set when a
     * redirect adds an ID.
     */
    // flatMapLatest is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun readSubscriptions() {
        viewModelScope.launch {
            state
                .map { it.pageIds }
                .distinctUntilChanged()
                .flatMapLatest { ids ->
                    merge(
                        *ids.map { id ->
                                subscriptions.subscribedChannel(id).map { subscribed ->
                                    ChannelIntent.SubscriptionRead(id, subscribed?.channel)
                                }
                            }
                            .toTypedArray(),
                    )
                }
                .collect(::dispatch)
        }
    }

    /**
     * Keeps the channel the page shows, if the card offers "Subscribe", with the uploads the page
     * shows. SubscribeWithUploads saves them after the subscription, as one unit that leaving the
     * page doesn't stop.
     */
    private fun subscribe() {
        val current = state.value
        val channel = current.shownChannel
        val offered = current.showsCard && current.subscription == Subscription.NotSubscribed
        if (!offered || channel == null) return
        val shown = shownUploads
        if (shown != null) savedNumber = shown.first
        viewModelScope.launch(writeFailed) {
            subscribeWithUploads.subscribe(channel, shown?.second)
        }
    }

    /** Keeps the uploads of [finished], if the reducer showed them. */
    private fun keepShownUploads(finished: ChannelIntent.LoadFinished) {
        val result = finished.result
        val shows = (state.value.load as? LoadArea.Loaded)?.number == finished.number
        if (result is YouTubeResult.Success && shows) shownUploads = finished.number to result.value
    }

    /** Saves the shown uploads once for their load, while the channel is subscribed. */
    private fun saveIfSubscribed() {
        val (number, shown) = shownUploads ?: return
        if (number == savedNumber || state.value.subscription !is Subscription.Subscribed) return
        savedNumber = number
        viewModelScope.launch(writeFailed) { storedVideos.saveUploads(shown) }
    }

    /**
     * Removes the subscription under every ID the user is subscribed under, each in its own
     * coroutine, so leaving the page during one write doesn't stop the next one from starting.
     */
    private fun unsubscribe() {
        for (id in state.value.subscribedIds) {
            viewModelScope.launch { subscriptions.unsubscribe(id) }
        }
    }

    /** Starts a call for the state's load, if the reducer just made a new one. */
    private fun startNewLoad() {
        val number = (state.value.load as? LoadArea.Loading)?.number
        if (number == null || number == startedNumber) return
        startedNumber = number
        loadJob?.cancel()
        val channelId = state.value.openedId
        loadJob = viewModelScope.launch {
            val result = uploads.latestUploads(channelId)
            dispatch(ChannelIntent.LoadFinished(number, result))
        }
    }

    /** Makes the ViewModel for the channel in [ChannelArgs]. Hilt implements it. */
    @AssistedFactory
    interface Factory {
        fun create(args: ChannelArgs): ChannelViewModel
    }
}
