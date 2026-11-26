package io.github.dewfallapp.feature.player.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.WatchState
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.PlayerIntent.EnterFullscreen
import io.github.dewfallapp.feature.player.logic.PlayerIntent.ExitFullscreen
import io.github.dewfallapp.feature.player.logic.PlayerIntent.MarkUnwatched
import io.github.dewfallapp.feature.player.logic.PlayerIntent.MarkWatched
import io.github.dewfallapp.feature.player.logic.PlayerIntent.TryAgain
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.SavedWatch
import io.github.dewfallapp.feature.player.logic.StoredPosition
import io.github.dewfallapp.feature.player.logic.WaitingPosition
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

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
    private val watchStates = FakeWatchStates()
    private val downloads = FakeDownloads()

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

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        PlayerViewModel(
            PlayerArgs(VIDEO),
            repository,
            watchStates,
            downloads,
            connection,
            savedState,
        )

    @Test
    fun `creating the ViewModel asks once for its video, and the answer shows`() = runTest {
        val viewModel = newViewModel()

        assertEquals(listOf(VIDEO), repository.calls.map { it.videoId })
        repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))

        val shown = assertIs<DetailsArea.Shown>(viewModel.state.value.details)
        assertEquals("A title", shown.page.title)
    }

    @Test
    fun `trying again after NETWORK asks once more`() = runTest {
        val viewModel = newViewModel()

        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.dispatch(TryAgain)

        assertEquals(listOf(VIDEO, VIDEO), repository.calls.map { it.videoId })
        assertEquals(DetailsArea.Loading(2), viewModel.state.value.details)
    }

    @Test
    fun `trying again after PRIVATE asks nothing`() = runTest {
        val viewModel = newViewModel()

        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.PRIVATE))
        viewModel.dispatch(TryAgain)

        assertEquals(1, repository.calls.size)
        assertEquals(DetailsArea.Failed(1, YouTubeError.PRIVATE), viewModel.state.value.details)
    }

    @Test
    fun `trying again while loading asks nothing`() = runTest {
        val viewModel = newViewModel()

        viewModel.dispatch(TryAgain)

        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `closing after Try again releases the newest playback, which plays on`() = runTest {
        val viewModel = newViewModel()
        val store = storeOf(viewModel)

        viewModel.startPlayback(C.TIME_UNSET)
        val retried = viewModel.startPlayback(61_000)
        store.clear()

        assertEquals(listOf(VIDEO to C.TIME_UNSET, VIDEO to 61_000L), connection.prepared)
        assertEquals(listOf(retried), connection.releaseRequests)
        assertTrue(connection.stopRequests.isEmpty())
        assertTrue(connection.playing)
    }

    @Test
    fun `a video that started during the screen's exit keeps playing`() = runTest {
        val viewModel = newViewModel()
        val store = storeOf(viewModel)
        viewModel.startPlayback(C.TIME_UNSET)

        // The next screen's video starts before this ViewModel is cleared.
        connection.prepare(VideoId("YE7VzlLtp-4"), play = true, startPositionMs = C.TIME_UNSET)
        store.clear()

        assertEquals(listOf("playback 1"), connection.releaseRequests)
        assertTrue(connection.stopRequests.isEmpty())
        assertTrue(connection.playing)
        assertEquals("playback 2", connection.currentItemId)
    }

    @Test
    fun `a followed playback gets the description and is released as the screen's own`() = runTest {
        val viewModel = newViewModel()
        val store = storeOf(viewModel)

        viewModel.follow("playback from the mini player")
        repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))
        store.clear()

        assertEquals(
            listOf(Triple("playback from the mini player", "A title", "A channel")),
            connection.descriptions,
        )
        assertEquals(listOf("playback from the mini player"), connection.releaseRequests)
        assertTrue(connection.prepared.isEmpty())
    }

    @Test
    fun `the ViewModel holds the picture until it is cleared`() = runTest {
        val store = storeOf(newViewModel())
        assertEquals(1, connection.pictureHolds)

        store.clear()

        assertEquals(0, connection.pictureHolds)
    }

    @Test
    fun `fullscreen starts from the saved state and is written back`() = runTest {
        val savedState = SavedStateHandle(mapOf("fullscreen" to true))
        val viewModel = newViewModel(savedState)
        assertTrue(viewModel.state.value.fullscreen)

        viewModel.dispatch(ExitFullscreen)
        assertEquals(false, savedState["fullscreen"])

        viewModel.dispatch(EnterFullscreen)
        assertEquals(true, savedState["fullscreen"])
        assertTrue(viewModel.state.value.fullscreen)
    }

    @Test
    fun `a new screen starts out of fullscreen`() = runTest {
        assertFalse(newViewModel().state.value.fullscreen)
    }

    @Test
    fun `a failure shared by the details and playback has one Try again for both`() = runTest {
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)
        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))

        val retried = viewModel.tryAgainDetails(PlaybackFailure.NETWORK, positionMs = 5_000)

        assertEquals(2, repository.calls.size)
        assertEquals("playback 2", retried)
        assertEquals(VIDEO to 5_000L, connection.prepared.last())
    }

    @Test
    fun `the details' Try again leaves a different playback failure alone`() = runTest {
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)
        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))

        val retried = viewModel.tryAgainDetails(PlaybackFailure.NOT_DECODABLE, positionMs = 0)

        assertEquals(2, repository.calls.size)
        assertNull(retried)
        assertEquals(1, connection.prepared.size)
    }

    @Test
    fun `details that arrive after the start describe that playback`() = runTest {
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)
        assertTrue(connection.descriptions.isEmpty())

        repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))

        assertEquals(listOf(Triple("playback 1", "A title", "A channel")), connection.descriptions)
    }

    @Test
    fun `a start after the details describes the new playback at once`() = runTest {
        val viewModel = newViewModel()
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
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)

        repository.calls.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.startPlayback(C.TIME_UNSET)

        assertTrue(connection.descriptions.isEmpty())
    }

    @Test
    fun `the state follows whether the database marks the video, and not its position`() = runTest {
        val viewModel = newViewModel()
        assertEquals(SavedWatch.Known(watched = false), viewModel.state.value.saved)
        val states = mutableListOf<PlayerState>()
        val following =
            launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.toList(states) }

        // A save every 30 seconds while playing changes no screen state.
        watchStates.state.value = WatchState(40.minutes, watchedAt = null, updatedAt = MARKED)
        watchStates.state.value = WatchState(41.minutes, watchedAt = null, updatedAt = MARKED)
        assertEquals(1, states.size)

        watchStates.state.value = WatchState(41.minutes, watchedAt = MARKED, updatedAt = MARKED)
        assertEquals(SavedWatch.Known(watched = true), viewModel.state.value.saved)
        following.cancel()
    }

    @Test
    fun `the stored position follows the database outside the screen state`() = runTest {
        val viewModel = newViewModel()

        assertEquals(StoredPosition.Known(null), viewModel.storedPosition.first())
        watchStates.state.value = WatchState(40.minutes, watchedAt = MARKED, updatedAt = MARKED)
        assertEquals(StoredPosition.Known(40.minutes), viewModel.storedPosition.first())
    }

    @Test
    fun `a stored position that can't be read is unreadable`() = runTest {
        watchStates.failReads = true

        val viewModel = newViewModel()

        assertEquals(StoredPosition.Unreadable, viewModel.storedPosition.first())
    }

    @Test
    fun `the chip's taps mark and unmark the video, and the answer comes back`() = runTest {
        val viewModel = newViewModel()

        viewModel.dispatch(MarkWatched)
        assertEquals(
            SavedWatch.Known(watched = true),
            viewModel.state.value.saved,
        )

        viewModel.dispatch(MarkUnwatched)
        assertEquals(
            SavedWatch.Known(watched = false),
            viewModel.state.value.saved,
        )
        assertEquals(listOf("mark ${VIDEO.value}", "unmark ${VIDEO.value}"), watchStates.marks)
    }

    @Test
    fun `a database that can't be read leaves the chip hidden`() = runTest {
        watchStates.failReads = true

        val viewModel = newViewModel()

        assertEquals(SavedWatch.Unreadable, viewModel.state.value.saved)
    }

    @Test
    fun `a mark the database can't write changes nothing and doesn't crash`() = runTest {
        val viewModel = newViewModel()
        watchStates.failWrites = true

        viewModel.dispatch(MarkWatched)

        assertEquals(listOf("mark ${VIDEO.value}"), watchStates.marks)
        assertEquals(
            SavedWatch.Known(watched = false),
            viewModel.state.value.saved,
        )
    }

    @Test
    fun `a start waits for the stored answer, and a second tap meanwhile starts nothing`() =
        runTest {
            watchStates.state.value = WatchState(40.minutes, watchedAt = null, updatedAt = MARKED)
            val viewModel = newViewModel()
            val held = CompletableDeferred<Unit>()
            watchStates.holdReads = held
            val starts = mutableListOf<Long>()

            viewModel.afterStoredAnswer(screenPositionMs = 600_000) { starts += it }
            assertTrue(viewModel.waitsForStoredAnswer)
            viewModel.afterStoredAnswer(screenPositionMs = 600_000) { starts += it }
            assertEquals(emptyList(), starts)

            held.complete(Unit)
            assertEquals(listOf(2_400_000L), starts)
            assertFalse(viewModel.waitsForStoredAnswer)
        }

    @Test
    fun `a stored answer that can't be read starts from the screen's own position`() = runTest {
        watchStates.failReads = true
        val viewModel = newViewModel()
        val starts = mutableListOf<Long>()

        viewModel.afterStoredAnswer(screenPositionMs = 600_000) { starts += it }

        assertEquals(listOf(600_000L), starts)
    }

    @Test
    fun `a video with no stored position and no screen position starts from the default`() =
        runTest {
            val viewModel = newViewModel()
            val starts = mutableListOf<Long>()

            viewModel.afterStoredAnswer(screenPositionMs = null) { starts += it }

            assertEquals(listOf(C.TIME_UNSET), starts)
        }

    @Test
    fun `the last position is the player's, then a running wait, then the screen's, then a wait`() =
        runTest {
            watchStates.state.value = WatchState(40.minutes, watchedAt = null, updatedAt = MARKED)
            val held = CompletableDeferred<Unit>()
            watchStates.holdReads = held
            val viewModel = newViewModel()
            val starts = mutableListOf<Long>()
            val unknown = WaitingPosition(shownMs = 600_000, startMs = null)

            // The player's own position wins, even with nothing stored known yet.
            viewModel.withLastPosition(5_000, unknown) { starts += it }
            // A waiting screen that knows where it stands starts there at once.
            viewModel.withLastPosition(null, WaitingPosition(700_000, 700_000)) { starts += it }
            assertEquals(listOf(5_000L, 700_000L), starts)

            // Before the database answers, it waits, and a tap meanwhile does nothing, even one
            // that knows where it stands.
            viewModel.withLastPosition(null, unknown) { starts += it }
            viewModel.withLastPosition(null, WaitingPosition(700_000, 700_000)) { starts += it }
            assertEquals(listOf(5_000L, 700_000L), starts)
            held.complete(Unit)
            assertEquals(listOf(5_000L, 700_000L, 2_400_000L), starts)
        }

    @Test
    fun `leaving the screen's composition cancels a wait, so it starts nothing`() = runTest {
        val held = CompletableDeferred<Unit>()
        watchStates.holdReads = held
        val viewModel = newViewModel()
        val starts = mutableListOf<Long>()

        viewModel.afterStoredAnswer(screenPositionMs = 600_000) { starts += it }
        viewModel.cancelStoredAnswer()
        held.complete(Unit)

        assertEquals(emptyList(), starts)
        assertFalse(viewModel.waitsForStoredAnswer)
    }
}

class PlaybackOfVideoTest {
    private val videos = mapOf("first" to VIDEO, "second" to VideoId("YE7VzlLtp-4"))

    @Test
    fun `the player's item counts only when it plays the screen's video`() {
        assertEquals("first", playbackOfVideo("first", VIDEO, videos::get))
        assertNull(playbackOfVideo("second", VIDEO, videos::get))
    }

    @Test
    fun `no item, or an item with no video, counts as none`() {
        assertNull(playbackOfVideo(null, VIDEO, videos::get))
        assertNull(playbackOfVideo("gone", VIDEO, videos::get))
    }
}
