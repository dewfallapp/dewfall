package io.github.dewfallapp

import android.os.Bundle
import android.os.Parcel
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
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.channelpage.ui.R as ChannelR
import io.github.dewfallapp.feature.inbox.ui.R as InboxR
import io.github.dewfallapp.feature.player.ui.R as PlayerR
import io.github.dewfallapp.feature.youtubesearch.ui.R as SearchR
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * Subscribes on the test video's channel page, goes back to the inbox, and finds the channel's
 * videos there, saved by the page with no request of their own, opens a video from the inbox and
 * minimizes it, then checks that the state Android saves holds no ID, title or name. The YouTube
 * layer is FakeYouTubeModule's, and the database is TestDatabaseModule's, empty for every test. See
 * docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InboxFromChannelTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var playback: PlaybackConnection

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @BeforeTest
    fun setUp() {
        hilt.inject()
    }

    @AfterTest
    fun stop() {
        compose.stopPlayback(playback)
    }

    @Test
    fun subscribingOnAChannelPageFillsTheInbox() {
        compose.openSearchFromInbox()
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForNode(hasText(TestVideo.TITLE))
        compose.onAllNodesWithText(TestVideo.TITLE).onFirst().performClick()
        // Only the video screen shows the description, from the video's details.
        waitForNode(hasText(TestVideo.DESCRIPTION))
        compose.onNodeWithText(TestVideo.CHANNEL_NAME).performClick()
        waitForNode(hasText(TestVideo.CHANNEL_DESCRIPTION))
        compose
            .onNodeWithText(context.getString(ChannelR.string.channel_page_subscribe))
            .performClick()
        waitForNode(hasText(context.getString(ChannelR.string.channel_page_unsubscribe)))

        // Back to Search YouTube, then back to the inbox.
        compose
            .onNodeWithContentDescription(context.getString(ChannelR.string.channel_page_back))
            .performClick()
        waitForNode(hasContentDescription(context.getString(SearchR.string.youtube_search_back)))
        compose
            .onNodeWithContentDescription(context.getString(SearchR.string.youtube_search_back))
            .performClick()

        // The inbox's card for the channel, with both of its videos, the 2 latest.
        val count = context.resources.getQuantityString(InboxR.plurals.inbox_card_count, 2, 2)
        waitForNode(hasText(count))
        waitForNode(hasText(TestVideo.OTHER_TITLE))

        compose.onAllNodesWithText(TestVideo.OTHER_TITLE).onFirst().performClick()
        waitForNode(hasText(TestVideo.DESCRIPTION))
        compose
            .onNodeWithContentDescription(context.getString(PlayerR.string.player_minimize))
            .performClick()
        waitForNode(hasText(count))

        // Last, because saving the state moves the activity's lifecycle back to CREATED.
        assertSavedStateHoldsNoUserData()
    }

    /**
     * Asks the activity to save its state, as Android does before it ends the process in the
     * background, and checks every value in it, at any depth. It must hold the saved back stack, so
     * an empty Bundle can't pass, and no video or channel ID, title or name.
     */
    private fun assertSavedStateHoldsNoUserData() {
        val saved = Bundle()
        compose.runOnUiThread {
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnSaveInstanceState(compose.activity, saved)
        }
        val text = savedText(saved)
        assertTrue("InboxKey" in text, "The saved state holds no back stack")
        val userValues =
            listOf(
                TestVideo.id.value,
                TestVideo.otherId.value,
                TestVideo.channelId.value,
                TestVideo.TITLE,
                TestVideo.OTHER_TITLE,
                TestVideo.CHANNEL_NAME,
            )
        for (value in userValues) {
            assertFalse(value in text, "The saved state holds a user's value")
        }
    }

    /**
     * The bytes that [saved] writes to a Parcel, as Android writes it before it ends the process,
     * read as text twice: in UTF-16, which Parcel's strings use, and in UTF-8, which byte arrays
     * may hold. So it sees every key and value at any depth, also inside Parcelables, without
     * reading the values one by one through Bundle's deprecated untyped getter.
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

    /** Waits until a node matches [matcher], for up to [SCREEN_TIMEOUT_MS]. */
    private fun waitForNode(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
