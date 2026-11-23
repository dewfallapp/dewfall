package io.github.dewfallapp

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/** How long the test waits for the first frames, as in SearchThenPlayTest. */
private const val PICTURE_TIMEOUT_MS = 60_000L

/**
 * Checks that the video screen draws its background, its minimize button and its details while the
 * SurfaceView shows a picture. On Android 10 the SurfaceView once cleared every pixel that the
 * screen had drawn before it, so the window showed black there, with no minimize button (#211). The
 * test reads the window's own pixels, where those cleared pixels are transparent. Screenshot tests
 * can't catch it, because they draw no SurfaceView hole. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VideoScreenBackgroundTest {
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
    fun backgroundAndMinimizeButtonShowOverThePicture() {
        compose.openSearchFromInbox()
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
        compose.onNodeWithText(TestVideo.TITLE).performClick()
        waitForNode(hasText(TestVideo.DESCRIPTION))

        // The SurfaceView clears its hole on each draw after its first frame, and the seek bar and
        // the times redraw the screen while the video plays. So by the time the video plays its
        // picture, the screen has drawn with the hole. A paused player keeps the last frame, and
        // the screen then stays still for the capture.
        compose.waitUntil(PICTURE_TIMEOUT_MS) { showsPicture() }
        compose.runOnUiThread { playback.player.value?.pause() }
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.runOnUiThread { playback.player.value?.isPlaying == false }
        }

        val minimize =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getString(PlayerR.string.player_minimize)
        val button = boundsOf(hasContentDescription(minimize))
        val description = boundsOf(hasText(TestVideo.DESCRIPTION))
        val window = compose.onRoot().captureToImage().asAndroidBitmap()

        assertTrue(pixelsIn(window, button).isNotEmpty(), "The button is outside the window")
        assertTrue(pixelsIn(window, description).isNotEmpty(), "The details are outside the window")
        assertEquals(0, seeThroughPixels(window, button), "See-through pixels in the button")
        assertEquals(0, seeThroughPixels(window, description), "See-through pixels in the details")
        assertTrue(colorsIn(window, button) > 1, "The minimize button shows no icon")
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
     * Whether the player plays the test video's picture. Media3's Player runs on the main thread.
     */
    private fun showsPicture(): Boolean = compose.runOnUiThread {
        val player = playback.player.value
        player != null && player.isPlaying && player.videoSize.width == TestVideo.WIDTH
    }

    private fun boundsOf(matcher: SemanticsMatcher): Rect =
        compose.onNode(matcher).fetchSemanticsNode().boundsInRoot

    /** How many pixels of [bitmap] inside [bounds] aren't fully opaque. */
    private fun seeThroughPixels(bitmap: Bitmap, bounds: Rect): Int =
        pixelsIn(bitmap, bounds).count { Color.alpha(it) != OPAQUE }

    /** How many different colors [bitmap] has inside [bounds]. */
    private fun colorsIn(bitmap: Bitmap, bounds: Rect): Int = pixelsIn(bitmap, bounds).toSet().size

    private fun pixelsIn(bitmap: Bitmap, bounds: Rect): List<Int> {
        val left = bounds.left.toInt().coerceIn(0, bitmap.width)
        val top = bounds.top.toInt().coerceIn(0, bitmap.height)
        val right = bounds.right.toInt().coerceIn(left, bitmap.width)
        val bottom = bounds.bottom.toInt().coerceIn(top, bitmap.height)
        val pixels = IntArray((right - left) * (bottom - top))
        if (pixels.isNotEmpty()) {
            bitmap.getPixels(pixels, 0, right - left, left, top, right - left, bottom - top)
        }
        return pixels.toList()
    }

    private companion object {
        /** The alpha of a fully opaque pixel. */
        const val OPAQUE = 255
    }
}
