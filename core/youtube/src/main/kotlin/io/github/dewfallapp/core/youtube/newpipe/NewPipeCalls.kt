package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader

/** The area of the YouTube layer's log tag, Dewfall.YouTube. */
internal const val LOG_AREA = "YouTube"

/** The message of the line that a failed call logs. Its fields say which call and why. */
private const val CALL_FAILED = "YouTube call failed"

/**
 * Held while NewPipeExtractor runs. NewPipeExtractor keeps YouTube's client version, and the player
 * code that decodes stream links, in static fields that it reads and writes without locks. Every
 * call touches the client version, so every call holds this lock. The fields belong to the process,
 * so every NewPipeCalls shares the lock.
 */
private val newPipeExtractorLock = Mutex()

/**
 * Runs the YouTube layer's calls into NewPipeExtractor, so that each one keeps the layer's rules.
 * Create one per process. NewPipeExtractor's setup is global, so a second one would set it up again
 * with its own downloader. See docs/architecture.md#how-a-call-runs.
 * - The first call sets NewPipeExtractor up with [downloader] and British English.
 * - Each call runs its blocking work on [dispatcher], so it is safe to call from the main thread.
 * - One call runs at a time, across every NewPipeCalls. Every call reads or writes
 *   NewPipeExtractor's static caches, which have no locks. A download that only sends its own
 *   requests through the downloader, [runDownload], touches none of them, so it doesn't wait.
 * - A failure comes back as [YouTubeResult.Failure], and is logged once, with the call and the
 *   category. Cancelling the calling coroutine cancels the request in progress, and the call then
 *   throws CancellationException, the only exception that it throws.
 *
 * [deviceLocale] gives the device's locale at each call, so a change of region applies to the next
 * call.
 */
internal class NewPipeCalls(
    private val downloader: Downloader,
    logger: Logger,
    private val dispatcher: CoroutineDispatcher,
    private val deviceLocale: () -> Locale = Locale::getDefault,
) {
    private val log = logger.withTag(LOG_AREA)

    // NewPipe.init writes static fields that aren't volatile. Each call reads this on the thread
    // that then calls NewPipeExtractor, and lazy's synchronized initialization makes that thread
    // see what init wrote. init takes GB as the country from the localization, and run replaces it
    // with the call's country.
    private val setUp = lazy { NewPipe.init(downloader, REQUEST_LOCALIZATION) }

    /**
     * Runs [block], which calls NewPipeExtractor and turns its answer into Dewfall's types. The
     * block gets the language and country to ask YouTube for, and may block its thread.
     */
    suspend fun <T> run(call: YouTubeCall, block: (RequestSettings) -> T): YouTubeResult<T> =
        mapFailures(call) {
            newPipeExtractorLock.withLock {
                runInterruptible(dispatcher) {
                    setUp.value
                    val settings = RequestSettings(contentCountryFor(deviceLocale()))
                    // NewPipeExtractor builds some extractors inside another one, such as the
                    // system playlist that an age-restricted channel's tab reads. The block can't
                    // apply the settings to those, so they ask for NewPipe's preferred country.
                    // Setting it here, on the thread that runs the block and under the lock, makes
                    // their requests name the call's country too.
                    NewPipe.setPreferredContentCountry(settings.contentCountry)
                    block(settings)
                }
            }
        }

    /**
     * Runs [block], which sends its own requests through the layer's downloader, with its host
     * checks, and calls no NewPipeExtractor code that reads or writes NewPipeExtractor's static
     * state. So it doesn't wait for NewPipeExtractor's lock, and NewPipeExtractor isn't set up for
     * it. It keeps the other rules of [run]: it runs on the dispatcher, a cancelled call throws
     * CancellationException, and a failure is logged once and comes back as its category. The block
     * may block its thread. Subtitle lines are read this way, as
     * docs/architecture.md#how-a-call-runs explains.
     */
    suspend fun <T> runDownload(call: YouTubeCall, block: (Downloader) -> T): YouTubeResult<T> =
        mapFailures(call) { runInterruptible(dispatcher) { block(downloader) } }

    /** The value that [work] returns as a Success, or its failure as the call's category. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> mapFailures(
        call: YouTubeCall,
        work: suspend () -> T,
    ): YouTubeResult<T> =
        try {
            YouTubeResult.Success(work())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The layer's edge, so it catches every exception: no NewPipeExtractor exception may
            // leave it (ADR 0008). NewPipeExtractor passes a cancelled request on as
            // InterruptedIOException, which would read as NETWORK, so a cancelled call throws
            // CancellationException instead.
            currentCoroutineContext().ensureActive()
            failure(call, e)
        }

    private fun failure(call: YouTubeCall, error: Exception): YouTubeResult.Failure {
        val category = youTubeErrorOf(error)
        val callField = LogField.state("call", call)
        val categoryField = LogField.state("error", category)
        // Only an app update fixes BROKEN, so it is logged as an error.
        if (category == YouTubeError.BROKEN) {
            log.error(CALL_FAILED, callField, categoryField, error = error)
        } else {
            log.warn(CALL_FAILED, callField, categoryField, error = error)
        }
        return YouTubeResult.Failure(category)
    }
}
