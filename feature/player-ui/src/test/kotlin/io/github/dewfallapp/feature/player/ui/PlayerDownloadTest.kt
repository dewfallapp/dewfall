package io.github.dewfallapp.feature.player.ui

import androidx.lifecycle.SavedStateHandle
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.DownloadChip
import io.github.dewfallapp.feature.player.logic.PlayerIntent
import io.github.dewfallapp.feature.player.logic.PlayerState
import io.github.dewfallapp.feature.player.logic.SavedWatch
import io.github.dewfallapp.feature.player.logic.shownDownload
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Tests how the video screen's ViewModel follows, starts and pauses the video's download. */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDownloadTest {
    private val repository = FakeRepository()
    private val connection = FakeConnection()
    private val watchStates = FakeWatchStates()
    private val downloads = FakeDownloads()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        PlayerViewModel(
            PlayerArgs(VIDEO),
            repository,
            watchStates,
            downloads,
            connection,
            SavedStateHandle(),
        )

    /** A ViewModel whose details have arrived. */
    private fun shownViewModel(): PlayerViewModel =
        newViewModel().also {
            repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))
        }

    @Test
    fun `the chip follows the download, and only a new percentage changes the state`() = runTest {
        val viewModel = shownViewModel()
        assertEquals(DownloadChip.NotDownloaded, viewModel.state.value.shownDownload)
        val states = mutableListOf<PlayerState>()
        val following =
            launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.toList(states) }

        downloads.status.value = DownloadStatus.Downloading(DownloadProgress(420, 1_000))
        downloads.status.value = DownloadStatus.Downloading(DownloadProgress(421, 1_000))
        assertEquals(DownloadChip.Downloading(42), viewModel.state.value.shownDownload)
        assertEquals(2, states.size)

        downloads.status.value = DownloadStatus.Downloaded(1_000)
        assertEquals(DownloadChip.Downloaded, viewModel.state.value.shownDownload)
        following.cancel()
    }

    @Test
    fun `a download that can't be read leaves no chip and the rest of the screen working`() =
        runTest {
            downloads.failReads = true

            val viewModel = shownViewModel()

            assertNull(viewModel.state.value.shownDownload)
            assertIs<DetailsArea.Shown>(viewModel.state.value.details)
            assertEquals(SavedWatch.Known(watched = false), viewModel.state.value.saved)
            viewModel.dispatch(PlayerIntent.MarkWatched)
            assertEquals(SavedWatch.Known(watched = true), viewModel.state.value.saved)
        }

    @Test
    fun `a tap downloads the video with the summary of the details it shows`() = runTest {
        val viewModel = shownViewModel()

        viewModel.dispatch(PlayerIntent.Download)

        assertEquals(listOf("download ${VIDEO.value}"), downloads.writes)
        val summary = downloads.downloaded.single()
        assertEquals(DETAILS.title, summary.title)
        assertEquals(DETAILS.channelId, summary.channelId)
        assertEquals(DETAILS.channelName, summary.channelName)
        assertEquals(VideoKind.VIDEO, summary.kind)
    }

    @Test
    fun `a tap before the details arrive downloads nothing`() = runTest {
        val viewModel = newViewModel()

        viewModel.dispatch(PlayerIntent.Download)

        assertEquals(emptyList(), downloads.writes)
    }

    @Test
    fun `a tap on the running download pauses it`() = runTest {
        val viewModel = shownViewModel()

        viewModel.dispatch(PlayerIntent.PauseDownload)

        assertEquals(listOf("pause ${VIDEO.value}"), downloads.writes)
    }

    @Test
    fun `a write that fails changes nothing and doesn't crash`() = runTest {
        val viewModel = shownViewModel()
        downloads.failWrites = true

        viewModel.dispatch(PlayerIntent.Download)
        viewModel.dispatch(PlayerIntent.PauseDownload)

        assertEquals(listOf("download ${VIDEO.value}", "pause ${VIDEO.value}"), downloads.writes)
        assertEquals(DownloadChip.NotDownloaded, viewModel.state.value.shownDownload)
    }
}
