package io.github.dewfallapp

import android.app.job.JobScheduler
import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.download.DownloadFiles
import io.github.dewfallapp.core.database.DownloadDao
import io.github.dewfallapp.core.database.DownloadEntity
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.downloads.ui.R as DownloadsR
import io.github.dewfallapp.feature.youtubesearch.ui.R as SearchR
import java.io.File
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * The Downloads screen with the real repository, the real job and an in-memory database: the
 * navigation bar's Downloads destination, a row that opens its video, Search YouTube from it, and
 * Cancel and Remove download, which delete the download's folder. Each download is written to the
 * database as it would stand, with a folder of its own, so no test waits for a real download. See
 * docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadsScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var downloadDao: DownloadDao

    @Inject lateinit var files: DownloadFiles

    @Inject lateinit var playback: PlaybackConnection

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val inboxLabel = context.getString(R.string.navigation_inbox)
    private val downloadsLabel = context.getString(R.string.navigation_downloads)
    private val remove = context.getString(DownloadsR.string.downloads_remove)
    private val removed = context.getString(DownloadsR.string.downloads_removed)
    private val cancel = context.getString(DownloadsR.string.downloads_cancel)
    private val emptyTitle = context.getString(DownloadsR.string.downloads_empty_title)
    private val searchButton = context.getString(DownloadsR.string.downloads_search)
    private val searchBack = context.getString(SearchR.string.youtube_search_back)

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
    fun theBarOpensDownloadsAndBackReturnsToTheInbox() {
        openDownloads()
        waitFor(hasText(emptyTitle))

        Espresso.pressBack()

        waitFor(hasText(inboxLabel) and isSelectable())
        bar(inboxLabel).assertIsSelected()
    }

    @Test
    fun removeDownloadDeletesTheFilesAndSaysSo() {
        val folder = keep(TestVideo.summary, DownloadState.DOWNLOADED)
        openDownloads()
        waitFor(hasText(TestVideo.TITLE))

        compose.onNode(hasText(remove) and hasClickAction()).performClick()

        waitFor(hasText(removed))
        waitForNo(hasText(TestVideo.TITLE))
        assertFalse(folder.exists(), "The removed download's folder is still there")
        assertNull(runBlocking { downloadDao.row(TestVideo.id.value) })
    }

    @Test
    fun cancelOnAPausedDownloadDeletesItsFilesWithoutAMessage() {
        val folder = keep(TestVideo.otherSummary, DownloadState.PAUSED)
        openDownloads()
        waitFor(hasText(TestVideo.OTHER_TITLE))

        compose.onNode(hasText(cancel) and hasClickAction()).performClick()

        waitForNo(hasText(TestVideo.OTHER_TITLE))
        assertFalse(folder.exists(), "The cancelled download's folder is still there")
        compose.onNode(hasText(removed)).assertDoesNotExist()
    }

    @Test
    fun aRowReadsAsOneItemAndOpensItsVideo() {
        keep(TestVideo.summary, DownloadState.DOWNLOADED)
        openDownloads()
        waitFor(hasText(TestVideo.TITLE))

        // What a screen reader reads: the row's text as one node, and its button as its own.
        compose
            .onNode(hasText(TestVideo.TITLE))
            .assert(hasClickAction())
            .assert(hasText(TestVideo.CHANNEL_NAME, substring = true))
            .assert(!hasText(remove, substring = true))
        compose.onNode(hasText(remove)).assert(hasClickAction()).assert(!hasText(TestVideo.TITLE))

        compose.onNode(hasText(TestVideo.TITLE)).performClick()
        waitFor(hasText(TestVideo.DESCRIPTION))

        Espresso.pressBack()
        waitFor(hasText(remove))
        bar(downloadsLabel).assertIsSelected()
    }

    @Test
    fun searchFromDownloadsGoesBackToDownloads() {
        openDownloads()

        compose.onNodeWithContentDescription(searchButton).performClick()
        waitFor(hasSetTextAction())
        waitForNo(hasText(inboxLabel) and isSelectable())

        compose.onNodeWithContentDescription(searchBack).performClick()
        waitFor(hasText(emptyTitle))
        bar(downloadsLabel).assertIsSelected()
    }

    /**
     * Asks the activity to save its state while the Downloads list shows, as Android does before it
     * ends the process in the background, and checks every value in it, at any depth. It must hold
     * the saved back stacks, so an empty Bundle can't pass, and no listed video's ID or title.
     */
    @Test
    fun theSavedStateHoldsNoListedVideo() {
        keep(TestVideo.summary, DownloadState.DOWNLOADED)
        openDownloads()
        waitFor(hasText(TestVideo.TITLE))

        val saved = Bundle()
        compose.runOnUiThread {
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnSaveInstanceState(compose.activity, saved)
        }
        val text = savedText(saved)

        assertTrue("DownloadsKey" in text, "The saved state holds no back stacks")
        assertFalse(TestVideo.id.value in text, "The saved state holds a video ID")
        assertFalse(TestVideo.TITLE in text, "The saved state holds a video title")
    }

    /**
     * Writes a download of [video] in [state] to the database, as the engine leaves one, with a
     * folder of its own that holds a file, and returns the folder.
     */
    private fun keep(video: VideoSummary, state: DownloadState): File {
        val folder = "folder-" + video.id.value
        val now = Clock.System.now()
        runBlocking {
            downloadDao.request(
                DownloadEntity(
                    videoId = video.id.value,
                    folder = folder,
                    title = video.title,
                    channelId = video.channelId.value,
                    channelName = video.channelName,
                    durationMs = video.duration?.inWholeMilliseconds,
                    publishedAt = null,
                    publishedApproximate = false,
                    kind = video.kind,
                    thumbnails = video.thumbnails,
                    state = state,
                    failure = null,
                    requestedAt = now,
                    videoItag = 18,
                    videoMime = "video/mp4",
                    videoBytes = 1_000,
                    audioItag = null,
                    audioMime = null,
                    audioBytes = null,
                    audioTrack = null,
                    downloadedBytes = if (state == DownloadState.DOWNLOADED) 1_000 else 400,
                    youtubeTries = 0,
                    updatedAt = now,
                ),
            )
        }
        return File(files.root, folder).apply {
            mkdirs()
            File(this, "media.part").writeBytes(ByteArray(400))
        }
    }

    private fun openDownloads() {
        waitFor(hasText(downloadsLabel) and isSelectable())
        bar(downloadsLabel).performClick()
        bar(downloadsLabel).assertIsSelected()
    }

    /** The navigation bar's item labelled [label]. */
    private fun bar(label: String) = compose.onNode(hasText(label) and isSelectable())

    /**
     * The bytes that [saved] writes to a Parcel, read as text in UTF-16 and in UTF-8, as
     * ChannelsNavigationTest reads them, so it sees every key and value at any depth.
     */
    private fun savedText(saved: Bundle): String {
        val parcel = Parcel.obtain()
        val bytes =
            try {
                saved.writeToParcel(parcel, 0)
                parcel.marshall()
            } finally {
                parcel.recycle()
            }
        return String(bytes, Charsets.UTF_16LE) + String(bytes, Charsets.UTF_8)
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNo(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }
    }
}
