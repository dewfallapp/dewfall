package io.github.dewfallapp

import android.app.job.JobScheduler
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.download.DownloadFiles
import io.github.dewfallapp.core.data.download.DownloadsRepository
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.AssetMedia
import io.github.dewfallapp.fake.FakeNetwork
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import java.io.File
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/** How long a download of the test video may take on a cold emulator, as in DownloadTest. */
private const val DOWNLOAD_TIMEOUT_MS = 60_000L

/**
 * How long the test waits for the player: the service's start and the first frames, on a cold
 * emulator that draws in software, or a failure.
 */
private const val PLAYER_TIMEOUT_MS = 60_000L

/** How far the video must have played, well past its start and short of its 30 seconds. */
private const val PLAYED_MS = 3_000L

/**
 * A downloaded video plays from its files, as openspec/specs/player/spec.md says: offline, with no
 * request for its bytes; a finished download with a file gone streams, and still says Downloaded;
 * and a file deleted while it plays fails on the seek that opens it again, and "Try again" streams
 * it. Each test downloads [TestVideo] through the real job, the real runner and the fakes, as
 * DownloadTest does, then opens it from Search YouTube. #58's test adds the download from the video
 * screen. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlayFromFilesTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason that SearchThenPlayTest gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var downloads: DownloadsRepository

    @Inject lateinit var files: DownloadFiles

    @Inject lateinit var network: FakeNetwork

    @Inject lateinit var media: AssetMedia

    @Inject lateinit var playback: PlaybackConnection

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Starts with no download folders, which a debug build used by hand may have left. */
    @BeforeTest
    fun inject() {
        hilt.inject()
        files.root.deleteRecursively()
    }

    /** So the next test starts the service with its own objects, with no job and no files. */
    @AfterTest
    fun cleanUp() {
        compose.stopPlayback(playback)
        context.getSystemService(JobScheduler::class.java).cancelAll()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        files.root.deleteRecursively()
    }

    @Test
    fun aDownloadedVideoPlaysOfflineFromItsFiles() {
        download()
        val before = attempts()
        showSearchResults()

        network.setReachable(false)
        compose.onNodeWithText(TestVideo.TITLE).performClick()

        // The details fail, as answer 1 of the change chose, while the video plays.
        waitForNode(hasText(compose.activity.getString(PlayerR.string.player_failed_network)))
        compose.waitUntil(PLAYER_TIMEOUT_MS) { playsPast(PLAYED_MS) }
        assertEquals(before, attempts(), "The player asked for the video's bytes")
    }

    @Test
    fun aDownloadWithAFileGoneStreams() {
        download()
        assertTrue(audioFile().delete())
        val before = requests()
        showSearchResults()

        compose.onNodeWithText(TestVideo.TITLE).performClick()

        compose.waitUntil(PLAYER_TIMEOUT_MS) { playsPast(PLAYED_MS) }
        assertTrue(requests().second > before.second, "The sound didn't stream")
        assertIs<DownloadStatus.Downloaded>(status())
    }

    @Test
    fun aFileDeletedWhilePlayingFailsOnASeekAndTryAgainStreams() {
        download()
        val before = requests()
        showSearchResults()
        compose.onNodeWithText(TestVideo.TITLE).performClick()
        compose.waitUntil(PLAYER_TIMEOUT_MS) { playsPast(PLAYED_MS) }
        assertEquals(before, requests(), "The player didn't play from the files")

        // The back buffer is empty, so the seek to the start loads the files again.
        assertTrue(audioFile().delete())
        compose.runOnUiThread { playback.player.value?.seekTo(0) }
        compose.waitUntil(PLAYER_TIMEOUT_MS) { playback.failure.value != null }
        assertEquals(PlaybackFailure.OTHER, playback.failure.value)
        waitForNode(hasText(compose.activity.getString(PlayerR.string.player_failed_stopped)))

        compose
            .onNodeWithText(compose.activity.getString(PlayerR.string.player_try_again))
            .performClick()
        // The failure stays until the new item plays, so this wait doesn't end on it.
        compose.waitUntil(PLAYER_TIMEOUT_MS) { playsPast(positionMs = 1, failFast = false) }
        assertTrue(requests().second > before.second, "The sound didn't stream")
    }

    /** Downloads [TestVideo] and waits until it is Downloaded. */
    private fun download() = runBlocking {
        downloads.download(TestVideo.summary)
        withTimeout(DOWNLOAD_TIMEOUT_MS) {
            downloads.status(TestVideo.id).first { it is DownloadStatus.Downloaded }
        }
    }

    private fun status(): DownloadStatus = runBlocking { downloads.status(TestVideo.id).first() }

    /** The one download folder's audio file. */
    private fun audioFile(): File {
        val folders = files.root.listFiles().orEmpty().toList()
        assertEquals(1, folders.size)
        return File(folders.single(), "audio.m4a")
    }

    /** The requests that AssetMedia got for the picture and the sound, answered or failed. */
    private fun attempts(): Pair<Int, Int> =
        media.attemptsFor(TestVideo.VIDEO_URL) to media.attemptsFor(TestVideo.AUDIO_URL)

    /** The requests that AssetMedia answered for the picture and the sound. */
    private fun requests(): Pair<Int, Int> =
        media.requestsFor(TestVideo.VIDEO_URL) to media.requestsFor(TestVideo.AUDIO_URL)

    /** Searches for the test video, with the network on, until its row shows. */
    private fun showSearchResults() {
        compose.openSearchFromInbox()
        // Search YouTube has one text field.
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
    }

    /**
     * Waits until a node matches [matcher], for up to [SCREEN_TIMEOUT_MS]. Compose's own
     * waitUntilAtLeastOneExists does the same, but is still an experimental API.
     */
    private fun waitForNode(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Whether the player plays the test video past [positionMs]. A failure ends the test at once,
     * instead of after the timeout, unless [failFast] is off.
     */
    private fun playsPast(positionMs: Long, failFast: Boolean = true): Boolean =
        compose.runOnUiThread {
            if (failFast) playback.failure.value?.let { fail("Playback failed: $it") }
            val player = playback.player.value ?: return@runOnUiThread false
            val videoId: VideoId? = player.currentMediaItem?.mediaId?.let(playback::videoOf)
            videoId == TestVideo.id && player.isPlaying && player.currentPosition >= positionMs
        }
}
