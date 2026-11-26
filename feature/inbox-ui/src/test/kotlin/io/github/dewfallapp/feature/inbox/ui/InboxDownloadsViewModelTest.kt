package io.github.dewfallapp.feature.inbox.ui

import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.feature.inbox.logic.InboxContent
import io.github.dewfallapp.feature.inbox.logic.InboxGroup
import io.github.dewfallapp.feature.inbox.logic.InboxIntent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Tests how the inbox's ViewModel follows, starts and pauses the videos' downloads. */
// setMain, resetMain, UnconfinedTestDispatcher and runCurrent are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class InboxDownloadsViewModelTest {
    private val subscriptions = FakeSubscriptions()
    private val storedVideos = FakeStoredVideos()
    private val checker = FakeChecker()
    private val downloads = FakeDownloads()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        subscriptions.channels.value = listOf(SubscribedChannel(KETTLE, SUBSCRIBED_AT))
        storedVideos.inbox.value = listOf(VIDEO)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val shownContent =
        InboxContent.Videos(listOf(InboxGroup(KETTLE.id, KETTLE.name, emptyList(), listOf(VIDEO))))

    @Test
    fun `a shown inbox gives each row its download status`() = runTest {
        val viewModel = InboxViewModel(subscriptions, storedVideos, checker, downloads)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(DownloadStatus.NotDownloaded, viewModel.state.value.downloadOf(VIDEO.id))

        val downloading = DownloadStatus.Downloading(DownloadProgress(42, 100))
        downloads.statuses.value = mapOf(VIDEO.id to downloading)
        runCurrent()

        assertEquals(downloading, viewModel.state.value.downloadOf(VIDEO.id))
        assertEquals(shownContent, viewModel.state.value.content)
    }

    @Test
    fun `a status read that throws leaves the rows without a control and the inbox as it was`() =
        runTest {
            downloads.failReads = true
            val viewModel = InboxViewModel(subscriptions, storedVideos, checker, downloads)
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()

            assertNull(viewModel.state.value.downloadOf(VIDEO.id))
            assertEquals(shownContent, viewModel.state.value.content)
            viewModel.dispatch(InboxIntent.CheckForNewVideos)
            assertEquals(1, checker.starts)
        }

    @Test
    fun `a row's taps download and pause its video`() = runTest {
        val viewModel = InboxViewModel(subscriptions, storedVideos, checker, downloads)

        viewModel.dispatch(InboxIntent.Download(VIDEO))
        viewModel.dispatch(InboxIntent.PauseDownload(VIDEO.id))

        assertEquals(
            listOf("download ${VIDEO.id.value}", "pause ${VIDEO.id.value}"),
            downloads.writes,
        )
    }

    @Test
    fun `a write that throws doesn't crash`() = runTest {
        downloads.failWrites = true
        val viewModel = InboxViewModel(subscriptions, storedVideos, checker, downloads)

        viewModel.dispatch(InboxIntent.Download(VIDEO))
        viewModel.dispatch(InboxIntent.PauseDownload(VIDEO.id))

        assertEquals(2, downloads.writes.size)
    }
}
