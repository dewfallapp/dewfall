package io.github.dewfallapp

import android.app.job.JobScheduler
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.download.DownloadFiles
import io.github.dewfallapp.core.data.download.DownloadsRepository
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.fake.FakeNetwork
import io.github.dewfallapp.fake.TestVideo
import java.io.File
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.runner.RunWith

/** How long a download of the test video may take on a cold emulator. */
private const val DOWNLOAD_TIMEOUT_MS = 60_000L

/**
 * How long a paused download may take to finish after the second ask, which schedules a run at
 * once, replacing any pending backoff, with room for a slow emulator.
 */
private const val RETRY_TIMEOUT_MS = 180_000L

/**
 * The download engine end to end, through the real job, the real runner and an in-memory database,
 * with the fake YouTube layer and its asset media: on Android 14 and later the user-initiated data
 * transfer job, and below it the WorkManager worker. MainActivity shows first, so the app is
 * visible when the download is scheduled, as a UIDT job needs. Each wait reads the download's
 * status until it is what the test expects, with a generous timeout, never a fixed sleep. See
 * docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var downloads: DownloadsRepository

    @Inject lateinit var files: DownloadFiles

    @Inject lateinit var network: FakeNetwork

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Starts with no download folders, which a debug build used by hand may have left. */
    @BeforeTest
    fun inject() {
        hilt.inject()
        files.root.deleteRecursively()
    }

    /** So the next test starts with no job and no files. */
    @AfterTest
    fun cleanUp() {
        context.getSystemService(JobScheduler::class.java).cancelAll()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        files.root.deleteRecursively()
    }

    private suspend fun awaitStatus(timeoutMs: Long, matches: (DownloadStatus) -> Boolean) =
        withTimeout(timeoutMs) { downloads.status(TestVideo.id).first(matches) }

    private fun asset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use {
            it.readBytes()
        }

    /** The one download folder's files, by name. */
    private fun downloadedFiles(): Map<String, File> {
        val folders = files.root.listFiles().orEmpty().toList()
        assertEquals(1, folders.size)
        return folders.single().listFiles().orEmpty().associateBy { it.name }
    }

    @Test
    fun aDownloadKeepsBothFilesByteForByte() = runBlocking {
        downloads.download(TestVideo.summary)

        val done = awaitStatus(DOWNLOAD_TIMEOUT_MS) { it is DownloadStatus.Downloaded }

        val kept = downloadedFiles()
        assertEquals(setOf("video.mp4", "audio.m4a"), kept.keys)
        assertContentEquals(asset(TestVideo.VIDEO_ASSET), kept.getValue("video.mp4").readBytes())
        assertContentEquals(asset(TestVideo.AUDIO_ASSET), kept.getValue("audio.m4a").readBytes())
        assertEquals(
            kept.values.sumOf { it.length() },
            assertIs<DownloadStatus.Downloaded>(done).bytes,
        )
    }

    @Test
    fun aPausedDownloadGoesOnWhenAskedAgain() = runBlocking {
        // With no connection, the download waits. Its run may already have asked to run again
        // later, or not have claimed it yet: either way, the pause holds and the second ask goes
        // on.
        network.setReachable(false)
        downloads.download(TestVideo.summary)
        awaitStatus(DOWNLOAD_TIMEOUT_MS) { it is DownloadStatus.Waiting }

        downloads.pause(TestVideo.id)
        awaitStatus(DOWNLOAD_TIMEOUT_MS) { it is DownloadStatus.Paused }
        network.setReachable(true)
        downloads.download(TestVideo.summary)

        awaitStatus(RETRY_TIMEOUT_MS) { it is DownloadStatus.Downloaded }
        assertContentEquals(
            asset(TestVideo.VIDEO_ASSET),
            downloadedFiles().getValue("video.mp4").readBytes(),
        )
    }
}
