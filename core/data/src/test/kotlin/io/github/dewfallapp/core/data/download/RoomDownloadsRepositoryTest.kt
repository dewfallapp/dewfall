package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.data.database.FixedClock
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val NOW = Instant.parse("2026-11-26T12:00:00Z")
private val PUBLISHED = Instant.parse("2026-11-20T12:00:00Z")

private val VIDEO =
    VideoSummary(
        id = VideoId("aaaaaaaaaaa"),
        title = "Wild yeasts and the first week of a sourdough starter",
        channelId = ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"),
        channelName = "Kettle and Crumb",
        duration = 54.minutes,
        published = PublishedTime(PUBLISHED, isApproximate = true),
        thumbnails = listOf(Thumbnail("https://i.example.invalid/vi/a/hq.jpg", 480, 360)),
        kind = VideoKind.VIDEO,
    )

/** Counts its starts. */
private class CountingScheduler : DownloadScheduler {
    var starts = 0

    override fun start() {
        starts++
    }
}

// runCurrent is still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class RoomDownloadsRepositoryTest {
    private val scheduler = CountingScheduler()

    private fun repository(dao: FakeDownloadDao, scope: CoroutineScope) =
        RoomDownloadsRepository(dao, scheduler, FixedClock(NOW), { "folder-1" }, scope)

    @Test
    fun `a download keeps the whole summary in a waiting row, then starts a run`() = runTest {
        val dao = FakeDownloadDao()

        repository(dao, backgroundScope).download(VIDEO)

        val row = dao.requestedRow!!
        assertEquals("aaaaaaaaaaa", row.videoId)
        assertEquals("folder-1", row.folder)
        assertEquals(VIDEO.title, row.title)
        assertEquals("UCaaaaaaaaaaaaaaaaaaaaaa", row.channelId)
        assertEquals("Kettle and Crumb", row.channelName)
        assertEquals(54 * 60_000L, row.durationMs)
        assertEquals(PUBLISHED, row.publishedAt)
        assertEquals(true, row.publishedApproximate)
        assertEquals(VideoKind.VIDEO, row.kind)
        assertEquals(VIDEO.thumbnails, row.thumbnails)
        assertEquals(DownloadState.WAITING, row.state)
        assertEquals(NOW, row.requestedAt)
        assertEquals(0, row.downloadedBytes)
        assertEquals(1, scheduler.starts)
    }

    @Test
    fun `asking again for a video that already waits still starts a run`() = runTest {
        val dao = FakeDownloadDao(requested = false)

        repository(dao, backgroundScope).download(VIDEO)

        assertEquals(1, scheduler.starts)
    }

    @Test
    fun `pausing passes the ID and the clock's time`() = runTest {
        val dao = FakeDownloadDao()

        repository(dao, backgroundScope).pause(VIDEO.id)

        assertEquals(listOf("pause aaaaaaaaaaa $NOW"), dao.calls)
        assertEquals(0, scheduler.starts)
    }

    @Test
    fun `a write finishes after its caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = FakeDownloadDao(gate = gate)
        val repository = repository(dao, backgroundScope)

        val caller = launch { repository.download(VIDEO) }
        runCurrent()
        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("request aaaaaaaaaaa"), dao.calls)
        assertEquals(1, scheduler.starts)
    }

    @Test
    fun `a video with no row isn't downloaded`() = runTest {
        val repository = repository(FakeDownloadDao(), backgroundScope)

        assertEquals(DownloadStatus.NotDownloaded, repository.status(VIDEO.id).first())
        assertEquals(emptyMap(), repository.statuses().first())
    }

    @Test
    fun `each state becomes its status`() = runTest {
        val separate = { state: DownloadState ->
            row(state = state, videoItag = 136, videoBytes = 800, audioItag = 251, audioBytes = 200)
                .copy(downloadedBytes = 420)
        }
        val progress = DownloadProgress(420, 1_000)
        val expected =
            mapOf(
                DownloadState.WAITING to DownloadStatus.Waiting(progress),
                DownloadState.DOWNLOADING to DownloadStatus.Downloading(progress),
                DownloadState.PAUSED to DownloadStatus.Paused(progress),
                DownloadState.DOWNLOADED to DownloadStatus.Downloaded(420),
            )

        for ((state, status) in expected) {
            val repository = repository(FakeDownloadDao(listOf(separate(state))), backgroundScope)
            assertEquals(status, repository.status(VIDEO.id).first())
        }
        val failed = row(state = DownloadState.FAILED, failure = DownloadFailure.NO_SPACE)
        assertEquals(
            DownloadStatus.Failed(DownloadFailure.NO_SPACE),
            repository(FakeDownloadDao(listOf(failed)), backgroundScope).status(VIDEO.id).first(),
        )
    }

    @Test
    fun `the total is known only when every chosen file's size is`() = runTest {
        val cases =
            listOf(
                row(videoItag = 18, videoBytes = 500) to 500L,
                row(videoItag = 136, videoBytes = 800, audioItag = 251, audioBytes = 200) to 1_000L,
                row(videoItag = 136, videoBytes = 800, audioItag = 251, audioBytes = null) to null,
                row(videoItag = 136, videoBytes = null) to null,
                row() to null,
            )

        for ((download, total) in cases) {
            val status =
                repository(FakeDownloadDao(listOf(download)), backgroundScope)
                    .status(VIDEO.id)
                    .first()
            assertEquals(DownloadStatus.Waiting(DownloadProgress(0, total)), status)
        }
    }

    @Test
    fun `statuses map every row by its video`() = runTest {
        val rows =
            listOf(
                row("aaaaaaaaaaa", DownloadState.WAITING),
                row("bbbbbbbbbbb", DownloadState.DOWNLOADED, downloadedBytes = 9),
            )

        assertEquals(
            mapOf(
                VideoId("aaaaaaaaaaa") to DownloadStatus.Waiting(DownloadProgress(0, null)),
                VideoId("bbbbbbbbbbb") to DownloadStatus.Downloaded(9),
            ),
            repository(FakeDownloadDao(rows), backgroundScope).statuses().first(),
        )
    }
}
