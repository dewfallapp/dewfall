package io.github.dewfallapp

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.toSize
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import javax.inject.Inject
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * How long the test waits for the first video to play, on a cold emulator that draws in software.
 */
private const val PLAYBACK_TIMEOUT_MS = 60_000L

/** How many swipes past the last row make sure the list is at its very end. */
private const val END_SWIPES = 2

/** How far the row may move, in pixels, and still count as where it was. */
private const val TOLERANCE_PX = 1f

/**
 * Search YouTube's results, scrolled to their end, keep their place when the video screen closes,
 * with the last result above the mini player (#241). When a video opened, the mini player used to
 * leave DewfallNavigation's Column, so the outgoing screen got the full height for its exit
 * transition, and its list clamped its scroll and saved that place.
 *
 * The mini player shows before the list is scrolled, so the list is measured with it both times. A
 * list scrolled to its end while the mini player shows no video, such as before the first video or
 * after its close, is a different case, which this test leaves out: it was measured at full height,
 * so its last rows end up under the mini player when it next appears.
 *
 * Both test videos have short one-line titles from the same channel, so the mini player has the
 * same height for each. A title that wrapped would move the last row for a reason other than #241.
 * See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ListEndAfterVideoTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var playback: PlaybackConnection

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val closePlayer = context.getString(PlayerR.string.mini_player_close)

    /** The test video's row in the results, not its title in the mini player. */
    private val lastRow = hasText(TestVideo.TITLE) and hasAnyAncestor(hasScrollToIndexAction())

    @BeforeTest
    fun setUp() {
        hilt.inject()
    }

    /** So the next test starts the service with its own objects. */
    @AfterTest
    fun stop() {
        compose.stopPlayback(playback)
    }

    @Test
    fun theEndOfTheResultsKeepsItsPlaceWhenTheVideoScreenCloses() {
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.LONG_QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.OTHER_TITLE))

        // The mini player shows before the list is scrolled.
        compose.onNode(hasText(TestVideo.OTHER_TITLE)).performClick()
        waitForNode(hasText(TestVideo.DESCRIPTION))
        compose.waitUntil(PLAYBACK_TIMEOUT_MS) {
            compose.runOnUiThread { playback.player.value?.isPlaying == true }
        }
        Espresso.pressBack()
        waitForNode(hasContentDescription(closePlayer))

        // The end of the list: the test video's row, the last one.
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(lastRow)
        repeat(END_SWIPES) { list.performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val listTop = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot.top
        val firstRows =
            compose
                .onAllNodes(
                    hasText(TestVideo.OTHER_TITLE) and hasAnyAncestor(hasScrollToIndexAction()),
                )
                .fetchSemanticsNodes()
        assertTrue(
            firstRows.all { it.unclippedBounds().bottom <= listTop },
            "The list didn't scroll",
        )
        val before = rowBounds()
        assertTrue(before.bottom <= listBottom() + TOLERANCE_PX, "The last row isn't fully shown")

        compose.onNode(lastRow).performClick()
        waitForNode(hasText(TestVideo.DESCRIPTION))
        Espresso.pressBack()
        waitForMiniPlayerOfTestVideo()
        compose.waitForIdle()

        val shown = compose.onAllNodes(lastRow).fetchSemanticsNodes()
        assertTrue(shown.isNotEmpty(), "The last row scrolled out of view")
        val after = shown.single().unclippedBounds()
        assertTrue(
            abs(after.top - before.top) <= TOLERANCE_PX,
            "The last row moved from ${before.top} to ${after.top}",
        )
        val barTitle =
            compose
                .onNode(hasText(TestVideo.TITLE) and !hasAnyAncestor(hasScrollToIndexAction()))
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(after.bottom <= listBottom() + TOLERANCE_PX, "The last row is cut off")
        assertTrue(
            listBottom() <= barTitle.top + TOLERANCE_PX,
            "The list reaches under the mini player",
        )
    }

    /** Waits until the video screen is gone, and the mini player shows the test video. */
    private fun waitForMiniPlayerOfTestVideo() {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(hasText(TestVideo.DESCRIPTION)).fetchSemanticsNodes().isEmpty() &&
                compose
                    .onAllNodes(hasContentDescription(closePlayer))
                    .fetchSemanticsNodes()
                    .isNotEmpty() &&
                compose
                    .onAllNodes(
                        hasText(TestVideo.TITLE) and !hasAnyAncestor(hasScrollToIndexAction()),
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
    }

    /** Where the test video's row is, in the window. */
    private fun rowBounds(): Rect = compose.onNode(lastRow).fetchSemanticsNode().unclippedBounds()

    /**
     * Where the node is in the window, whole. boundsInRoot is clipped to the list's viewport, so a
     * row cut off at the bottom would still seem to end inside it.
     */
    private fun SemanticsNode.unclippedBounds(): Rect = Rect(positionInRoot, size.toSize())

    /** The bottom of the results list, which ends at the mini player's top edge. */
    private fun listBottom(): Float =
        compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot.bottom

    /** Waits until a node matches [matcher], for up to [SCREEN_TIMEOUT_MS]. */
    private fun waitForNode(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
