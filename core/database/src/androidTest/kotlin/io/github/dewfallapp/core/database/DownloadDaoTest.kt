package io.github.dewfallapp.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.VideoKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/** A waiting download of the video [videoId], asked for at [at]. */
private fun waiting(videoId: String, at: Instant = MONDAY) =
    DownloadEntity(
        videoId = videoId,
        folder = "f-$videoId",
        title = "A video",
        channelId = CHANNEL_A,
        channelName = "Kettle and Crumb",
        durationMs = 600_000,
        publishedAt = MONDAY,
        publishedApproximate = false,
        kind = VideoKind.VIDEO,
        thumbnails = THUMBNAILS,
        state = DownloadState.WAITING,
        failure = null,
        requestedAt = at,
        videoItag = null,
        videoMime = null,
        videoBytes = null,
        audioItag = null,
        audioMime = null,
        audioBytes = null,
        audioTrack = null,
        downloadedBytes = 0,
        youtubeTries = 0,
        updatedAt = at,
    )

/** The downloads spec's queue, as the table keeps it. */
@RunWith(AndroidJUnit4::class)
class DownloadDaoTest {
    private val database = inMemoryDatabase()
    private val downloads = database.downloadDao()
    private val queue = database.downloadQueueDao()

    @AfterTest fun closeDatabase() = database.close()

    private suspend fun stateOf(n: Int) = downloads.row(videoId(n))?.state

    @Test
    fun theQueueRunsInTheOrderAsked() = runTest {
        downloads.request(waiting(videoId(2), TUESDAY))
        downloads.request(waiting(videoId(1), MONDAY))
        downloads.request(waiting(videoId(3), WEDNESDAY))

        assertEquals(videoId(1), queue.claimNext(WEDNESDAY)?.videoId)
        assertEquals(videoId(2), queue.claimNext(WEDNESDAY)?.videoId)
        assertEquals(videoId(3), queue.claimNext(WEDNESDAY)?.videoId)
        assertNull(queue.claimNext(WEDNESDAY))
    }

    @Test
    fun claimingMarksTheRowRunning() = runTest {
        downloads.request(waiting(videoId(1)))

        queue.claimNext(TUESDAY)

        assertEquals(DownloadState.DOWNLOADING, stateOf(1))
        assertEquals(listOf(DownloadState.DOWNLOADING), queue.queuedStates())
    }

    @Test
    fun askingAgainForAWaitingRunningOrFinishedDownloadChangesNothing() = runTest {
        assertTrue(downloads.request(waiting(videoId(1), MONDAY)))
        assertFalse(downloads.request(waiting(videoId(1), TUESDAY)))
        assertEquals(MONDAY, downloads.row(videoId(1))?.requestedAt)

        queue.claimNext(MONDAY)
        assertFalse(downloads.request(waiting(videoId(1), TUESDAY)))
        assertEquals(DownloadState.DOWNLOADING, stateOf(1))

        queue.finish(videoId(1), bytes = 500, TUESDAY)
        assertFalse(downloads.request(waiting(videoId(1), WEDNESDAY)))
        assertEquals(DownloadState.DOWNLOADED, stateOf(1))
    }

    @Test
    fun aPausedDownloadGoesBackToTheEndOfTheQueueWithItsBytes() = runTest {
        downloads.request(waiting(videoId(1), MONDAY))
        downloads.request(waiting(videoId(2), TUESDAY))
        queue.claimNext(TUESDAY)
        queue.setFormats(videoId(1), 136, "video/mp4", 900, 251, "audio/webm", 100, "en.4", TUESDAY)
        queue.setProgress(videoId(1), 400, TUESDAY)
        downloads.pause(videoId(1), TUESDAY)

        assertTrue(downloads.request(waiting(videoId(1), WEDNESDAY)))

        val row = downloads.row(videoId(1))!!
        assertEquals(DownloadState.WAITING, row.state)
        assertEquals(400, row.downloadedBytes)
        assertEquals(136, row.videoItag)
        assertEquals(videoId(2), queue.claimNext(WEDNESDAY)?.videoId)
    }

    @Test
    fun aFailedDownloadGoesOnWithItsFailureAndTriesCleared() = runTest {
        downloads.request(waiting(videoId(1)))
        queue.claimNext(MONDAY)
        queue.backToWaiting(videoId(1), youtubeTries = 2, MONDAY)
        queue.claimNext(MONDAY)
        queue.fail(videoId(1), DownloadFailure.YOUTUBE, keepBytes = true, MONDAY)

        assertTrue(downloads.request(waiting(videoId(1), TUESDAY)))

        val row = downloads.row(videoId(1))!!
        assertEquals(DownloadState.WAITING, row.state)
        assertNull(row.failure)
        assertEquals(0, row.youtubeTries)
    }

