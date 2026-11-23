package io.github.dewfallapp

import android.os.Bundle
import android.os.Parcel
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.ui.R as CoreUiR
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.channelpage.ui.R as ChannelR
import io.github.dewfallapp.feature.youtubesearch.ui.R as SearchR
import io.github.dewfallapp.navigation.ChannelKey
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
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
 * Subscribes to the test channel from Search YouTube's channel row, unsubscribes there through the
 * dialog, subscribes on the channel's page and finds the row following it, then checks what Android
 * would save for the process while the page is open. The YouTube layer is FakeYouTubeModule's, and
 * the database is the emulator tests' own. It finds controls by their text and content descriptions
 * from the app's string resources, so a new layout of the screen doesn't break it. See
 * docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchChannelTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var subscriptions: SubscriptionsRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val subscribeTo =
        context.getString(SearchR.string.youtube_search_subscribe_to, TestVideo.CHANNEL_NAME)

    private val subscribedTo =
        context.getString(SearchR.string.youtube_search_subscribed_to, TestVideo.CHANNEL_NAME)

    private val pageSubscribe = context.getString(ChannelR.string.channel_page_subscribe)

    private val pageUnsubscribe = context.getString(ChannelR.string.channel_page_unsubscribe)

    private val dialogTitle =
        context.getString(CoreUiR.string.core_ui_unsubscribe_title, TestVideo.CHANNEL_NAME)

    private val dialogUnsubscribe = context.getString(CoreUiR.string.core_ui_unsubscribe)

    private val back = context.getString(ChannelR.string.channel_page_back)

    /** Starts with the test channel not subscribed, whatever an earlier run left. */
    @BeforeTest
    fun setUp() {
        hilt.inject()
        runBlocking { subscriptions.unsubscribe(TestVideo.channelId) }
    }

    /** Leaves no subscription, so the next test starts the same way. */
    @AfterTest
    fun tearDown() {
        runBlocking { subscriptions.unsubscribe(TestVideo.channelId) }
    }

    @Test
    fun subscribeAndUnsubscribeFromAChannelRow() {
        compose.openSearchFromInbox()
        compose.onNode(hasSetTextAction()).performTextInput(TestVideo.QUERY)
        compose.onNode(hasSetTextAction()).performImeAction()

        waitForNode(hasContentDescription(subscribeTo))
        compose.onNodeWithContentDescription(subscribeTo).performClick()
        waitForNode(hasContentDescription(subscribedTo))
        assertTrue(isSubscribed())

        compose.onNodeWithContentDescription(subscribedTo).performClick()
        waitForNode(hasText(dialogTitle))
        compose.onNodeWithText(dialogUnsubscribe).performClick()
        waitForNode(hasContentDescription(subscribeTo))
        assertFalse(isSubscribed())

        // Only the channel row shows the excerpt, so tapping it opens the channel page, which shows
        // the whole description. Subscribing there shows in the row on the way back.
        openChannelPage()
        compose.onNodeWithText(pageSubscribe).performClick()
        waitForNode(hasText(pageUnsubscribe))
        compose.onNodeWithContentDescription(back).performClick()
        waitForNode(hasContentDescription(subscribedTo))

        // Saving the state moves the activity's lifecycle back to CREATED, so it comes last.
        openChannelPage()
        checkSavedState()
    }

    /** Opens the test channel's page from its row, and waits until it shows the description. */
    private fun openChannelPage() {
        compose.onNodeWithText(TestVideo.CHANNEL_EXCERPT).performClick()
        waitForNode(hasText(TestVideo.CHANNEL_DESCRIPTION))
    }

    private fun isSubscribed(): Boolean = runBlocking {
        subscriptions.isSubscribed(TestVideo.channelId).first()
    }

    /**
     * Asks the activity to save its state, as Android does before it ends the process in the
     * background, and reads every string in it. The saved back stack must be there, with the open
     * channel page's key and its channel's ID, so an empty Bundle can't pass. No channel name,
     * excerpt or description, and no video ID or title, may be there, as the youtube-search spec's
     * "Search text stays private" says.
     */
    private fun checkSavedState() {
        val saved = Bundle()
        compose.activityRule.scenario.onActivity { activity ->
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnSaveInstanceState(activity, saved)
        }
        val text = stringsIn(saved)
        val keyName = ChannelKey.serializer().descriptor.serialName
        assertTrue(keyName in text, "the saved state holds no channel page")
        assertTrue(TestVideo.channelId.value in text, "the saved state holds no channel ID")
        val hidden =
            listOf(
                TestVideo.CHANNEL_NAME,
                TestVideo.CHANNEL_EXCERPT,
                TestVideo.CHANNEL_DESCRIPTION,
                TestVideo.TITLE,
                TestVideo.OTHER_TITLE,
                TestVideo.id.value,
                TestVideo.otherId.value,
            )
        // The test channel's made-up ID holds the first video's ID, so the check looks for the
        // video IDs only outside it.
        val withoutChannelId = text.replace(TestVideo.channelId.value, "")
        for (value in hidden) {
            // The values are TestVideo's made-up ones, so the message may name them.
            assertFalse(value in withoutChannelId, "the saved state holds $value")
        }
    }

    /**
     * Every string in [bundle], at any depth, as one text. A Parcel writes each string in UTF-16 at
     * an offset that is a multiple of 4, so reading the whole Parcel as UTF-16 shows each one.
     */
    private fun stringsIn(bundle: Bundle): String {
        val parcel = Parcel.obtain()
        try {
            bundle.writeToParcel(parcel, 0)
            return String(parcel.marshall(), Charsets.UTF_16LE)
        } finally {
            parcel.recycle()
        }
    }

    /** Waits until a node matches [matcher], for up to [SCREEN_TIMEOUT_MS]. */
    private fun waitForNode(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
