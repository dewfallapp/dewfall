package io.github.dewfallapp.feature.player.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.Player
import io.github.dewfallapp.core.data.video.VideoDetailsRepository
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.core.playback.stopsPlayback
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerIntent.TryAgain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * A connection that records what the ViewModel asks of it. Its player's current item is the latest
 * prepared one, unless a test sets it, and stopping follows the real rule, stopsPlayback.
 */
private class FakeConnection : PlaybackConnection {
    val prepared = mutableListOf<Pair<VideoId, Long>>()
    val stopRequests = mutableListOf<String>()
    val descriptions = mutableListOf<Triple<String, String, String>>()
    var currentItemId: String? = null
    var playing = false

    override val player: StateFlow<Player?> = MutableStateFlow(null)
    override val failure: StateFlow<PlaybackFailure?> = MutableStateFlow(null)

    override fun prepare(videoId: VideoId, play: Boolean, startPositionMs: Long): String {
        prepared += videoId to startPositionMs
        val id = "playback ${prepared.size}"
        currentItemId = id
        playing = play
        return id
    }

    override fun isKnown(playbackId: String): Boolean = true

    override fun pause(playbackId: String) {
        if (currentItemId == playbackId) playing = false
    }

    override fun stop(playbackId: String) {
        stopRequests += playbackId
        if (stopsPlayback(currentItemId, playbackId)) {
            playing = false
            currentItemId = null
        }
    }

    override fun describe(playbackId: String, title: String, channelName: String) {
        descriptions += Triple(playbackId, title, channelName)
    }

    override fun setAppVisible(visible: Boolean) = Unit
}

/** A store that holds [viewModel], so the test can clear it as navigation does. */
private fun storeOf(viewModel: PlayerViewModel): ViewModelStore {
    val store = ViewModelStore()
    ViewModelProvider.create(store, viewModelFactory { initializer { viewModel } })[
            PlayerViewModel::class]
    return store
}

/**
 * Tests which calls the ViewModel starts, and that their answers come back through the reducer. The
 * reducer's own tests cover the rest.
 */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val repository = FakeRepository()
    private val connection = FakeConnection()

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
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)

        assertEquals(listOf(VIDEO), repository.calls.map { it.videoId })
        repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))

        val shown = assertIs<DetailsArea.Shown>(viewModel.state.value.details)
        assertEquals("A title", shown.page.title)
    }

    @Test
    fun `trying again after NETWORK asks once more`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)

        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.dispatch(TryAgain)

        assertEquals(listOf(VIDEO, VIDEO), repository.calls.map { it.videoId })
        assertEquals(DetailsArea.Loading(2), viewModel.state.value.details)
    }

    @Test
    fun `trying again after PRIVATE asks nothing`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)

        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.PRIVATE))
        viewModel.dispatch(TryAgain)

        assertEquals(1, repository.calls.size)
        assertEquals(DetailsArea.Failed(1, YouTubeError.PRIVATE), viewModel.state.value.details)
    }

    @Test
    fun `trying again while loading asks nothing`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)

        viewModel.dispatch(TryAgain)

        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `closing after Try again stops the newest playback`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)
        val store = storeOf(viewModel)

        viewModel.startPlayback(C.TIME_UNSET)
        val retried = viewModel.startPlayback(61_000)
        store.clear()

        assertEquals(listOf(VIDEO to C.TIME_UNSET, VIDEO to 61_000L), connection.prepared)
        assertEquals(listOf(retried), connection.stopRequests)
        assertFalse(connection.playing)
    }

    @Test
    fun `a video that started during the screen's exit keeps playing`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)
        val store = storeOf(viewModel)
        viewModel.startPlayback(C.TIME_UNSET)

        // The next screen's video starts before this ViewModel is cleared.
        connection.prepare(VideoId("YE7VzlLtp-4"), play = true, startPositionMs = C.TIME_UNSET)
        store.clear()

        assertEquals(listOf("playback 1"), connection.stopRequests)
        assertTrue(connection.playing)
        assertEquals("playback 2", connection.currentItemId)
    }

    @Test
    fun `a failure shared by the details and playback has one Try again for both`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)
        viewModel.startPlayback(C.TIME_UNSET)
        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))

        val retried = viewModel.tryAgainDetails(PlaybackFailure.NETWORK, positionMs = 5_000)

        assertEquals(2, repository.calls.size)
        assertEquals("playback 2", retried)
        assertEquals(VIDEO to 5_000L, connection.prepared.last())
    }

    @Test
    fun `the details' Try again leaves a different playback failure alone`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)
        viewModel.startPlayback(C.TIME_UNSET)
        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))

        val retried = viewModel.tryAgainDetails(PlaybackFailure.NOT_DECODABLE, positionMs = 0)

        assertEquals(2, repository.calls.size)
        assertNull(retried)
        assertEquals(1, connection.prepared.size)
    }

    @Test
    fun `details that arrive after the start describe that playback`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)
        viewModel.startPlayback(C.TIME_UNSET)
        assertTrue(connection.descriptions.isEmpty())

        repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))

        assertEquals(listOf(Triple("playback 1", "A title", "A channel")), connection.descriptions)
    }

    @Test
    fun `a start after the details describes the new playback at once`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)
        repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))
        assertTrue(connection.descriptions.isEmpty())

        viewModel.startPlayback(C.TIME_UNSET)
        // A playback "Try again" makes a new playback ID, which gets the description too.
        viewModel.startPlayback(positionMs = 5_000)

        assertEquals(
            listOf(
                Triple("playback 1", "A title", "A channel"),
                Triple("playback 2", "A title", "A channel"),
            ),
            connection.descriptions,
        )
    }

    @Test
    fun `details that failed describe nothing`() = runTest {
        val viewModel = PlayerViewModel(PlayerArgs(VIDEO), repository, connection)
        viewModel.startPlayback(C.TIME_UNSET)

        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.startPlayback(C.TIME_UNSET)

        assertTrue(connection.descriptions.isEmpty())
    }
}
