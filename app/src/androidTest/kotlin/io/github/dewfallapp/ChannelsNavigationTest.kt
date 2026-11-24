package io.github.dewfallapp

import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.fake.TestVideo
import io.github.dewfallapp.feature.channels.ui.R as ChannelsR
import io.github.dewfallapp.feature.youtubesearch.ui.R as SearchR
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * The navigation bar between the inbox and the Channels screen, as the inbox spec's navigation bar
 * requirements and the subscriptions spec's Channels screen requirements say: each destination's
 * history, the back gesture, a tap on the selected destination, Search YouTube from Channels, a
 * Search YouTube in each destination, recreation and the saved state. Each test subscribes to the
 * test channel first. The YouTube layer is FakeYouTubeModule's, and the database is
 * TestDatabaseModule's, empty for every test. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChannelsNavigationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var subscriptions: SubscriptionsRepository

    @Inject lateinit var playback: PlaybackConnection

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val inboxLabel = context.getString(R.string.navigation_inbox)
    private val channelsLabel = context.getString(R.string.navigation_channels)
    private val searchButton = context.getString(ChannelsR.string.channels_search)
    private val searchBack = context.getString(SearchR.string.youtube_search_back)

    /** The Channels screen's count, which only its list shows, with the one test channel. */
    private val channelsCount =
        context.resources.getQuantityString(ChannelsR.plurals.channels_count, 1, 1)

    @BeforeTest
    fun setUp() {
        hilt.inject()
        runBlocking { subscriptions.subscribe(TestVideo.uploads.channel) }
    }

    @AfterTest
    fun tearDown() {
        compose.stopPlayback(playback)
        runBlocking { subscriptions.unsubscribe(TestVideo.channelId) }
    }

    @Test
    fun channelsKeepsItsHistoryAndBackGoesToTheInbox() {
        openChannelsList()
        openChannelPage()
        bar(channelsLabel).assertIsSelected()

        tap(inboxLabel)
        bar(inboxLabel).assertIsSelected()
        waitForNo(hasText(TestVideo.CHANNEL_DESCRIPTION))
        tap(channelsLabel)
        waitFor(hasText(TestVideo.CHANNEL_DESCRIPTION))

        Espresso.pressBack()
        waitForNo(hasText(TestVideo.CHANNEL_DESCRIPTION))
        waitFor(hasText(channelsCount))
        bar(channelsLabel).assertIsSelected()
        Espresso.pressBack()
        waitForNo(hasText(channelsCount))
        bar(inboxLabel).assertIsSelected()
    }

    @Test
    fun tappingChannelsAgainReturnsToTheList() {
        openChannelsList()
        openChannelPage()

        tap(channelsLabel)

        waitForNo(hasText(TestVideo.CHANNEL_DESCRIPTION))
        waitFor(hasText(channelsCount))
        bar(channelsLabel).assertIsSelected()
    }

    @Test
    fun searchFromChannelsHidesTheBarAndGoesBackToChannels() {
        openChannelsList()

        compose.onNodeWithContentDescription(searchButton).performClick()
        waitFor(hasSetTextAction())
        waitForNo(hasText(inboxLabel) and isSelectable())

        compose.onNodeWithContentDescription(searchBack).performClick()
        waitFor(hasText(channelsCount))
        bar(channelsLabel).assertIsSelected()
    }

    @Test
    fun eachDestinationKeepsItsOwnSearchYouTube() {
        // From the inbox: a search, a video, then its channel, whose page shows the bar.
        compose.openSearchFromInbox()
        search(TestVideo.LONG_QUERY)
        // The long search lists the second test video first, and the first one off screen.
        waitFor(hasText(TestVideo.OTHER_TITLE))
        compose.onAllNodesWithText(TestVideo.OTHER_TITLE).onFirst().performClick()
        waitFor(hasText(TestVideo.DESCRIPTION))
        compose.onNodeWithText(TestVideo.CHANNEL_NAME).performClick()
        waitFor(hasText(TestVideo.CHANNEL_DESCRIPTION))

        // From Channels: another search, then its channel row's page.
        tap(channelsLabel)
        waitFor(hasText(channelsCount))
        compose.onNodeWithContentDescription(searchButton).performClick()
        search(TestVideo.QUERY)
        waitFor(hasText(TestVideo.CHANNEL_EXCERPT))
        compose.onNodeWithText(TestVideo.CHANNEL_EXCERPT).performClick()
        waitFor(hasText(TestVideo.CHANNEL_DESCRIPTION))

        // The inbox's Search YouTube still has its own text.
        tap(inboxLabel)
        waitFor(hasText(TestVideo.CHANNEL_DESCRIPTION))
        Espresso.pressBack()
        waitFor(hasSetTextAction() and hasText(TestVideo.LONG_QUERY))
        compose.onNodeWithContentDescription(searchBack).performClick()
        waitFor(hasText(inboxLabel) and isSelectable())

        // And so has the Channels destination's.
        tap(channelsLabel)
        waitFor(hasText(TestVideo.CHANNEL_DESCRIPTION))
        Espresso.pressBack()
        waitFor(hasSetTextAction() and hasText(TestVideo.QUERY))
    }

    @Test
    fun theDestinationsSurviveTheActivityBeingRecreated() {
        openChannelsList()
        openChannelPage()

        compose.activityRule.scenario.recreate()

        waitFor(hasText(TestVideo.CHANNEL_DESCRIPTION))
        bar(channelsLabel).assertIsSelected()
        Espresso.pressBack()
        waitForNo(hasText(TestVideo.CHANNEL_DESCRIPTION))
        waitFor(hasText(channelsCount))
    }

    /**
     * Asks the activity to save its state while the Channels list shows, as Android does before it
     * ends the process in the background, and checks every value in it, at any depth. It must hold
     * the saved back stacks, so an empty Bundle can't pass, and no listed channel's ID or name.
     */
    @Test
    fun theSavedStateHoldsNoListedChannel() {
        openChannelsList()

        val saved = Bundle()
        compose.runOnUiThread {
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnSaveInstanceState(compose.activity, saved)
        }
        val text = savedText(saved)

        assertTrue("ChannelsKey" in text, "The saved state holds no back stacks")
        assertFalse(TestVideo.channelId.value in text, "The saved state holds a channel ID")
        assertFalse(TestVideo.CHANNEL_NAME in text, "The saved state holds a channel name")
    }

    private fun openChannelsList() {
        waitFor(hasText(channelsLabel) and isSelectable())
        tap(channelsLabel)
        waitFor(hasText(channelsCount))
        bar(channelsLabel).assertIsSelected()
    }

    private fun openChannelPage() {
        compose.onNodeWithText(TestVideo.CHANNEL_NAME).performClick()
        waitFor(hasText(TestVideo.CHANNEL_DESCRIPTION))
    }

    private fun search(query: String) {
        waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput(query)
        compose.onNode(hasSetTextAction()).performImeAction()
    }

    /** The navigation bar's item labelled [label]. */
    private fun bar(label: String) = compose.onNode(hasText(label) and isSelectable())

    private fun tap(label: String) {
        bar(label).performClick()
    }

    /**
     * The bytes that [saved] writes to a Parcel, read as text in UTF-16 and in UTF-8, as
     * InboxFromChannelTest reads them, so it sees every key and value at any depth.
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

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNo(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }
    }
}
