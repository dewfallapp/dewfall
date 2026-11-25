package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.data.database.FixedClock
import io.github.dewfallapp.core.database.DownloadEntity
import io.github.dewfallapp.core.database.DownloadQueueDao
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

private val NOW = Instant.parse("2026-11-26T12:00:00Z")

/** Answers claimNext with [next] and queuedStates with [states], and records the writes. */
private class FakeDownloadQueueDao(
    private val next: DownloadEntity? = null,
    private val states: List<DownloadState> = emptyList(),
) : DownloadQueueDao() {
    val calls = mutableListOf<String>()

    override suspend fun queuedStates() = states

    override suspend fun claimNext(now: Instant) = next

    override suspend fun setFormats(
        videoId: String,
        videoItag: Int,
        videoMime: String,
        videoBytes: Long?,
        audioItag: Int?,
        audioMime: String?,
        audioBytes: Long?,
        audioTrack: String?,
        now: Instant,
    ): Int {
        calls +=
            "formats $videoId $videoItag $videoMime $videoBytes $audioItag $audioMime " +
                "$audioBytes $audioTrack $now"
        return 1
    }

    override suspend fun fail(
        videoId: String,
        failure: DownloadFailure,
        keepBytes: Boolean,
        now: Instant,
    ): Int {
        calls += "fail $videoId $failure $keepBytes $now"
        return 0
    }

    override suspend fun resetDownloading(now: Instant) = error("Not used")

    override suspend fun nextWaiting() = error("Not used")

    override suspend fun markDownloading(videoId: String, now: Instant) = error("Not used")

    override suspend fun clearFormats(videoId: String, now: Instant) = error("Not used")

    override suspend fun setProgress(videoId: String, bytes: Long, now: Instant) = error("Not used")

    override suspend fun backToWaiting(videoId: String, youtubeTries: Int, now: Instant) =
        error("Not used")

    override suspend fun finish(videoId: String, bytes: Long, now: Instant) = error("Not used")
}

class RoomDownloadQueueTest {
    private fun queue(dao: FakeDownloadQueueDao, scope: CoroutineScope) =
        RoomDownloadQueue(dao, FakeDownloadDao(), FixedClock(NOW), scope)

    @Test
    fun `a claimed row with a video and an audio format keeps both, with the track`() = runTest {
        val row =
            row(
                    state = DownloadState.DOWNLOADING,
                    videoItag = 136,
                    videoBytes = 800,
                    audioItag = 251,
                    audioBytes = 200,
                    downloadedBytes = 300,
                )
                .copy(audioTrack = "en.4", youtubeTries = 2)

        val claimed = queue(FakeDownloadQueueDao(next = row), backgroundScope).claimNext()!!

        assertEquals(VideoId("aaaaaaaaaaa"), claimed.videoId)
        assertEquals("folder-1", claimed.folder)
        assertEquals(
            ChosenFormats(
                ChosenFormat(136, "video/mp4", 800),
                ChosenFormat(251, "audio/webm", 200, track = "en.4"),
            ),
            claimed.chosen,
        )
        assertEquals(300, claimed.downloadedBytes)
        assertEquals(2, claimed.youtubeTries)
    }

    @Test
    fun `a claimed row with one format that has both keeps no audio`() = runTest {
        val row = row(state = DownloadState.DOWNLOADING, videoItag = 18, videoBytes = 500)

        val claimed = queue(FakeDownloadQueueDao(next = row), backgroundScope).claimNext()!!

        assertEquals(
            ChosenFormats(ChosenFormat(18, "video/mp4", 500), audio = null),
            claimed.chosen,
        )
    }

    @Test
    fun `a claimed row with no formats yet has none`() = runTest {
        val claimed =
            queue(
                    FakeDownloadQueueDao(next = row(state = DownloadState.DOWNLOADING)),
                    backgroundScope,
                )
                .claimNext()!!

        assertNull(claimed.chosen)
    }

    @Test
    fun `no waiting row claims nothing`() = runTest {
        assertNull(queue(FakeDownloadQueueDao(), backgroundScope).claimNext())
    }

    @Test
    fun `formats are written with the audio track, and a write says whether it changed a row`() =
        runTest {
            val dao = FakeDownloadQueueDao()
            val queue = queue(dao, backgroundScope)
            val chosen =
                ChosenFormats(
                    ChosenFormat(136, "video/mp4", 800),
                    ChosenFormat(251, "audio/webm", null, track = "en.4"),
                )

            assertEquals(true, queue.setFormats(VideoId("aaaaaaaaaaa"), chosen))
            assertEquals(
                false,
                queue.fail(VideoId("aaaaaaaaaaa"), DownloadFailure.NO_SPACE, keepBytes = true),
            )

            assertEquals(
                listOf(
                    "formats aaaaaaaaaaa 136 video/mp4 800 251 audio/webm null en.4 $NOW",
                    "fail aaaaaaaaaaa NO_SPACE true $NOW",
                ),
                dao.calls,
            )
        }

    @Test
    fun `queued states come through, and the checks read them`() = runTest {
        val queue =
            queue(
                FakeDownloadQueueDao(
                    states = listOf(DownloadState.WAITING, DownloadState.DOWNLOADING),
                ),
                backgroundScope,
            )

        assertEquals(listOf(QueueState.WAITING, QueueState.DOWNLOADING), queue.queuedStates())
        assertEquals(true, queue.hasQueued())
        assertEquals(true, queue.hasDownloading())
        val idle = queue(FakeDownloadQueueDao(states = emptyList()), backgroundScope)
        assertEquals(false, idle.hasQueued())
        assertEquals(false, idle.hasDownloading())
    }
}
