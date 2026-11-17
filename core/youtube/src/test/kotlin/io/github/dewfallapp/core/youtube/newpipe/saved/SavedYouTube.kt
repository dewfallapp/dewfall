package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.youtube.newpipe.NewPipeCalls
import io.github.dewfallapp.core.youtube.newpipe.NewPipeDownloader
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The YouTube layer, answering from the saved set [set] instead of YouTube, for one call in a test.
 * It resets NewPipeExtractor's static state first and builds a NewPipeCalls of its own, whose first
 * call sets NewPipeExtractor up with the replaying client, so the call sends exactly the requests
 * that the recorder saved. The layer logs through [logger] and runs on [dispatcher], as it would in
 * the app.
 */
internal class SavedYouTube(set: SavedSet, logger: Logger, dispatcher: CoroutineDispatcher) {
    /** The saved set's files, as the replay reads them. */
    val saved: SavedSetContent = loadSavedSet(set)

    private val replay: ReplayingClient
    private val layer: YouTubeLayer

    init {
        resetNewPipeExtractorState()
        replay = ReplayingClient(set, saved)
        val calls =
            NewPipeCalls(NewPipeDownloader(replay.client), logger, dispatcher) { SAVED_SET_LOCALE }
        layer = YouTubeLayer(calls, logger)
    }

    /** The requests that the call sent, in order. */
    val sentRequests: List<Endpoint>
        get() = replay.sentRequests

    /**
     * Runs [call] on the layer. If the call sent a request that the saved set has no answer for,
     * the test fails with a report of that request, before it checks a result that would only say
     * NETWORK.
     */
    suspend fun <T> call(call: suspend YouTubeLayer.() -> T): T {
        val result = layer.call()
        replay.assertNoMisses()
        return result
    }
}