    @Test
    fun aRunsWritesNeverUndoAPause() = runTest {
        downloads.request(waiting(videoId(1)))
        queue.claimNext(MONDAY)
        queue.setProgress(videoId(1), 100, MONDAY)

        downloads.pause(videoId(1), TUESDAY)

        assertEquals(0, queue.setProgress(videoId(1), 200, TUESDAY))
        assertEquals(0, queue.backToWaiting(videoId(1), youtubeTries = 1, TUESDAY))
        assertEquals(0, queue.fail(videoId(1), DownloadFailure.NO_SPACE, keepBytes = true, TUESDAY))
        assertEquals(0, queue.finish(videoId(1), bytes = 1_000, TUESDAY))
        val row = downloads.row(videoId(1))!!
        assertEquals(DownloadState.PAUSED, row.state)
        assertEquals(100, row.downloadedBytes)
    }

    @Test
    fun aRunLeftOverByAnEndedProcessWaitsAgain() = runTest {
        downloads.request(waiting(videoId(1)))
        queue.claimNext(MONDAY)

        assertEquals(1, queue.resetDownloading(TUESDAY))

        assertEquals(DownloadState.WAITING, stateOf(1))
        assertEquals(listOf(DownloadState.WAITING), queue.queuedStates())
    }

    @Test
    fun aStopByTheUserPausesEveryWaitingAndRunningDownload() = runTest {
        downloads.request(waiting(videoId(1), MONDAY))
        downloads.request(waiting(videoId(2), TUESDAY))
        downloads.request(waiting(videoId(3), WEDNESDAY))
        queue.claimNext(WEDNESDAY)
        queue.claimNext(WEDNESDAY)
        queue.finish(videoId(2), bytes = 10, WEDNESDAY)

        assertEquals(2, downloads.pauseQueued(WEDNESDAY))

        assertEquals(DownloadState.PAUSED, stateOf(1))
        assertEquals(DownloadState.DOWNLOADED, stateOf(2))
        assertEquals(DownloadState.PAUSED, stateOf(3))
        assertTrue(queue.queuedStates().isEmpty())
    }

    @Test
    fun failingWithoutKeepingForgetsTheFormatsAndBytes() = runTest {
        downloads.request(waiting(videoId(1)))
        queue.claimNext(MONDAY)
        queue.setFormats(videoId(1), 136, "video/mp4", 900, 251, "audio/webm", 100, "en.4", MONDAY)
        queue.setProgress(videoId(1), 300, MONDAY)

        queue.fail(videoId(1), DownloadFailure.UNAVAILABLE, keepBytes = false, TUESDAY)

        val row = downloads.row(videoId(1))!!
        assertEquals(DownloadState.FAILED, row.state)
        assertEquals(DownloadFailure.UNAVAILABLE, row.failure)
        assertNull(row.videoItag)
        assertNull(row.audioTrack)
        assertEquals(0, row.downloadedBytes)
    }

    @Test
    fun failingWithKeptBytesKeepsTheFormatsAndBytes() = runTest {
        downloads.request(waiting(videoId(1)))
        queue.claimNext(MONDAY)
        queue.setFormats(videoId(1), 136, "video/mp4", 900, 251, "audio/webm", 100, "en.4", MONDAY)
        queue.setProgress(videoId(1), 300, MONDAY)

        queue.fail(videoId(1), DownloadFailure.NO_SPACE, keepBytes = true, TUESDAY)

        val row = downloads.row(videoId(1))!!
        assertEquals(DownloadFailure.NO_SPACE, row.failure)
        assertEquals(136, row.videoItag)
        assertEquals(251, row.audioItag)
        assertEquals(100, row.audioBytes)
        assertEquals("en.4", row.audioTrack)
        assertEquals(300, row.downloadedBytes)
    }

    @Test
    fun theDownloadFlowFollowsTheRow() = runTest {
        assertNull(downloads.download(videoId(1)).first()?.state)

        downloads.request(waiting(videoId(1)))
        assertEquals(DownloadState.WAITING, downloads.download(videoId(1)).first()?.state)

        downloads.pause(videoId(1), TUESDAY)
        assertEquals(DownloadState.PAUSED, downloads.download(videoId(1)).first()?.state)
    }
}
