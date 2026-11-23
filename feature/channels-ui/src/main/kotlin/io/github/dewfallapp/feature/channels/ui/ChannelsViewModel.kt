package io.github.dewfallapp.feature.channels.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.channels.logic.ChannelsIntent
import io.github.dewfallapp.feature.channels.logic.ChannelsList
import io.github.dewfallapp.feature.channels.logic.ChannelsReducer
import io.github.dewfallapp.feature.channels.logic.ChannelsState
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * How long the screen goes on reading after it stops collecting its state, so a configuration
 * change doesn't start the read again, as Android's state production guide does.
 */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Reads the subscribed channels with their last uploads from the phone, and dispatches each answer
 * as [ChannelsIntent.ChannelsRead]. It reads only while the screen collects its state, and for
 * [STOP_TIMEOUT_MS] after, so the query doesn't run again for every save while the inbox covers a
 * hidden Channels screen. The state keeps the last answer for the way back. A read that fails shows
 * that the channels can't be shown, and the next time the screen shows, it reads again.
 *
 * It takes no dispatcher, because the repository's Flow is main-safe. The channels are user data,
 * so it never logs them.
 */
@HiltViewModel
internal class ChannelsViewModel
@Inject
constructor(private val subscriptions: SubscriptionsRepository) :
    MviViewModel<ChannelsState, ChannelsIntent, Nothing>(ChannelsState(), ChannelsReducer) {

    init {
        readWhileShown()
    }

    override fun runSideEffects(intent: ChannelsIntent) = Unit

    /**
     * Reads while the screen shows, and for [STOP_TIMEOUT_MS] after. A read that failed has ended,
     * and a collector that comes back within [STOP_TIMEOUT_MS] doesn't change the sharing command,
     * so each return of a collector while the list is Unreadable starts a new read too.
     */
    // flatMapLatest is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun readWhileShown() {
        viewModelScope.launch {
            val command =
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)
                    .command(stateSubscriptionCount)
                    .distinctUntilChanged()
                    .map { it == SharingCommand.START }
            val returnAfterFailure =
                stateSubscriptionCount
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .filter { shown -> shown && state.value.list == ChannelsList.Unreadable }
            merge(command, returnAfterFailure)
                .flatMapLatest { read -> if (read) channels() else emptyFlow() }
                .collect(::dispatch)
        }
    }

    private fun channels(): Flow<ChannelsIntent> =
        subscriptions
            .channelOverviews()
            .map<_, ChannelsIntent> { ChannelsIntent.ChannelsRead(it) }
            // A read that throws, as from a damaged database, must not crash the app, so the
            // screen says it can't show the channels.
            .catch { emit(ChannelsIntent.ReadFailed) }
}
