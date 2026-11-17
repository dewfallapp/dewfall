package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.youtube.newpipe.NewPipeCalls
import io.github.dewfallapp.core.youtube.newpipe.NewPipeDownloader
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import io.github.dewfallapp.core.youtube.newpipe.saved.Endpoint
import io.github.dewfallapp.core.youtube.newpipe.saved.ReplayingClient
import io.github.dewfallapp.core.youtube.newpipe.saved.SAVED_SET_LOCALE
import io.github.dewfallapp.core.youtube.newpipe.saved.SavedSet
import io.github.dewfallapp.core.youtube.newpipe.saved.YouTubeLayer
import io.github.dewfallapp.core.youtube.newpipe.saved.loadSavedSet
import io.github.dewfallapp.core.youtube.newpipe.saved.resetNewPipeExtractorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Tests the parts of the live run that count what a call sends and logs, which LiveCheck relies on
 * for REQUESTS_CHANGED and READ_IN_PART. Nothing reaches the network: the calls are answered from
 * saved sets.
 */
class RunLiveCheckTest {
    private val requests = EndpointRecorder()
    private val warnings = WarningCounter { _, _, _ -> }

    @Test
    fun `the options say whether to skip, and which kind to report as failed`() {
        val lines = mutableListOf<String>()
        val logger = recordingLogger(lines)

        val strict = assertNotNull(optionsOf(arrayOf("results.txt", "false"), logger))
        val skipping =
            assertNotNull(optionsOf(arrayOf("results.txt", "true", "UPLOADS_JAWED"), logger))

        assertFalse(strict.skipUnreachable)
        assertNull(strict.simulate)
        assertTrue(skipping.skipUnreachable)
        assertEquals(SavedSet.UPLOADS_JAWED, skipping.simulate)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a wrong option stops the run, and the log doesn't repeat it`() {
        val lines = mutableListOf<String>()
        val logger = recordingLogger(lines)

        assertNull(optionsOf(arrayOf("results.txt"), logger))
        assertNull(optionsOf(arrayOf("results.txt", "yes"), logger))
        assertNull(optionsOf(arrayOf("results.txt", "false", "NOT_A_KIND"), logger))

        val notABoolean =
            "ERROR Dewfall.LiveCheck Whether to skip the calls that can't be checked is not " +
                "true or false"
        val notAKind =
            "ERROR Dewfall.LiveCheck The kind to report as failed is not a kind of the live check"
        assertEquals(listOf(notABoolean, notABoolean, notAKind), lines)
    }

    @Test
    fun `the run exits with an error only when a kind failed`() {
        val passed = KindResult.passed(SavedSet.SEARCH_NASA)
        val skipped =
            KindResult.skipped(SavedSet.VIDEO_BIG_BUCK_BUNNY, UncheckedBecause.RUNNER_BLOCKED)
        val failed =
            KindResult.failed(SavedSet.VIDEO_BIG_BUCK_BUNNY, UncheckedBecause.RUNNER_BLOCKED)

        assertEquals(0, exitCodeOf(listOf(passed, skipped)))
        assertEquals(1, exitCodeOf(listOf(passed, failed)))
    }

    @Test
    fun `WarningCounter counts the lines at WARN and above, and passes every line on`() {
        val lines = mutableListOf<String>()
        val counter = WarningCounter { level, tag, text -> lines += "$level $tag $text" }
        val log = Logger(counter, LogLevel.DEBUG).withTag("Test")

        log.debug("One")
        log.info("Two")
        log.warn("Three")
        log.error("Four")

        assertEquals(2, counter.count)
        assertEquals(
            listOf(
                "DEBUG Dewfall.Test One",
                "INFO Dewfall.Test Two",
                "WARN Dewfall.Test Three",
                "ERROR Dewfall.Test Four",
            ),
            lines,
        )
    }

    @Test
    fun `EndpointRecorder keeps each request's endpoint until they are taken`() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor(requests)
                .addInterceptor { chain -> emptyAnswer(chain.request()) }
                .build()

        client.newCall(post("https://www.youtube.com/youtubei/v1/browse")).execute().close()
        client.newCall(Request.Builder().url("https://example.com/").build()).execute().close()

        assertEquals(listOf(Endpoint.BROWSE, Endpoint.OTHER), requests.take())
        assertEquals(emptyList(), requests.take())
    }

    @Test
    fun `the caller takes only the requests and warnings of its own call`() = runTest {
        val uploads =
            callerAnswering(SavedSet.UPLOADS_JAWED).call(liveCallOf(SavedSet.UPLOADS_JAWED))
        val deleted =
            callerAnswering(SavedSet.CHANNEL_DELETED).call(liveCallOf(SavedSet.CHANNEL_DELETED))

        assertNull(uploads.error)
        assertEquals(SavedSet.UPLOADS_JAWED.requests, uploads.requests)
        assertEquals(0, uploads.warnings)
        assertEquals(YouTubeError.UNAVAILABLE, deleted.error)
        assertEquals(SavedSet.CHANNEL_DELETED.requests, deleted.requests)
        // The layer logs a failure once, at WARN.
        assertEquals(1, deleted.warnings)
    }

    /**
     * A YouTubeCaller over the layer, built as the live run builds it, with the shared [requests]
     * and [warnings], but answered from [set] instead of YouTube.
     */
    private fun TestScope.callerAnswering(set: SavedSet): YouTubeCaller {
        resetNewPipeExtractorState()
        val replay = ReplayingClient(set, loadSavedSet(set))
        // The recorder goes first, because the replaying interceptor answers without going on.
        val client = replay.client.newBuilder().apply { interceptors().add(0, requests) }.build()
        val logger = Logger(warnings, LogLevel.DEBUG)
        val calls =
            NewPipeCalls(NewPipeDownloader(client), logger, StandardTestDispatcher(testScheduler)) {
                SAVED_SET_LOCALE
            }
        return YouTubeCaller(YouTubeLayer(calls, logger), requests, warnings)
    }
}

/** The live check's call of [set]. */
private fun liveCallOf(set: SavedSet): LiveCall = LIVE_CALLS.single { it.kind == set }

/** A POST request with an empty body to [url]. */
private fun post(url: String): Request =
    Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()

/** An empty 200 answer to [request]. */
private fun emptyAnswer(request: Request): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("")
        .body(ByteArray(0).toResponseBody())
        .build()
