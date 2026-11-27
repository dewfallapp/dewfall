package io.github.dewfallapp.feature.downloads.ui

import io.github.dewfallapp.core.data.download.DownloadsRepository
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.feature.downloads.logic.BytesInUse
import io.github.dewfallapp.feature.downloads.logic.DownloadsEffect
import io.github.dewfallapp.feature.downloads.logic.DownloadsIntent
import io.github.dewfallapp.feature.downloads.logic.DownloadsList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private val MONDAY = Instant.parse("2026-11-23T09:00:00Z")

private val VIDEO =
    VideoSummary(
        id = VideoId("aaaaaaaaaaa"),
        title = "Wild yeasts and the first week of a sourdough starter",
        channelId = ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"),
        channelName = "Kettle and Crumb",
        duration = null,
        published = null,
        thumbnails = emptyList(),
        kind = VideoKind.VIDEO,
    )

private val WAITING =
    Download(VIDEO, DownloadStatus.Waiting(DownloadProgress(0, null)), MONDAY, finishedAt = null)

/** The downloads the test sets, and a record of the writes. */
private class FakeDownloads : DownloadsRepository {
    val downloads = MutableStateFlow<List<Download>>(emptyList())
    val bytesInUse = MutableStateFlow(0L)
    val writes = mutableListOf<String>()

    /** When true, the read throws, as a damaged database would. */
    var failReads = false

    /** When true, the read of the space throws, as a damaged database would. */
    var failBytesInUse = false

    /** When true, every write throws, as a full disk would. */
    var failWrites = false

    override fun downloads(): Flow<List<Download>> =
        if (failReads) flow { throw IllegalStateException("Damaged database") } else downloads

    override fun bytesInUse(): Flow<Long> =
        if (failBytesInUse) flow { throw IllegalStateException("Damaged database") } else bytesInUse

    override suspend fun pause(videoId: VideoId) {
        writes += "pause ${videoId.value}"
        check(!failWrites) { "The disk is full" }
    }

    override suspend fun download(video: VideoSummary) {
        writes += "download ${video.id.value}"
        check(!failWrites) { "The disk is full" }
    }

    override suspend fun remove(videoId: VideoId) {
        writes += "remove ${videoId.value}"
        check(!failWrites) { "The disk is full" }
    }

    override fun statuses() = error("The Downloads screen reads the downloads")

    override fun status(videoId: VideoId) = error("The Downloads screen reads the downloads")
}

/**
 * Tests that the ViewModel turns the read into the screen's state, reads only while its state has a
 * collector, and runs each tap's write. The reducer's own tests cover the lists.
 */
