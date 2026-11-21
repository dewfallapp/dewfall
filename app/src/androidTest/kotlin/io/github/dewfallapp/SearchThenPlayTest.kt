package io.github.dewfallapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.media3.common.VideoSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.AssetMedia
import io.github.dewfallapp.fake.TestVideo
import javax.inject.Inject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * How long the test waits for the video to play past [PLAYED_MS]: the service's start, the links
 * and the first frames on a cold emulator that draws in software, then the 3 seconds.
 */
private const val PLAYBACK_TIMEOUT_MS = 60_000L

/** How far the video must have played, well past its start and short of its 30 seconds. */
private const val PLAYED_MS = 3_000L

/**
 * The first emulator test: search for a video on Search YouTube, open it, and see it play. The
 * YouTube layer is FakeYouTubeModule's, and everything else is the app's own code. It finds
 * controls by their roles and by the test video's own text, and checks playback through the player,
 * not through the video screen's controls, so a new layout of the video screen doesn't break it.
 * See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchThenPlayTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /**
     * Compose UI 1.12.1's v2 rule queues the composition's effects on a StandardTestDispatcher,
     * which runs them on the test thread. The video area's Media3 state holders then remove their
     * player listeners off the main thread, which MediaController refuses with "MediaController
     * method is called from a wrong thread". An UnconfinedTestDispatcher runs each effect on the
     * thread that resumes it, the main thread, as the v1 rule did. Compose's synchronization docs
     * say that ui-test-junit4 1.13.0-alpha01 adds main-thread synchronization, which may make this
     * unnecessary. See docs/testing.md#what-search-then-play-checks.
     */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var playback: PlaybackConnection

    @Inject lateinit var media: AssetMedia

    @BeforeTest
    fun inject() {
        hilt.inject()
    }

    @Test
    fun searchThenPlay() {
        // Search YouTube has one text field.
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()

        waitForNode(hasText(TestVideo.TITLE))
        compose.onNodeWithText(TestVideo.TITLE).performClick()

        // Only the video screen shows the description, from the video's details.
        waitForNode(hasText(TestVideo.DESCRIPTION))

        // The video starts without a tap, as openspec/specs/player/spec.md says.
        compose.waitUntil(PLAYBACK_TIMEOUT_MS) { playedPast(PLAYED_MS) }
        val played = compose.runOnUiThread { snapshot() }
        assertTrue(played.playing)
        assertEquals(VideoSize(TestVideo.WIDTH, TestVideo.HEIGHT), played.videoSize)
        assertNull(played.failure)

        // The sound and the picture came through the player's own media sources.
        assertTrue(media.requestsFor(TestVideo.VIDEO_URL) > 0, "No request for the picture")
        assertTrue(media.requestsFor(TestVideo.AUDIO_URL) > 0, "No request for the sound")
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
     * Whether the player plays, past [positionMs], with the test video's picture. A failure ends
     * the test at once, instead of after the timeout.
     */
    private fun playedPast(positionMs: Long): Boolean {
        val state = compose.runOnUiThread { snapshot() }
        state.failure?.let { fail("Playback failed: $it") }
        return state.playing &&
            state.positionMs >= positionMs &&
            state.videoSize.width == TestVideo.WIDTH
    }

    /** What the player shows now. Call it on the main thread, as Media3's Player asks. */
    private fun snapshot(): PlaybackSnapshot {
        val player = playback.player.value
        return PlaybackSnapshot(
            playing = player?.isPlaying == true,
            positionMs = player?.currentPosition ?: 0L,
            videoSize = player?.videoSize ?: VideoSize.UNKNOWN,
            failure = playback.failure.value,
        )
    }

    private data class PlaybackSnapshot(
        val playing: Boolean,
        val positionMs: Long,
        val videoSize: VideoSize,
        val failure: PlaybackFailure?,
    )
}
