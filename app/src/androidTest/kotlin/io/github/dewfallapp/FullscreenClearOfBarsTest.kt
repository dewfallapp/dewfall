package io.github.dewfallapp

import android.os.Build
import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * How long the test waits for the controls to hide while the video plays: the service's start, the
 * links and the first frames on a cold emulator that draws in software, then the 3 seconds.
 */
private const val HIDE_TIMEOUT_MS = 60_000L

/**
 * In fullscreen, no system bar covers the controls, as openspec/specs/player/spec.md's "The system
 * bars over fullscreen" scenario says. On Android 10 with three-button navigation the navigation
 * bar sits on the side in landscape, and before #218's fix it covered the exit button once the bars
 * had shown or hidden. The test lets the controls and the bars hide, shows them again with a tap,
 * pauses, and checks the exit button, the play button and the title against the root view's insets
 * of the bars, whether they show or not. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FullscreenClearOfBarsTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason that SearchThenPlayTest gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var playback: PlaybackConnection

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
    fun noBarCoversTheControls() {
        val fullscreen = compose.activity.getString(PlayerR.string.player_fullscreen)
        val exit = compose.activity.getString(PlayerR.string.player_exit_fullscreen)
        val play = compose.activity.getString(PlayerR.string.player_video_play)
        val show = compose.activity.getString(PlayerR.string.player_show_controls)

        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
        compose.onNodeWithText(TestVideo.TITLE).performClick()
        waitForNode(hasText(TestVideo.DESCRIPTION))

        // Fullscreen turns a phone to landscape, which makes a new activity.
        compose.onNodeWithContentDescription(fullscreen).performClick()
        waitForNode(hasContentDescription(exit))
        compose.waitUntil(SCREEN_TIMEOUT_MS) { compose.runOnUiThread { decorView().landscape() } }
        // The bug needs the navigation bar on a side, as three-button navigation puts it on a
        // phone in landscape before Android 11. Elsewhere the test checks nothing, so it skips,
        // and the workflow's own step makes sure its Android 10 emulator has the bar on a side.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val bars = compose.runOnUiThread { barsOf(decorView()) }
            assumeTrue(
                "Before Android 11 this test needs three-button navigation, with the bar on a " +
                    "side in landscape, but the bars' insets are $bars",
                bars != null && (bars.left > 0 || bars.right > 0),
            )
        }

        // The video plays, so the controls and the bars hide after 3 seconds.
        compose.waitUntil(HIDE_TIMEOUT_MS) {
            compose.onAllNodes(hasContentDescription(exit)).fetchSemanticsNodes().isEmpty()
        }

        // A tap on the picture shows them and the bars again. Pausing then keeps them, so the
        // check doesn't race the next 3 seconds.
        compose.onNode(hasClickLabel(show)).performClick()
        waitForNode(hasContentDescription(exit))
        compose.runOnUiThread { playback.player.value?.pause() }
        waitForNode(hasContentDescription(play))

        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            clearOfBars(hasContentDescription(exit)) &&
                clearOfBars(hasContentDescription(play)) &&
                clearOfBars(hasText(TestVideo.TITLE))
        }
    }

    /**
     * Whether the one node that [matcher] finds sits inside the system bars' insets, whether the
     * bars show or not, as the root view has them.
     */
    private fun clearOfBars(matcher: SemanticsMatcher): Boolean {
        val nodes = compose.onAllNodes(matcher).fetchSemanticsNodes()
        val bounds = nodes.singleOrNull()?.boundsInWindow ?: return false
        return compose.runOnUiThread {
            val view = decorView()
            val bars = barsOf(view) ?: return@runOnUiThread false
            bounds.left >= bars.left &&
                bounds.top >= bars.top &&
                bounds.right <= view.width - bars.right &&
                bounds.bottom <= view.height - bars.bottom
        }
    }

    /**
     * The system bars' insets of [view]'s window, whether the bars show or not, as androidx.core
     * reads them from the root view, or null before the view has any.
     */
    private fun barsOf(view: View): Insets? =
        ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())

    /** The current activity's window, which the turn to landscape replaced. */
    private fun decorView(): View = compose.activity.window.decorView

    private fun View.landscape() = width > height

    /**
     * Waits until a node matches [matcher], for up to [SCREEN_TIMEOUT_MS]. Compose's own
     * waitUntilAtLeastOneExists does the same, but is still an experimental API.
     */
    private fun waitForNode(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** A node whose click action has [label], as the fullscreen picture's has. */
    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label $label") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }
}