// setMain, resetMain, UnconfinedTestDispatcher, runCurrent and advanceTimeBy are still marked
// experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val repository = FakeDownloads()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun readers() = repository.downloads.subscriptionCount.value

    private fun spaceReaders() = repository.bytesInUse.subscriptionCount.value

    private fun effects(viewModel: DownloadsViewModel) =
        viewModel.pendingEffects.value.map { it.effect }

    @Test
    fun `nothing is read while nothing shows the state`() = runTest {
        val viewModel = DownloadsViewModel(repository)
        runCurrent()

        assertEquals(0, readers())
        assertEquals(DownloadsList.Loading, viewModel.state.value.list)
    }

    @Test
    fun `a shown screen turns each read into its list`() = runTest {
        val viewModel = DownloadsViewModel(repository)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(DownloadsList.NoDownloads, viewModel.state.value.list)

        repository.downloads.value = listOf(WAITING)
        runCurrent()
        assertEquals(DownloadsList.Shown(listOf(WAITING), emptyList()), viewModel.state.value.list)
    }

    @Test
    fun `reading stops 5 seconds after the last collector leaves`() = runTest {
        val viewModel = DownloadsViewModel(repository)
        val shown = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(1, readers())

        shown.cancel()
        advanceTimeBy(5_001)
        runCurrent()

        assertEquals(0, readers())
    }

    @Test
    fun `a read that throws shows unreadable, and the screen reads again when it shows again`() =
        runTest {
            repository.failReads = true
            val viewModel = DownloadsViewModel(repository)
            val shown = backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            assertEquals(DownloadsList.Unreadable, viewModel.state.value.list)
            shown.cancel()
            advanceTimeBy(1_000)
            runCurrent()

            repository.failReads = false
            repository.downloads.value = listOf(WAITING)
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()

            assertEquals(
                DownloadsList.Shown(listOf(WAITING), emptyList()),
                viewModel.state.value.list,
            )
        }

    @Test
    fun `each tap runs its write`() = runTest {
        val viewModel = DownloadsViewModel(repository)

        viewModel.dispatch(DownloadsIntent.Pause(VIDEO.id))
        viewModel.dispatch(DownloadsIntent.Resume(VIDEO))
        viewModel.dispatch(DownloadsIntent.Remove(VIDEO.id, finished = false))
        runCurrent()

        assertEquals(
            listOf("pause aaaaaaaaaaa", "download aaaaaaaaaaa", "remove aaaaaaaaaaa"),
            repository.writes,
        )
    }

    @Test
    fun `removing a finished download says so once it is gone, and a cancel says nothing`() =
        runTest {
            val viewModel = DownloadsViewModel(repository)

            viewModel.dispatch(DownloadsIntent.Remove(VIDEO.id, finished = false))
            runCurrent()
            assertEquals(emptyList(), effects(viewModel))

            viewModel.dispatch(DownloadsIntent.Remove(VIDEO.id, finished = true))
            runCurrent()
            assertEquals(listOf(DownloadsEffect.ShowRemoved), effects(viewModel))

            viewModel.effectHandled(viewModel.pendingEffects.value.single().id)
            assertEquals(emptyList(), effects(viewModel))
        }

    @Test
    fun `a write that throws changes nothing and says nothing`() = runTest {
        repository.failWrites = true
        val viewModel = DownloadsViewModel(repository)
        backgroundScope.launch { viewModel.state.collect {} }
        repository.downloads.value = listOf(WAITING)
        runCurrent()
        val before = viewModel.state.value

        viewModel.dispatch(DownloadsIntent.Pause(VIDEO.id))
        viewModel.dispatch(DownloadsIntent.Resume(VIDEO))
        viewModel.dispatch(DownloadsIntent.Remove(VIDEO.id, finished = true))
        runCurrent()

        assertEquals(before, viewModel.state.value)
        assertEquals(emptyList(), effects(viewModel))
        assertEquals(3, repository.writes.size)
    }

    @Test
    fun `a shown screen follows the space downloads take`() = runTest {
        val viewModel = DownloadsViewModel(repository)
        assertEquals(BytesInUse.Reading, viewModel.state.value.bytesInUse)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(BytesInUse.Read(0), viewModel.state.value.bytesInUse)

        repository.bytesInUse.value = 3_600
        runCurrent()
        assertEquals(BytesInUse.Read(3_600), viewModel.state.value.bytesInUse)
    }

    @Test
    fun `the space is read only while the state has a collector`() = runTest {
        val viewModel = DownloadsViewModel(repository)
        runCurrent()
        assertEquals(0, spaceReaders())

        val shown = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(1, spaceReaders())

        shown.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, spaceReaders())

        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(1, spaceReaders())
    }

    @Test
    fun `a read of the space that throws shows no amount and leaves the list`() = runTest {
        repository.failBytesInUse = true
        repository.downloads.value = listOf(WAITING)
        val viewModel = DownloadsViewModel(repository)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(BytesInUse.Unreadable, viewModel.state.value.bytesInUse)
        assertEquals(DownloadsList.Shown(listOf(WAITING), emptyList()), viewModel.state.value.list)
        assertEquals(1, readers())
    }

    @Test
    fun `after a failed read of the space, a screen that shows again reads it again`() = runTest {
        repository.failBytesInUse = true
        val viewModel = DownloadsViewModel(repository)
        val shown = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(BytesInUse.Unreadable, viewModel.state.value.bytesInUse)
        shown.cancel()
        advanceTimeBy(1_000)
        runCurrent()

        repository.failBytesInUse = false
        repository.bytesInUse.value = 420
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(BytesInUse.Read(420), viewModel.state.value.bytesInUse)
    }
}
