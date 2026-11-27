package io.github.dewfallapp

import android.app.job.JobScheduler
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.github.dewfallapp.core.data.download.DownloadFiles
import io.github.dewfallapp.core.data.download.DownloadScheduler
import io.github.dewfallapp.core.database.DownloadDao
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.download.DownloadSchedulerModule
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.core.ui.R as CoreUiR
import io.github.dewfallapp.fake.HeldScheduler
import io.github.dewfallapp.fake.TestVideo
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * The video screen's "Waiting to download" chip, which isn't a button. A tap on the chip's Download
 * writes the row as WAITING, and the real job would then claim it, mark it DOWNLOADING and, with no
 * media, put it back to WAITING, so a check of the chip could read it while it said "Downloading",
 * the failure that #268 reports. Here HeldScheduler replaces DownloadSchedulerModule's scheduler
 * and starts no run, so nothing claims the row, and it stays WAITING for the whole check. It has
 * its own class, because `@UninstallModules` applies to every test in a class, and
 * DownloadButtonsTest's tests need the real job. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@UninstallModules(DownloadSchedulerModule::class)
@RunWith(AndroidJUnit4::class)
class WaitingChipTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    private val held = HeldScheduler()

    /** Hilt binds the field's declared type, so it is DownloadScheduler, not HeldScheduler. */
    @BindValue @JvmField val scheduler: DownloadScheduler = held

    @Inject lateinit var files: DownloadFiles

    @Inject lateinit var playback: PlaybackConnection

    @Inject lateinit var downloadDao: DownloadDao

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Starts with no download folders, which a debug build used by hand may have left, and with no
     * job or work, so a job that an earlier test left can't claim this test's row.
     */
    @BeforeTest
    fun setUp() {
        hilt.inject()
        cancelRuns()
        files.root.deleteRecursively()
    }

    /** So the next test starts with no playback, no job and no files. */
    @AfterTest
    fun cleanUp() {
        compose.stopPlayback(playback)
        cancelRuns()
        files.root.deleteRecursively()
    }

    @Test
    fun theWaitingChipHasNoAction() {
        openTestVideo()
        val download = context.getString(CoreUiR.string.core_ui_download)
        val waiting = context.getString(CoreUiR.string.core_ui_download_waiting)
        waitForNode(hasText(download))
        // Read just before the tap, because MainActivity's onStart asked for a run too.
        val before = held.starts
        compose.onNodeWithText(download).performClick()

        waitForNode(hasText(waiting))
        compose.onNodeWithText(waiting).assert(hasNoClickAction())
        // Nothing claimed the row, so the chip above showed a waiting download.
        val row = runBlocking { downloadDao.row(TestVideo.id.value) }
        assertEquals(DownloadState.WAITING, row?.state)
        // The tap's write asked for a run, as it does with the real scheduler. The repository asks
        // after the write, in the app's scope, so the chip can show first.
        compose.waitUntil(SCREEN_TIMEOUT_MS) { held.starts > before }
    }

    /** Opens the test video's screen from Search YouTube. */
    private fun openTestVideo() {
        compose.openSearchFromInbox()
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
        compose.onNodeWithText(TestVideo.TITLE).performClick()
    }

    /** Cancels every job and every WorkManager work. */
    private fun cancelRuns() {
        context.getSystemService(JobScheduler::class.java).cancelAll()
        WorkManager.getInstance(context).cancelAllWork().result.get()
    }

    /** Waits until a node matches [matcher], for up to [timeoutMs]. */
    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = SCREEN_TIMEOUT_MS) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
