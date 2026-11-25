package io.github.dewfallapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.fake.FakeNetwork
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.inbox.ui.R as InboxR
import javax.inject.Inject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * Subscribes to the test channel with nothing saved, as after an import, taps "Check for new
 * videos" while the fake YouTube can't be reached and finds the connection banner, then taps "Try
 * again" with YouTube back and finds the channel's videos in the inbox and the banner gone. The
 * YouTube layer is FakeYouTubeModule's, and the database is TestDatabaseModule's, empty for every
 * test. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InboxCheckTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var subscriptions: SubscriptionsRepository

    @Inject lateinit var network: FakeNetwork

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @BeforeTest
    fun setUp() {
        hilt.inject()
    }

    @Test
    fun checkingForNewVideosFillsTheInboxAfterTryingAgain() {
        runBlocking { subscriptions.subscribe(TestVideo.uploads.channel) }
        val caughtUp =
            context.resources.getQuantityString(InboxR.plurals.inbox_caught_up_body, 1, 1)
        waitForNode(hasText(caughtUp))
        val offline = context.getString(InboxR.string.inbox_check_failed_network)

        network.youTubeReachable = false
        compose
            .onNodeWithContentDescription(context.getString(InboxR.string.inbox_check))
            .performClick()
        waitForNode(hasText(offline))
        compose.onNodeWithText(caughtUp).assertExists()
        compose.onNodeWithText(context.getString(InboxR.string.inbox_checking)).assertDoesNotExist()

        network.youTubeReachable = true
        compose.onNodeWithText(context.getString(InboxR.string.inbox_try_again)).performClick()

        waitForNode(hasText(TestVideo.TITLE))
        waitForNode(hasText(TestVideo.OTHER_TITLE))
        // The videos are saved before the check ends, so wait for the line to change back.
        val checking = context.getString(InboxR.string.inbox_checking)
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(hasText(checking)).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText(offline).assertDoesNotExist()
    }

    /** Waits until a node matches [matcher], for up to [SCREEN_TIMEOUT_MS]. */
    private fun waitForNode(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
