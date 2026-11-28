package io.github.dewfallapp.feature.player.ui

import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.C
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.feature.player.logic.DetailsArea
import io.github.dewfallapp.feature.player.logic.DownloadChip
import io.github.dewfallapp.feature.player.logic.PlayerIntent
import io.github.dewfallapp.feature.player.logic.page
import io.github.dewfallapp.feature.player.logic.shownDownload
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The summary saved with the video's download, with other names than YouTube's [DETAILS]. */
private val SAVED =
    VideoSummary(
        id = VIDEO,
        title = "A saved title",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "A saved channel",
        duration = 12.minutes,
        published = null,
        thumbnails = emptyList(),
        kind = VideoKind.VIDEO,
    )

/** Tests how the video screen's ViewModel shows the details saved with the video's download. */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSavedDetailsTest {
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

    private fun failCall(index: Int = 0, error: YouTubeError = YouTubeError.NETWORK) {
        repository.calls[index].answer.complete(YouTubeResult.Failure(error))
    }

    @Test
    fun `offline with a download, the saved details show and describe the playback`() = runTest {
        downloads.savedVideo.value = SAVED
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)

        failCall()

        val details = assertIs<DetailsArea.Saved>(viewModel.state.value.details)
        assertEquals("A saved title", details.page.title)
        assertEquals(
            listOf(Triple("playback 1", "A saved title", "A saved channel")),
            connection.descriptions,
        )
    }

    @Test
    fun `a summary that comes after the failure shows and describes the playback`() = runTest {
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)
        failCall()
        assertIs<DetailsArea.Failed>(viewModel.state.value.details)
        assertTrue(connection.descriptions.isEmpty())

        downloads.savedVideo.value = SAVED

        assertEquals("A saved title", viewModel.state.value.details.page?.title)
        assertEquals(
            listOf(Triple("playback 1", "A saved title", "A saved channel")),
            connection.descriptions,
        )
    }

    @Test
    fun `a start while the saved details show describes the new playback`() = runTest {
        downloads.savedVideo.value = SAVED
        val viewModel = newViewModel()
        failCall()

        viewModel.startPlayback(C.TIME_UNSET)

        assertEquals(
            listOf(Triple("playback 1", "A saved title", "A saved channel")),
            connection.descriptions,
        )
    }

    @Test
    fun `a summary that changes while YouTube's details show describes nothing again`() = runTest {
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)
        repository.calls.single().answer.complete(YouTubeResult.Success(DETAILS))

        downloads.savedVideo.value = SAVED

        assertIs<DetailsArea.Shown>(viewModel.state.value.details)
        assertEquals(listOf(Triple("playback 1", "A title", "A channel")), connection.descriptions)
    }

    @Test
    fun `a summary that can't be read leaves the failure`() = runTest {
        downloads.failSavedVideo = true
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)

        failCall()

        assertEquals(DetailsArea.Failed(1, YouTubeError.NETWORK), viewModel.state.value.details)
        assertNull(viewModel.state.value.savedVideo)
        assertTrue(connection.descriptions.isEmpty())
    }

    @Test
    fun `removing the download brings back the failure and keeps the playback's names`() = runTest {
        downloads.savedVideo.value = SAVED
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)
        failCall()

        downloads.savedVideo.value = null

        assertEquals(
            DetailsArea.Failed(1, YouTubeError.NETWORK),
            viewModel.state.value.details,
        )
        assertEquals(
            listOf(Triple("playback 1", "A saved title", "A saved channel")),
            connection.descriptions,
        )
    }

    @Test
    fun `the download chip shows with the saved details`() = runTest {
        downloads.savedVideo.value = SAVED
        downloads.status.value = DownloadStatus.Paused(DownloadProgress(400, 1_000))
        val viewModel = newViewModel()

        failCall()

        assertEquals(DownloadChip.Paused(40), viewModel.state.value.shownDownload)
    }

    @Test
    fun `a tap on the chip while the saved details show downloads the saved summary`() = runTest {
        downloads.savedVideo.value = SAVED
        val viewModel = newViewModel()
        failCall()

        viewModel.dispatch(PlayerIntent.Download)

        assertEquals(listOf(SAVED), downloads.downloaded)
    }

    @Test
    fun `Try again from the saved details asks once more, and retries a shared failure`() =
        runTest {
            downloads.savedVideo.value = SAVED
            val viewModel = newViewModel()
            viewModel.startPlayback(C.TIME_UNSET)
            failCall()

            val retried = viewModel.tryAgainDetails(PlaybackFailure.NETWORK, positionMs = 5_000)

            assertEquals(2, repository.calls.size)
            assertEquals(DetailsArea.Loading(2), viewModel.state.value.details)
            assertEquals("playback 2", retried)
            repository.calls[1].answer.complete(YouTubeResult.Success(DETAILS))
            assertIs<DetailsArea.Shown>(viewModel.state.value.details)
            assertEquals(
                Triple("playback 2", "A title", "A channel"),
                connection.descriptions.last(),
            )
        }

    @Test
    fun `another failure, such as a private video, shows no saved details`() = runTest {
        downloads.savedVideo.value = SAVED
        val viewModel = newViewModel()
        viewModel.startPlayback(C.TIME_UNSET)

        failCall(error = YouTubeError.PRIVATE)

        assertEquals(DetailsArea.Failed(1, YouTubeError.PRIVATE), viewModel.state.value.details)
        assertTrue(connection.descriptions.isEmpty())
    }
}
