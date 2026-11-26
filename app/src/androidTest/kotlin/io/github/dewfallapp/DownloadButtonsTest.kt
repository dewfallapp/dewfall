package io.github.dewfallapp

import android.app.job.JobScheduler
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasNoClickAction
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
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.data.subscriptions.UploadsChecker
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.core.ui.R as CoreUiR
import io.github.dewfallapp.fake.FakeNetwork
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/** How long a download of the test video may take on a cold emulator, as in DownloadTest. */
private const val DOWNLOAD_TIMEOUT_MS = 60_000L

/**
 * The download buttons end to end, through the real job, the real runner and an in-memory database,
 * with the fake YouTube layer and its asset media: a tap on an inbox row's download button, which
 * leaves the video closed and ends with the row's check mark, and a tap on the video screen's chip,
 * which ends with "Downloaded". See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadButtonsTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var subscriptions: SubscriptionsRepository

    @Inject lateinit var checker: UploadsChecker

    @Inject lateinit var files: DownloadFiles

    @Inject lateinit var playback: PlaybackConnection

    @Inject lateinit var network: FakeNetwork

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
    fun aRowsDownloadButtonDownloadsWithoutOpeningTheVideo() {
        runBlocking { subscriptions.subscribe(TestVideo.uploads.channel) }
        checker.start()
        waitForNode(hasText(TestVideo.TITLE))

        val download = context.getString(CoreUiR.string.core_ui_download)
        val downloaded = context.getString(CoreUiR.string.core_ui_downloaded)
        // What a screen reader reads: the row's text as one node, and the button as its own.
        compose
            .onNode(hasText(TestVideo.TITLE))
            .assert(hasClickAction())
            .assert(!hasContentDescription(download))
        compose
            .onNode(hasContentDescription(download) and hasAnyAncestor(hasText(TestVideo.TITLE)))
            .assert(hasClickAction())
            .performClick()

        waitForNode(hasContentDescription(downloaded), DOWNLOAD_TIMEOUT_MS)
        // The check mark is its own node, which isn't a button.
        compose.onNode(hasContentDescription(downloaded)).assert(hasNoClickAction())
        compose.onNode(hasText(TestVideo.TITLE)).assert(!hasContentDescription(downloaded))
        // The tap didn't open the video screen.
        compose
            .onNode(hasContentDescription(context.getString(PlayerR.string.player_minimize)))
            .assertDoesNotExist()
    }

    @Test
    fun theVideoScreensChipDownloadsTheVideo() {
        openTestVideo()

        val download = context.getString(CoreUiR.string.core_ui_download)
        val downloaded = context.getString(CoreUiR.string.core_ui_downloaded)
        waitForNode(hasText(download))
        compose.onNodeWithText(download).assert(hasClickAction()).performClick()

        waitForNode(hasText(downloaded), DOWNLOAD_TIMEOUT_MS)
        // Selected, and no action for a screen reader to announce.
        compose.onNodeWithText(downloaded).assert(hasNoClickAction()).assertIsSelected()
    }

    @Test
    fun theWaitingChipHasNoAction() {
        openTestVideo()
        val download = context.getString(CoreUiR.string.core_ui_download)
        val waiting = context.getString(CoreUiR.string.core_ui_download_waiting)
        waitForNode(hasText(download))
        // With the media unreachable, the download waits for a connection.
        network.mediaReachable = false
        try {
            compose.onNodeWithText(download).performClick()

            waitForNode(hasText(waiting), DOWNLOAD_TIMEOUT_MS)
            compose.onNodeWithText(waiting).assert(hasNoClickAction())
        } finally {
            network.mediaReachable = true
        }
    }

    /** Opens the test video's screen from Search YouTube. */
    private fun openTestVideo() {
        compose.openSearchFromInbox()
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
        compose.onNodeWithText(TestVideo.TITLE).performClick()
    }

    /** Waits until a node matches [matcher], for up to [timeoutMs]. */
    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = SCREEN_TIMEOUT_MS) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
