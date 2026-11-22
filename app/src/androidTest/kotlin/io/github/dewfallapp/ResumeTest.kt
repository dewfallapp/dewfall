package io.github.dewfallapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.media3.common.Player
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.watch.WatchStateRepository
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * How long the test waits for the player: the service's start, the links and the first frames on a
 * cold emulator that draws in software, then the 10 seconds.
 */
private const val PLAYBACK_TIMEOUT_MS = 60_000L

/**
 * How far the video plays before the test leaves it: well past the start, where a start from zero
 * is, and well short of 27 of its 30 seconds, where it would count as watched.
 */
private const val PLAYED_MS = 10_000L

/**
 * The resume end to end, through the real PlaybackService and an in-memory database: play the test
 * video past 10 seconds, pause it, minimize it, close the mini player, open it again, and see it
 * start where it was left. It reads the position at the player's first ready state after the reopen
 * with a listener, so a start from zero can't pass by playing on to 10 seconds. See
 * docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ResumeTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** As in SearchThenPlayTest, whose KDoc explains the dispatcher. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var playback: PlaybackConnection

    @Inject lateinit var watchStates: WatchStateRepository

    @BeforeTest
    fun inject() {
        hilt.inject()
    }

    /** So the next test starts the service with its own objects. */
    @AfterTest
    fun stop() {
        compose.stopPlayback(playback)
    }

    @Test
    fun reopeningAVideoStartsWhereItWasLeft() {
        val close = compose.activity.getString(PlayerR.string.mini_player_close)

        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
        compose.onNodeWithText(TestVideo.TITLE).performClick()
        compose.waitUntil(PLAYBACK_TIMEOUT_MS) { compose.runOnUiThread { playsPast(PLAYED_MS) } }

        // Pause, so it stays well short of 90 percent, then minimize and close the mini player.
        compose.runOnUiThread { playback.player.value?.pause() }
        Espresso.pressBack()
        waitForNode(hasContentDescription(close))
        compose.onNode(hasContentDescription(close)).performClick()

        // The close's save has landed before the video opens again.
        compose.waitUntil(SCREEN_TIMEOUT_MS) { storedMs() >= PLAYED_MS }
        val stored = runBlocking { watchStates.watchState(TestVideo.id).first() }
        assertNull(stored?.watchedAt, "Marked watched at 10 of 30 seconds")

        // The first ready state of the reopened video says where it starts.
        var firstReadyMs: Long? = null
        compose.runOnUiThread {
            val player = assertNotNull(playback.player.value, "The player is gone")
            player.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (
                            playbackState == Player.STATE_READY &&
                                firstReadyMs == null &&
                                player.currentMediaItem != null
                        ) {
                            firstReadyMs = player.currentPosition
                            player.removeListener(this)
                        }
                    }
                },
            )
        }
        compose.onNodeWithText(TestVideo.TITLE).performClick()
        compose.waitUntil(PLAYBACK_TIMEOUT_MS) { compose.runOnUiThread { firstReadyMs != null } }

        val startedAt = compose.runOnUiThread { firstReadyMs }
        assertTrue(
            startedAt != null && startedAt >= PLAYED_MS,
            "The video started at $startedAt ms, not where it was left",
        )
    }

    /** The test video's stored position in milliseconds, or 0 when it has none. */
    private fun storedMs(): Long = runBlocking {
        watchStates.watchState(TestVideo.id).first()?.position?.inWholeMilliseconds ?: 0L
    }

    /**
     * Whether the player plays the test video past [positionMs]. Call it on the main thread, as
     * Media3's Player asks.
     */
    private fun playsPast(positionMs: Long): Boolean {
        val player = playback.player.value ?: return false
        return player.isPlaying && player.currentPosition >= positionMs
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
}
