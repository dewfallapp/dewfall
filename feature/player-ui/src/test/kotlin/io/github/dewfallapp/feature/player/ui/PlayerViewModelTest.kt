package io.github.dewfallapp.feature.player.ui

import io.github.dewfallapp.core.data.video.VideoDetailsRepository
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerIntent.TryAgain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private val VIDEO = VideoId("aqz-KE-bpKQ")

private val DETAILS =
    VideoDetails(
        id = VIDEO,
        title = "A title",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "A channel",
        channelAvatars = emptyList(),
        description = "",
        duration = null,
        published = null,
        thumbnails = emptyList(),
        isLive = false,
    )

/** One call the fake repository got. It waits until the test completes its answer. */
private class FakeCall(val videoId: VideoId) {
    val answer = CompletableDeferred<YouTubeResult<VideoDetails>>()
}

/** A repository whose calls wait for the test, so a test decides when each one finishes. */
private class FakeRepository : VideoDetailsRepository {
    val calls = mutableListOf<FakeCall>()

    override suspend fun videoDetails(videoId: VideoId): YouTubeResult<VideoDetails> {
        val call = FakeCall(videoId).also { calls += it }
        return call.answer.await()
    }
}

/**
 * Tests which calls the ViewModel starts, and that their answers come back through the reducer. The
 * reducer's own tests cover the rest.
 */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val repository = FakeRepository()

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which local tests don't have. An unconfined
        // dispatcher starts each call at once, so the test sees it running right after creation.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `creating the ViewModel asks once for its video, and the answer shows`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository)

        assertEquals(listOf(VIDEO), repository.calls.map { it.videoId })
        repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))

        val shown = assertIs<DetailsArea.Shown>(viewModel.state.value.details)
        assertEquals("A title", shown.page.title)
    }

    @Test
    fun `trying again after NETWORK asks once more`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository)

        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.dispatch(TryAgain)

        assertEquals(listOf(VIDEO, VIDEO), repository.calls.map { it.videoId })
        assertEquals(DetailsArea.Loading(2), viewModel.state.value.details)
    }

    @Test
    fun `trying again after PRIVATE asks nothing`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository)

        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.PRIVATE))
        viewModel.dispatch(TryAgain)

        assertEquals(1, repository.calls.size)
        assertEquals(DetailsArea.Failed(1, YouTubeError.PRIVATE), viewModel.state.value.details)
    }

    @Test
    fun `trying again while loading asks nothing`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository)

        viewModel.dispatch(TryAgain)

        assertEquals(1, repository.calls.size)
    }
}
