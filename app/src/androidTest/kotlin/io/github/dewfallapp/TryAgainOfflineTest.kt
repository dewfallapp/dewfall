package io.github.dewfallapp

import androidx.compose.ui.test.SemanticsMatcher
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
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.FakeNetwork
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * How long the test waits for the player: the service's start and ExoPlayer's retries of a failed
 * load, with their growing delays, or the links and the first frames, on a cold emulator that draws
 * in software.
 */
private const val PLAYER_TIMEOUT_MS = 60_000L

/** How far the video must have played, well past its start and short of its 30 seconds. */
private const val PLAYED_MS = 3_000L

/**
 * A video opened with the network off plays after the one "Try again", even when the player still
 * held an earlier video's error, as #212 found with a live stream in the mini player. The first
 * video's files fail, so the player holds a network error, and the mini player shows it. Then the
 * second video opens with the network off. Its details and its links fail, and the screen says so
 * once, because both failed the same way. With the network back, its one "Try again" retries both,
 * as openspec/specs/player/spec.md's "Details and playback both fail" scenario says. See
 * docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TryAgainOfflineTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason that SearchThenPlayTest gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var playback: PlaybackConnection

    @Inject lateinit var network: FakeNetwork

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
    fun tryAgainRetriesBothAfterAnEarlierPlayerError() {
        val unreachable = compose.activity.getString(PlayerR.string.player_failed_network)
        val tryAgain = compose.activity.getString(PlayerR.string.player_try_again)

        // The first video's details and links load, and its files fail.
        network.mediaReachable = false
        compose.openSearchFromInbox()
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
        compose.onNodeWithText(TestVideo.TITLE).performClick()
        waitForNode(hasText(TestVideo.DESCRIPTION))
        compose.waitUntil(PLAYER_TIMEOUT_MS) { playback.failure.value == PlaybackFailure.NETWORK }

        // The back gesture minimizes it. It stays the player's item, with its error.
        Espresso.pressBack()
        waitForNode(hasText(TestVideo.OTHER_TITLE))

        // The second video opens with the network off.
        network.setReachable(false)
        compose.onNodeWithText(TestVideo.OTHER_TITLE).performClick()
        waitForNode(hasText(unreachable))
        // The player is ended with no item only once the session's prepare has run on the empty
        // player that the failed links left. Before #212's fix, that prepare cleared the earlier
        // error and with it the second video's failure.
        compose.waitUntil(PLAYER_TIMEOUT_MS) {
            compose.runOnUiThread {
                val player = playback.player.value
                player != null &&
                    player.currentMediaItem == null &&
                    player.playbackState == Player.STATE_ENDED
            }
        }
        assertEquals(PlaybackFailure.NETWORK, playback.failure.value)

        // The screen says it once, so there is one "Try again", which retries both.
        network.setReachable(true)
        compose.onNodeWithText(tryAgain).performClick()
        waitForNode(hasText(TestVideo.DESCRIPTION))
        compose.waitUntil(PLAYER_TIMEOUT_MS) { compose.runOnUiThread { playsOtherPast(PLAYED_MS) } }
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
     * Whether the player plays the second video past [positionMs]. Call it on the main thread, as
     * Media3's Player asks.
     */
    private fun playsOtherPast(positionMs: Long): Boolean {
        val player = playback.player.value ?: return false
        val videoId: VideoId? = player.currentMediaItem?.mediaId?.let(playback::videoOf)
        return videoId == TestVideo.otherId &&
            player.isPlaying &&
            player.currentPosition >= positionMs
    }
}
