package io.github.dewfallapp.feature.youtubedebug.ui

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus
import io.github.dewfallapp.feature.youtubedebug.logic.DebugCall
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.Cancel
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.InputChanged
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.Run
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Tests which calls the ViewModel starts and stops, and that their answers come back through the
 * reducer. The reducer's own tests cover the rest. The fake data sources wait for the test to
 * complete their answer, so a test decides when each call finishes.
 */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeDebugViewModelTest {
    private val emptySearch = SearchResults(channels = emptyList(), videos = emptyList())

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which local tests don't have. An unconfined
        // dispatcher starts each call at once, so the test sees it running right after dispatch.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `run search sends the input and shows the answer`() = runTest {
        val search = FakeCall<String, SearchResults>()
        val viewModel = viewModel(search = { search.answer(it) })

        viewModel.dispatch(Run(DebugCall.SEARCH))

        assertEquals(listOf("nasa"), search.requests)
        assertEquals(CallStatus.Running("nasa"), viewModel.state.value.search.status)

        search.complete(YouTubeResult.Success(emptySearch))

        assertIs<CallStatus.Succeeded<*>>(viewModel.state.value.search.status)
    }

    @Test
    fun `each section calls its own function with the parsed ID`() = runTest {
        val details = FakeCall<VideoId, VideoDetails>()
        val uploads = FakeCall<ChannelId, ChannelUploads>()
        val links = FakeCall<VideoId, StreamLinks>()
        val viewModel =
            viewModel(
                videoDetails = { details.answer(it) },
                latestUploads = { uploads.answer(it) },
                streamLinks = { links.answer(it) },
            )

        viewModel.dispatch(InputChanged(DebugCall.VIDEO_DETAILS, " aqz-KE-bpKQ "))
        viewModel.dispatch(Run(DebugCall.VIDEO_DETAILS))
        viewModel.dispatch(Run(DebugCall.LATEST_UPLOADS))
        viewModel.dispatch(Run(DebugCall.STREAM_LINKS))

        assertEquals(listOf(VideoId("aqz-KE-bpKQ")), details.requests)
        assertEquals(listOf(ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ")), uploads.requests)
        assertEquals(listOf(VideoId("aqz-KE-bpKQ")), links.requests)

        details.complete(YouTubeResult.Failure(YouTubeError.PRIVATE))
        uploads.complete(YouTubeResult.Failure(YouTubeError.CHANNEL_CLOSED))
        links.complete(YouTubeResult.Failure(YouTubeError.BLOCKED))

        val state = viewModel.state.value
        assertEquals(CallStatus.Failed(YouTubeError.PRIVATE), state.videoDetails.status)
        assertEquals(CallStatus.Failed(YouTubeError.CHANNEL_CLOSED), state.latestUploads.status)
        assertEquals(CallStatus.Failed(YouTubeError.BLOCKED), state.streamLinks.status)
    }

    @Test
    fun `run with input that isn't an ID calls nothing`() = runTest {
        // The default data sources fail the test if they are called.
        val viewModel = viewModel()

        viewModel.dispatch(InputChanged(DebugCall.VIDEO_DETAILS, "@NASA"))
        viewModel.dispatch(InputChanged(DebugCall.LATEST_UPLOADS, "@NASA"))
        viewModel.dispatch(InputChanged(DebugCall.STREAM_LINKS, "@NASA"))
        viewModel.dispatch(Run(DebugCall.VIDEO_DETAILS))
        viewModel.dispatch(Run(DebugCall.LATEST_UPLOADS))
        viewModel.dispatch(Run(DebugCall.STREAM_LINKS))

        val state = viewModel.state.value
        assertEquals(CallStatus.InvalidInput, state.videoDetails.status)
        assertEquals(CallStatus.InvalidInput, state.latestUploads.status)
        assertEquals(CallStatus.InvalidInput, state.streamLinks.status)
    }

    @Test
    fun `cancel stops the call, and its answer never shows`() = runTest {
        val search = FakeCall<String, SearchResults>()
        val viewModel = viewModel(search = { search.answer(it) })
        viewModel.dispatch(Run(DebugCall.SEARCH))

        viewModel.dispatch(Cancel(DebugCall.SEARCH))

        assertTrue(search.wasCancelled)
        assertEquals(CallStatus.Cancelled, viewModel.state.value.search.status)
        search.complete(YouTubeResult.Success(emptySearch))
        assertEquals(CallStatus.Cancelled, viewModel.state.value.search.status)
    }

    @Test
    fun `run while running cancels the first call and shows only the second answer`() = runTest {
        val first = CompletableDeferred<YouTubeResult<SearchResults>>()
        val second = CompletableDeferred<YouTubeResult<SearchResults>>()
        val cancelled = mutableListOf<String>()
        val viewModel =
            viewModel(
                search = { query ->
                    try {
                        if (query == "nasa") first.await() else second.await()
                    } catch (e: CancellationException) {
                        cancelled += query
                        throw e
                    }
                }
            )

        viewModel.dispatch(Run(DebugCall.SEARCH))
        viewModel.dispatch(InputChanged(DebugCall.SEARCH, "blender"))
        viewModel.dispatch(Run(DebugCall.SEARCH))

        assertEquals(listOf("nasa"), cancelled)
        first.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        assertEquals(CallStatus.Running("blender"), viewModel.state.value.search.status)
        second.complete(YouTubeResult.Failure(YouTubeError.BROKEN))
        assertEquals(CallStatus.Failed(YouTubeError.BROKEN), viewModel.state.value.search.status)
    }

    @Test
    fun `run again with the same input cancels the first call and ignores its late answer`() =
        runTest {
            // The reducer can't tell the two answers apart, because both are for the same
            // request, so only cancelling the first call keeps its answer out.
            val answers =
                listOf(
                    CompletableDeferred<YouTubeResult<SearchResults>>(),
                    CompletableDeferred<YouTubeResult<SearchResults>>(),
                )
            val cancelledCalls = mutableListOf<Int>()
            var callCount = 0
            val viewModel =
                viewModel(
                    search = {
                        val call = callCount++
                        try {
                            answers[call].await()
                        } catch (e: CancellationException) {
                            cancelledCalls += call
                            throw e
                        }
                    }
                )

            viewModel.dispatch(Run(DebugCall.SEARCH))
            viewModel.dispatch(Run(DebugCall.SEARCH))

            assertEquals(listOf(0), cancelledCalls)
            answers[0].complete(YouTubeResult.Failure(YouTubeError.NETWORK))
            assertEquals(CallStatus.Running("nasa"), viewModel.state.value.search.status)
            answers[1].complete(YouTubeResult.Failure(YouTubeError.BROKEN))
            assertEquals(
                CallStatus.Failed(YouTubeError.BROKEN),
                viewModel.state.value.search.status,
            )
        }

    @Test
    fun `run with invalid input stops the section's running call`() = runTest {
        val details = FakeCall<VideoId, VideoDetails>()
        val viewModel = viewModel(videoDetails = { details.answer(it) })
        viewModel.dispatch(Run(DebugCall.VIDEO_DETAILS))

        viewModel.dispatch(InputChanged(DebugCall.VIDEO_DETAILS, "nope"))
        viewModel.dispatch(Run(DebugCall.VIDEO_DETAILS))

        assertTrue(details.wasCancelled)
        assertEquals(CallStatus.InvalidInput, viewModel.state.value.videoDetails.status)
    }

    @Test
    fun `sections run and cancel apart from each other`() = runTest {
        val search = FakeCall<String, SearchResults>()
        val uploads = FakeCall<ChannelId, ChannelUploads>()
        val viewModel =
            viewModel(search = { search.answer(it) }, latestUploads = { uploads.answer(it) })
        viewModel.dispatch(Run(DebugCall.SEARCH))
        viewModel.dispatch(Run(DebugCall.LATEST_UPLOADS))

        viewModel.dispatch(Cancel(DebugCall.SEARCH))
        val channel =
            Channel(
                id = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
                name = "A channel",
                avatars = emptyList(),
                description = "",
            )
        uploads.complete(YouTubeResult.Success(ChannelUploads(channel, videos = emptyList())))

        assertTrue(search.wasCancelled)
        assertFalse(uploads.wasCancelled)
        assertEquals(CallStatus.Cancelled, viewModel.state.value.search.status)
        assertIs<CallStatus.Succeeded<*>>(viewModel.state.value.latestUploads.status)
    }

    private fun viewModel(
        search: SearchRemoteDataSource = SearchRemoteDataSource { fail("search was called") },
        videoDetails: VideoDetailsRemoteDataSource = VideoDetailsRemoteDataSource {
            fail("videoDetails was called")
        },
        latestUploads: ChannelUploadsRemoteDataSource = ChannelUploadsRemoteDataSource {
            fail("latestUploads was called")
        },
        streamLinks: StreamLinksRemoteDataSource = StreamLinksRemoteDataSource {
            fail("streamLinks was called")
        },
    ): YouTubeDebugViewModel =
        YouTubeDebugViewModel(search, videoDetails, latestUploads, streamLinks)
}

/**
 * One fake call to the YouTube layer. It records each request, then waits until the test completes
 * its answer, and notes whether the caller cancelled it while it waited.
 */
private class FakeCall<R, T> {
    val requests = mutableListOf<R>()
    var wasCancelled = false
        private set

    private val result = CompletableDeferred<YouTubeResult<T>>()

    suspend fun answer(request: R): YouTubeResult<T> {
        requests += request
        try {
            return result.await()
        } catch (e: CancellationException) {
            wasCancelled = true
            throw e
        }
    }

    fun complete(answer: YouTubeResult<T>) {
        result.complete(answer)
    }
}
