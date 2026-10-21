package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.localization.Localization

/** How long a test waits for another thread before it fails. */
private const val WAIT_SECONDS = 5L

/**
 * How long a test waits to see that something doesn't happen. A call that the lock fails to hold
 * back starts within a few milliseconds.
 */
private const val QUIET_MILLIS = 300L

class NewPipeCallsTest {
    /** The first line of each log line: level, tag, message and fields, without the error. */
    private val lines = Collections.synchronizedList(mutableListOf<String>())
    private val logger =
        Logger(
            { level, tag, text -> lines += "$level $tag ${text.lines().first()}" },
            LogLevel.DEBUG,
        )

    @Test
    fun `a call returns its block's value and logs nothing`() = runTest {
        val calls = NewPipeCalls(NoRequests, logger, StandardTestDispatcher(testScheduler))

        val result = calls.run(YouTubeCall.SEARCH) { 42 }

        assertEquals(YouTubeResult.Success(42), result)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `the block runs with NewPipeExtractor set up with the downloader and British English`() =
        runTest {
            val calls = NewPipeCalls(NoRequests, logger, StandardTestDispatcher(testScheduler))

            calls.run(YouTubeCall.SEARCH) {
                assertSame(NoRequests, NewPipe.getDownloader())
                assertEquals(Localization("en", "GB"), NewPipe.getPreferredLocalization())
            }
        }

    @Test
    fun `each call gets the device's country at the time of the call`() = runTest {
        var locale = Locale.forLanguageTag("bn-BD")
        val calls =
            NewPipeCalls(NoRequests, logger, StandardTestDispatcher(testScheduler)) { locale }

        val first = calls.run(YouTubeCall.SEARCH) { it.contentCountry.countryCode }
        locale = Locale.forLanguageTag("de-DE")
        val second = calls.run(YouTubeCall.SEARCH) { it.contentCountry.countryCode }

        assertEquals(YouTubeResult.Success("BD"), first)
        assertEquals(YouTubeResult.Success("DE"), second)
    }

    @Test
    fun `a failure comes back as its category and is logged once with the call`() = runTest {
        val calls = NewPipeCalls(NoRequests, logger, StandardTestDispatcher(testScheduler))

        val result = calls.run(YouTubeCall.STREAM_LINKS) { throw IOException("No connection") }

        assertEquals(YouTubeResult.Failure(YouTubeError.NETWORK), result)
        assertEquals(
            listOf("WARN Dewfall.YouTube YouTube call failed call=STREAM_LINKS error=NETWORK"),
            lines,
        )
    }

    @Test
    fun `a failure that needs an update is logged as an error`() = runTest {
        val calls = NewPipeCalls(NoRequests, logger, StandardTestDispatcher(testScheduler))

        val result = calls.run(YouTubeCall.LATEST_UPLOADS) { throw ParsingException("Changed") }

        assertEquals(YouTubeResult.Failure(YouTubeError.BROKEN), result)
        assertEquals(
            listOf("ERROR Dewfall.YouTube YouTube call failed call=LATEST_UPLOADS error=BROKEN"),
            lines,
        )
    }

    @Test
    fun `a failed call writes the error's class name, never its message`() = runTest {
        val texts = mutableListOf<String>()
        val textLogger = Logger({ _, _, text -> texts += text }, LogLevel.DEBUG)
        val calls = NewPipeCalls(NoRequests, textLogger, StandardTestDispatcher(testScheduler))

        calls.run(YouTubeCall.VIDEO_DETAILS) {
            throw ParsingException("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        }

        val text = texts.single()
        assertTrue(text.contains(ParsingException::class.java.name))
        assertFalse(text.contains("dQw4w9WgXcQ"))
    }

    @Test
    fun `cancelling a waiting call throws CancellationException and logs nothing`() = runBlocking {
        // A real thread, because cancelling interrupts the thread that the call blocks.
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val calls = NewPipeCalls(NoRequests, logger, dispatcher)
            val waiting = CountDownLatch(1)
            val thrown = AtomicReference<Throwable>()
            val job =
                launch(Dispatchers.Default) {
                    try {
                        calls.run(YouTubeCall.STREAM_LINKS) {
                            waiting.countDown()
                            waitAsTheDownloaderDoes()
                        }
                    } catch (e: CancellationException) {
                        thrown.set(e)
                        throw e
                    }
                }

            assertTrue(waiting.await(WAIT_SECONDS, TimeUnit.SECONDS))
            job.cancelAndJoin()

            assertIs<CancellationException>(thrown.get())
            assertEquals(emptyList(), lines)
        }
    }

    @Test
    fun `a cancelled call throws CancellationException even if the block swallows the interrupt`() =
        runBlocking {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                val calls = NewPipeCalls(NoRequests, logger, dispatcher)
                val waiting = CountDownLatch(1)
                val thrown = AtomicReference<Throwable>()
                // The caller runs on the call's own dispatcher, so nothing dispatches back to it.
                val job =
                    launch(dispatcher) {
                        try {
                            calls.run(YouTubeCall.STREAM_LINKS) {
                                waiting.countDown()
                                waitAndSwallowTheInterrupt()
                            }
                        } catch (e: CancellationException) {
                            thrown.set(e)
                            throw e
                        }
                    }

                assertTrue(waiting.await(WAIT_SECONDS, TimeUnit.SECONDS))
                job.cancelAndJoin()

                assertIs<CancellationException>(thrown.get())
                assertEquals(emptyList(), lines)
            }
        }

    @Test
    fun `the next call runs after a call is cancelled or fails`() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val calls = NewPipeCalls(NoRequests, logger, dispatcher)
            val waiting = CountDownLatch(1)
            val cancelled =
                launch(Dispatchers.Default) {
                    calls.run(YouTubeCall.STREAM_LINKS) {
                        waiting.countDown()
                        waitAsTheDownloaderDoes()
                    }
                }
            assertTrue(waiting.await(WAIT_SECONDS, TimeUnit.SECONDS))
            cancelled.cancelAndJoin()

            // A lock that stayed held would make these calls wait, so the timeout fails the test
            // instead of hanging it.
            val next =
                withTimeout(WAIT_SECONDS.seconds) {
                    calls.run(YouTubeCall.SEARCH) { throw IOException("No connection") }
                    calls.run(YouTubeCall.SEARCH) { 7 }
                }

            assertEquals(YouTubeResult.Success(7), next)
        }
    }

    @Test
    fun `two calls never run NewPipeExtractor at the same time`() = runBlocking {
        // Two real threads, so that only the lock can keep the second call out, and two
        // NewPipeCalls, because NewPipeExtractor's caches belong to the process.
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val calls = NewPipeCalls(NoRequests, logger, dispatcher)
            val otherCalls = NewPipeCalls(NoRequests, logger, dispatcher)
            val running = AtomicInteger()
            val mostAtOnce = AtomicInteger()
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            fun enter() = mostAtOnce.accumulateAndGet(running.incrementAndGet(), ::maxOf)

            val first =
                launch(Dispatchers.Default) {
                    calls.run(YouTubeCall.VIDEO_DETAILS) {
                        enter()
                        firstStarted.countDown()
                        releaseFirst.await(WAIT_SECONDS, TimeUnit.SECONDS)
                        running.decrementAndGet()
                    }
                }
            assertTrue(firstStarted.await(WAIT_SECONDS, TimeUnit.SECONDS))
            val second =
                launch(Dispatchers.Default) {
                    otherCalls.run(YouTubeCall.STREAM_LINKS) {
                        enter()
                        secondStarted.countDown()
                        running.decrementAndGet()
                    }
                }

            // The second call waits for the lock while the first one holds it.
            assertFalse(secondStarted.await(QUIET_MILLIS, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            joinAll(first, second)

            assertEquals(0, secondStarted.count)
            assertEquals(1, mostAtOnce.get())
        }
    }

    /**
     * Blocks as NewPipeDownloader does while it waits for an answer, and passes an interrupt on as
     * NewPipeExtractor does, as an InterruptedIOException.
     */
    private fun waitAsTheDownloaderDoes() {
        try {
            CountDownLatch(1).await(WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw InterruptedIOException("Cancelled").apply { initCause(e) }
        }
    }

    /**
     * Blocks until it is interrupted, then carries on and returns whether it waited to the end, as
     * NewPipeExtractor does where it catches every exception of an optional request.
     */
    private fun waitAndSwallowTheInterrupt(): Boolean =
        try {
            CountDownLatch(1).await(WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            false
        }

    /** A downloader for tests whose calls never send a request. */
    private object NoRequests : Downloader() {
        override fun execute(request: Request): Response =
            throw AssertionError("A NewPipeCalls test sent a request")
    }
}
