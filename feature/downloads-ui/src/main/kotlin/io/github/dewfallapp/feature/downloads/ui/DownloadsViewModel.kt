package io.github.dewfallapp.feature.downloads.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.download.DownloadsRepository
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.downloads.logic.DownloadsEffect
import io.github.dewfallapp.feature.downloads.logic.DownloadsIntent
import io.github.dewfallapp.feature.downloads.logic.DownloadsList
import io.github.dewfallapp.feature.downloads.logic.DownloadsReducer
import io.github.dewfallapp.feature.downloads.logic.DownloadsState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
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
 * Reads every download from the phone, and dispatches each answer as
 * [DownloadsIntent.DownloadsRead]. It reads only while the screen collects its state, and for
 * [STOP_TIMEOUT_MS] after, so the query doesn't run for every progress write while another screen
 * covers a hidden Downloads screen. A read that fails shows that the downloads can't be shown, and
 * the next time the screen shows, it reads again.
 *
 * It pauses, resumes, cancels and removes downloads through the repository. A write that throws
 * leaves the row as it was, so it shows what the phone holds, and a finished download's removal
 * says "Download removed" only once the remove returned.
 *
 * It takes no dispatcher, because the repository is main-safe. The downloads are user data, so it
 * never logs them, and it has no Logger, as InboxViewModel has none.
 */
@HiltViewModel
internal class DownloadsViewModel @Inject constructor(private val downloads: DownloadsRepository) :
    MviViewModel<DownloadsState, DownloadsIntent, DownloadsEffect>(
        DownloadsState(),
        DownloadsReducer,
    ) {

    /** A write that throws leaves the row as it was: its status shows what the phone holds. */
    private val writeFailed = CoroutineExceptionHandler { _, _ -> }

    init {
        readWhileShown()
    }

    override fun runSideEffects(intent: DownloadsIntent) {
        when (intent) {
            is DownloadsIntent.Pause -> {
                viewModelScope.launch(writeFailed) { downloads.pause(intent.videoId) }
            }
            is DownloadsIntent.Resume -> {
                viewModelScope.launch(writeFailed) { downloads.download(intent.video) }
            }
            is DownloadsIntent.Remove -> {
                viewModelScope.launch(writeFailed) {
                    downloads.remove(intent.videoId)
                    if (intent.finished) dispatch(DownloadsIntent.Removed)
                }
            }
            else -> Unit
        }
    }

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
                    .filter { shown -> shown && state.value.list == DownloadsList.Unreadable }
            merge(command, returnAfterFailure)
                .flatMapLatest { read -> if (read) downloadsRead() else emptyFlow() }
                .collect(::dispatch)
        }
    }

    private fun downloadsRead(): Flow<DownloadsIntent> =
        downloads
            .downloads()
            .map<_, DownloadsIntent> { DownloadsIntent.DownloadsRead(it) }
            // A read that throws, as from a damaged database, must not crash the app, so the
            // screen says it can't show the downloads.
            .catch { emit(DownloadsIntent.ReadFailed) }
}
