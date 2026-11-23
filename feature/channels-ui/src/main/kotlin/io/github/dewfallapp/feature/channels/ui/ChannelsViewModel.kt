package io.github.dewfallapp.feature.channels.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.channels.logic.ChannelsIntent
import io.github.dewfallapp.feature.channels.logic.ChannelsReducer
import io.github.dewfallapp.feature.channels.logic.ChannelsState
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
 * hidden Channels screen. The state keeps the last answer for the way back.
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

    // flatMapLatest is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun readWhileShown() {
        viewModelScope.launch {
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)
                .command(stateSubscriptionCount)
                .distinctUntilChanged()
                .flatMapLatest { command ->
                    if (command == SharingCommand.START) {
                        subscriptions.channelOverviews().map { ChannelsIntent.ChannelsRead(it) }
                    } else {
                        emptyFlow()
                    }
                }
                .collect(::dispatch)
        }
    }
}
