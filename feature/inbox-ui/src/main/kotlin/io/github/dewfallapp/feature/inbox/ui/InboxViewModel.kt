package io.github.dewfallapp.feature.inbox.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.data.subscriptions.UploadsChecker
import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.inbox.logic.InboxContent
import io.github.dewfallapp.feature.inbox.logic.InboxIntent
import io.github.dewfallapp.feature.inbox.logic.InboxReducer
import io.github.dewfallapp.feature.inbox.logic.InboxState
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * How long the inbox goes on reading after the screen stops collecting its state, so a
 * configuration change doesn't start the reads again, as Android's state production guide does.
 */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Reads the inbox from the phone: the number of subscribed channels and the inbox's videos, and
 * dispatches each pair as [InboxIntent.Loaded]. It also reads the app's check for new videos, as
 * [InboxIntent.CheckChanged], and starts a check for [InboxIntent.CheckForNewVideos]. The check
 * runs in the app, not here, so it goes on when the inbox doesn't show. It reads only while the
 * screen collects its state, and for [STOP_TIMEOUT_MS] after, so the query doesn't run again for
 * every change while another screen covers the inbox. The state keeps the last answer for the way
 * back.
 *
 * A read that throws, such as on a damaged database, shows Unreadable instead of crashing the app,
 * which opens on the inbox. The reads start again the next time the inbox shows after they stopped,
 * and also when it shows again within [STOP_TIMEOUT_MS] while it is Unreadable, because the failed
 * read has ended by then. It never collects its own state, which would keep its collector count
 * above 0.
 *
 * It takes no dispatcher, because the repositories' Flows are main-safe. It has no Logger, as
 * PlayerViewModel has none, and logs nothing.
 */
@HiltViewModel
internal class InboxViewModel
@Inject
constructor(
    private val subscriptions: SubscriptionsRepository,
    private val storedVideos: StoredVideosRepository,
    private val checker: UploadsChecker,
) : MviViewModel<InboxState, InboxIntent, Nothing>(InboxState(), InboxReducer) {

    init {
        readWhileShown()
    }

    override fun runSideEffects(intent: InboxIntent) {
        if (intent == InboxIntent.CheckForNewVideos) checker.start()
    }

    /**
     * Reads while the screen shows, and for [STOP_TIMEOUT_MS] after. A read that failed has ended,
     * and a collector that comes back within [STOP_TIMEOUT_MS] doesn't change the sharing command,
     * so each return of a collector while the inbox is Unreadable starts new reads too. A read that
     * works keeps its grace period.
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
                    .filter { shown -> shown && state.value.content == InboxContent.Unreadable }
            merge(command, returnAfterFailure)
                .flatMapLatest { read -> if (read) merge(inbox(), check()) else emptyFlow() }
                .collect(::dispatch)
        }
    }

    private fun inbox(): Flow<InboxIntent> =
        combine<Int, List<VideoSummary>, InboxIntent>(
                subscriptions.subscribedChannels().map { it.size }.distinctUntilChanged(),
                storedVideos.inboxVideos(),
            ) { count, videos ->
                InboxIntent.Loaded(count, videos)
            }
            // The inbox is the first screen, so a read that throws must not crash the app.
            .catch { emit(InboxIntent.ReadFailed) }

    /** The check's state, which is a StateFlow, so it never throws and starts with its value. */
    private fun check(): Flow<InboxIntent> = checker.state.map(InboxIntent::CheckChanged)
}
