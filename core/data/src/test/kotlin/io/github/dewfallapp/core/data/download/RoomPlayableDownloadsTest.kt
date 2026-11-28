package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.database.DownloadEntity
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.model.VideoId
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private const val ID = "aaaaaaaaaaa"

class RoomPlayableDownloadsTest {
    private val base = Files.createTempDirectory("playable").toFile()
    private val root = File(base, "downloads")
    private val files = DownloadFiles(root)

    /** Removes the test's temporary folder, and fails the test if something in it stays. */
    @AfterTest
    fun removeTemporaryFolder() {
        assertTrue(base.deleteRecursively())
        assertFalse(base.exists())
    }

    /** A finished download of a video file and an audio file, with the sizes it recorded. */
    private fun separate(
        videoBytes: Long? = 10,
        audioBytes: Long? = 5,
        downloadedBytes: Long = 15,
    ): DownloadEntity =
        row(
            videoId = ID,
            state = DownloadState.DOWNLOADED,
            videoItag = 137,
            videoBytes = videoBytes,
            audioItag = 251,
            audioBytes = audioBytes,
            downloadedBytes = downloadedBytes,
        )

    private fun write(file: File, bytes: Int): File {
        checkNotNull(file.parentFile).mkdirs()
        file.writeBytes(ByteArray(bytes))
        return file
    }

    private fun videoFile(folder: String = "folder-1") =
        files.complete(folder, DownloadFileKind.VIDEO, "video/mp4")

    private fun audioFile(folder: String = "folder-1") =
        files.complete(folder, DownloadFileKind.AUDIO, "audio/webm")

    private fun writeBoth() {
        write(videoFile(), 10)
        write(audioFile(), 5)
    }

    private suspend fun TestScope.playable(vararg rows: DownloadEntity): PlayableDownload =
        RoomPlayableDownloads(
                FakeDownloadDao(rows.toList()),
                files,
                StandardTestDispatcher(testScheduler),
            )
            .playable(VideoId(ID))

    @Test
    fun `a finished download's two files play`() = runTest {
        writeBoth()

        assertEquals(PlayableDownload.Separate(videoFile(), audioFile()), playable(separate()))
    }

    @Test
    fun `a finished download's one combined file plays`() = runTest {
        val media = write(files.complete("folder-1", DownloadFileKind.MEDIA, "video/mp4"), 12)
        val row =
            row(
                videoId = ID,
                state = DownloadState.DOWNLOADED,
                videoItag = 18,
                videoBytes = 12,
                downloadedBytes = 12,
            )

        assertEquals(PlayableDownload.Combined(media), playable(row))
    }

    @Test
    fun `a download that isn't done never plays, even with its files there`() = runTest {
        writeBoth()

        for (state in DownloadState.entries - DownloadState.DOWNLOADED) {
            assertEquals(
                PlayableDownload.NotDownloaded,
                playable(separate().copy(state = state)),
                state.name,
            )
        }
    }

    @Test
    fun `a video with no download doesn't play from files`() = runTest {
        assertEquals(PlayableDownload.NotDownloaded, playable())
    }

    @Test
    fun `a missing file streams`() = runTest {
        write(videoFile(), 10)

        assertEquals(PlayableDownload.Incomplete(IncompleteReason.MISSING), playable(separate()))
    }

    @Test
    fun `a folder in a file's place streams`() = runTest {
        write(videoFile(), 10)
        audioFile().mkdirs()

        assertEquals(PlayableDownload.Incomplete(IncompleteReason.MISSING), playable(separate()))
    }

    @Test
    fun `a folder name that leads outside the downloads' folder streams`() = runTest {
        // The files exist and have the right sizes, but outside the root.
        root.mkdirs()
        write(videoFile("../outside"), 10)
        write(audioFile("../outside"), 5)
        assertTrue(videoFile("../outside").isFile)

        assertEquals(
            PlayableDownload.Incomplete(IncompleteReason.MISSING),
            playable(separate().copy(folder = "../outside")),
        )
    }

    @Test
    fun `an empty file streams, even when no size was recorded`() = runTest {
        write(videoFile(), 0)
        write(audioFile(), 5)

        assertEquals(
            PlayableDownload.Incomplete(IncompleteReason.SIZE),
            playable(separate(videoBytes = null, downloadedBytes = 5)),
        )
    }

    @Test
    fun `a file of another length than the recorded size streams`() = runTest {
        write(videoFile(), 9)
        write(audioFile(), 6)

        // The sum still matches, so only the recorded size catches it.
        assertEquals(PlayableDownload.Incomplete(IncompleteReason.SIZE), playable(separate()))
    }

    @Test
    fun `files that don't add up to the bytes on the phone stream`() = runTest {
        writeBoth()

        assertEquals(
            PlayableDownload.Incomplete(IncompleteReason.SIZE),
            playable(separate(videoBytes = null, audioBytes = null, downloadedBytes = 16)),
        )
    }

    @Test
    fun `files play when the download recorded no sizes, if they add up`() = runTest {
        writeBoth()

        assertEquals(
            PlayableDownload.Separate(videoFile(), audioFile()),
            playable(separate(videoBytes = null, audioBytes = null)),
        )
    }

    @Test
    fun `a row without a file's MIME type streams`() = runTest {
        writeBoth()

        assertEquals(
            PlayableDownload.Incomplete(IncompleteReason.MISSING),
            playable(separate().copy(videoMime = null)),
        )
        assertEquals(
            PlayableDownload.Incomplete(IncompleteReason.MISSING),
            playable(separate().copy(audioMime = null)),
        )
    }

    @Test
    fun `a database that throws streams`() = runTest {
        val lookup =
            RoomPlayableDownloads(
                FakeDownloadDao(rowError = IOException("disk")),
                files,
                StandardTestDispatcher(testScheduler),
            )

        assertEquals(PlayableDownload.Unreadable, lookup.playable(VideoId(ID)))
    }

    @Test
    fun `an Error from the database is thrown on, not turned into Unreadable`() = runTest {
        val error = TestError()
        val lookup =
            RoomPlayableDownloads(
                FakeDownloadDao(rowError = error),
                files,
                StandardTestDispatcher(testScheduler),
            )

        val thrown = assertFailsWith<TestError> { lookup.playable(VideoId(ID)) }
        assertSame(error, thrown)
    }

    @Test
    fun `a cancelled lookup ends without an answer`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val lookup =
            RoomPlayableDownloads(
                FakeDownloadDao(listOf(separate()), gate = gate),
                files,
                StandardTestDispatcher(testScheduler),
            )
        var answer: PlayableDownload? = null

        val job = launch { answer = lookup.playable(VideoId(ID)) }
        testScheduler.runCurrent()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertNull(answer)
    }

    @Test
    fun `the answers' text shows no path`() {
        val separate = PlayableDownload.Separate(videoFile(), audioFile())
        val combined = PlayableDownload.Combined(videoFile())

        assertFalse(separate.toString().contains("folder-1"))
        assertFalse(combined.toString().contains("folder-1"))
    }
}

/** An Error that only this test throws. */
private class TestError : Error("test")
