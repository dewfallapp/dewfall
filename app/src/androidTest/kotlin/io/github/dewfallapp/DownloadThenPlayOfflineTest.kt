package io.github.dewfallapp

import android.app.job.JobScheduler
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.download.DownloadFiles
import io.github.dewfallapp.core.data.download.DownloadsRepository
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.core.ui.R as CoreUiR
import io.github.dewfallapp.fake.AssetMedia
import io.github.dewfallapp.fake.FakeNetwork
import io.github.dewfallapp.fake.StreamLinkCalls
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import io.github.dewfallapp.feature.youtubesearch.ui.R as SearchR
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

/** How far the stream must have played before the test pauses it, so it surely streamed. */
private const val STREAMED_MS = 1_000L

/** How far the video must play on from where it started, well short of its 30 seconds. */
private const val PLAYED_MS = 3_000L

/**
 * #58, the whole of Phase 6 as a user meets it: a video downloaded from its screen, then played
 * with the network off. The video streams, the test pauses it and taps the video screen's
 * "Download" chip, and the real job and runner download it through the fakes until the chip says
 * "Downloaded". The back gesture minimizes it, and the mini player's "Close player" lets go of the
 * stream, as the player spec's "A download that finishes while the video streams" asks before the
 * files can play. With FakeNetwork off, the test opens the video from the Downloads screen, which
 * reads only the phone. It plays from the files, its position moving 3 seconds past where it
 * started, with no call for its stream links and no request for its bytes. The details can't load
 * with the network off, so the description never shows. Where it starts is ResumeTest's check.
 * PlayFromFilesTest plays a download offline that was never streamed, opened from Search YouTube.
 * See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadThenPlayOfflineTest {
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

    @Inject lateinit var links: StreamLinkCalls

    @Inject lateinit var playback: PlaybackConnection

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Starts with no download folders, which a debug build used by hand may have left. */
    @BeforeTest
    fun setUp() {
        hilt.inject()
        files.root.deleteRecursively()
    }

    /** So the next test starts with no playback, no job and no files. */
    @AfterTest
    fun cleanUp() {
        compose.stopPlayback(playback)
        context.getSystemService(JobScheduler::class.java).cancelAll()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        files.root.deleteRecursively()
    }

    @Test
    fun aVideoDownloadedFromItsScreenPlaysOffline() {
        // The video streams, so its links and both files were asked for.
        openTestVideo()
        compose.waitUntil(PLAYER_TIMEOUT_MS) { playsPast(STREAMED_MS) }
        assertTrue(links.countFor(TestVideo.id) > 0, "The links weren't counted")
        assertTrue(media.requestsFor(TestVideo.VIDEO_URL) > 0, "The picture didn't stream")
        assertTrue(media.requestsFor(TestVideo.AUDIO_URL) > 0, "The sound didn't stream")
        // Paused, so it can't reach its end however long the download takes.
        compose.runOnUiThread { playback.player.value?.pause() }

        val download = context.getString(CoreUiR.string.core_ui_download)
        val downloaded = context.getString(CoreUiR.string.core_ui_downloaded)
        compose.onNodeWithText(download).performClick()
        waitForNode(hasText(downloaded), DOWNLOAD_TIMEOUT_MS)
        assertIs<DownloadStatus.Downloaded>(status())

        // The player lets go of the stream before the network goes off, so the held stream can't
        // fail and leave its failure behind.
        Espresso.pressBack()
        val close = context.getString(PlayerR.string.mini_player_close)
        waitForNode(hasContentDescription(close))
        compose.onNodeWithContentDescription(close).performClick()
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.runOnUiThread { playback.player.value?.currentMediaItem == null }
        }

        network.setReachable(false)
        compose
            .onNodeWithContentDescription(context.getString(SearchR.string.youtube_search_back))
            .performClick()
        val downloadsLabel = context.getString(R.string.navigation_downloads)
        waitForNode(hasText(downloadsLabel) and isSelectable())
        compose.onNode(hasText(downloadsLabel) and isSelectable()).performClick()
        waitForNode(hasText(TestVideo.TITLE) and hasClickAction())

        // Counted this late, so a load of the closed stream can't land after them.
        val linksBefore = links.countFor(TestVideo.id)
        val bytesBefore = attempts()
        compose.onNode(hasText(TestVideo.TITLE) and hasClickAction()).performClick()

        compose.waitUntil(PLAYER_TIMEOUT_MS) { playsPast(positionMs = 0) }
        val startedAt = compose.runOnUiThread { playback.player.value?.currentPosition } ?: 0L
        compose.waitUntil(PLAYER_TIMEOUT_MS) { playsPast(startedAt + PLAYED_MS) }
        // The details couldn't load, so the app ran offline. Saved details, #262, have no
        // description either.
        compose.onNode(hasText(TestVideo.DESCRIPTION)).assertDoesNotExist()
        assertEquals(linksBefore, links.countFor(TestVideo.id), "The player asked for links")
        assertEquals(bytesBefore, attempts(), "The player asked for the video's bytes")
        assertIs<DownloadStatus.Downloaded>(status())
    }

    /** Opens the test video's screen from Search YouTube, and waits for its details. */
    private fun openTestVideo() {
        compose.openSearchFromInbox()
        // Search YouTube has one text field.
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
        compose.onNodeWithText(TestVideo.TITLE).performClick()
        waitForNode(hasText(TestVideo.DESCRIPTION))
    }

    private fun status(): DownloadStatus = runBlocking { downloads.status(TestVideo.id).first() }

    /** The requests that AssetMedia got for the picture and the sound, answered or failed. */
    private fun attempts(): Pair<Int, Int> =
        media.attemptsFor(TestVideo.VIDEO_URL) to media.attemptsFor(TestVideo.AUDIO_URL)

    /**
     * Waits until a node matches [matcher], for up to [timeoutMs]. Compose's own
     * waitUntilAtLeastOneExists does the same, but is still an experimental API.
     */
    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = SCREEN_TIMEOUT_MS) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Whether the player plays the test video past [positionMs]. A failure ends the test at once,
     * instead of after the timeout.
     */
    private fun playsPast(positionMs: Long): Boolean = compose.runOnUiThread {
        playback.failure.value?.let { fail("Playback failed: $it") }
        val player = playback.player.value ?: return@runOnUiThread false
        val videoId: VideoId? = player.currentMediaItem?.mediaId?.let(playback::videoOf)
        videoId == TestVideo.id && player.isPlaying && player.currentPosition >= positionMs
    }
}
