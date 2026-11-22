package io.github.dewfallapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.channelpage.ui.R as ChannelR
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
 * Opens a video's channel page from the video screen, subscribes, goes back, and opens the page
 * again to find the subscription kept. The YouTube layer is FakeYouTubeModule's, and the database
 * is the app's own. It finds controls by their text and content descriptions from the app's string
 * resources, so a new layout of the page doesn't break it. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChannelSubscribeTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var playback: PlaybackConnection

    @Inject lateinit var subscriptions: SubscriptionsRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val subscribe = context.getString(ChannelR.string.channel_page_subscribe)

    private val unsubscribe = context.getString(ChannelR.string.channel_page_unsubscribe)

    private val back = context.getString(ChannelR.string.channel_page_back)

    private val closePlayer = context.getString(PlayerR.string.mini_player_close)

    /** Starts with the test channel not subscribed, whatever an earlier run left. */
    @BeforeTest
    fun setUp() {
        hilt.inject()
        runBlocking { subscriptions.unsubscribe(TestVideo.channelId) }
    }

    /** Leaves no subscription, and stops playback so the next test starts the service anew. */
    @AfterTest
    fun tearDown() {
        runBlocking { subscriptions.unsubscribe(TestVideo.channelId) }
        compose.stopPlayback(playback)
    }

    @Test
    fun subscribeFromTheChannelPage() {
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        openChannelPage()

        compose.onNodeWithText(subscribe).performClick()
        waitForNode(hasText(unsubscribe))
        assertTrue(runBlocking { subscriptions.isSubscribed(TestVideo.channelId).first() })

        compose.onNodeWithContentDescription(back).performClick()
        openChannelPage(waitFor = unsubscribe)
    }

    /**
     * Opens the test video, from the search results or the mini player, which both show its title,
     * then its channel page, and waits until the page shows [waitFor].
     */
    private fun openChannelPage(waitFor: String = subscribe) {
        waitForNode(hasText(TestVideo.TITLE))
        compose.onAllNodesWithText(TestVideo.TITLE).onFirst().performClick()
        // Only the video screen shows the description, from the video's details.
        waitForNode(hasText(TestVideo.DESCRIPTION))
        compose.onNodeWithText(TestVideo.CHANNEL_NAME).performClick()
        // Only the channel page shows the channel's description, and the video plays on in the
        // mini player under it, whose close button only the mini player has.
        waitForNode(hasText(TestVideo.CHANNEL_DESCRIPTION))
        waitForNode(hasContentDescription(closePlayer))
        waitForNode(hasText(waitFor))
    }

    /** Waits until a node matches [matcher], for up to [SCREEN_TIMEOUT_MS]. */
    private fun waitForNode(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
