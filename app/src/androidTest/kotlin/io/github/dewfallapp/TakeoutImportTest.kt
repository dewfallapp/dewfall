package io.github.dewfallapp

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.feature.channels.ui.R as ChannelsR
import io.github.dewfallapp.feature.inbox.ui.R as InboxR
import io.github.dewfallapp.feature.takeoutimport.ui.R as TakeoutR
import java.io.File
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith

/** How long the test waits for a screen to show what it expects. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * Made-up channels in a channel ID's form, never real channels', with names that no other screen
 * shows.
 */
private val CHANNELS =
    listOf(
        "UCtakeouttest00000000001" to "Made-up Bakery",
        "UCtakeouttest00000000002" to "Made-up Garden",
        "UCtakeouttest00000000003" to "Made-up Workshop",
    )

/**
 * A made-up subscriptions table in Takeout's shape: its header, one line per channel, and one line
 * that holds no channel, so the report has two counts.
 */
private val TABLE = buildString {
    append("Channel Id,Channel Url,Channel Title\n")
    for ((id, name) in CHANNELS) {
        append("$id,http://www.youtube.com/channel/$id,$name\n")
    }
    append("not a channel,,\n")
}

/**
 * Opens the Takeout import from the empty inbox, answers the system file picker with a made-up .csv
 * file in the app's cache folder, checks the report and the subscriptions, and returns to the inbox
 * with Done. A second test opens it from the empty Channels screen, without the navigation bar, and
 * returns there with the back arrow. Espresso-Intents answers the picker, so no other app opens.
 * The YouTube layer is FakeYouTubeModule's, which the import never calls, and the database is
 * TestDatabaseModule's, empty for every test. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TakeoutImportTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    /** An UnconfinedTestDispatcher, for the reason SearchThenPlayTest's rule gives. */
    // UnconfinedTestDispatcher is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>(UnconfinedTestDispatcher())

    @Inject lateinit var subscriptions: SubscriptionsRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "takeout-import-test.csv")

    @BeforeTest
    fun setUp() {
        hilt.inject()
        file.writeText(TABLE)
        Intents.init()
        val picked = Intent().setData(Uri.fromFile(file))
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(ActivityResult(Activity.RESULT_OK, picked))
    }

    @AfterTest
    fun tearDown() {
        Intents.release()
        file.delete()
    }

    @Test
    fun importingFromTheEmptyInboxSubscribesToEveryChannel() {
        waitForNode(hasText(context.getString(InboxR.string.inbox_no_channels_title)))
        compose.onNodeWithText(context.getString(InboxR.string.inbox_import_button)).performClick()
        waitForNode(hasText(context.getString(TakeoutR.string.takeout_import_title)))

        compose
            .onNodeWithText(context.getString(TakeoutR.string.takeout_import_choose))
            .performClick()

        val found =
            context.resources.getQuantityString(
                TakeoutR.plurals.takeout_import_found,
                CHANNELS.size,
                CHANNELS.size,
            )
        waitForNode(hasText(found))
        intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        val counts =
            context.resources.getQuantityString(
                TakeoutR.plurals.takeout_import_new,
                CHANNELS.size,
                CHANNELS.size,
            ) +
                context.getString(TakeoutR.string.takeout_import_counts_separator) +
                context.resources.getQuantityString(
                    TakeoutR.plurals.takeout_import_unreadable,
                    1,
                    1,
                )
        compose.onNodeWithText(counts).assertExists()
        for ((_, name) in CHANNELS) {
            compose.onNodeWithText(name).assertExists()
        }
        val subscribed = runBlocking { subscriptions.subscribedChannels().first() }
        assertEquals(
            CHANNELS.toSet(),
            subscribed.map { it.channel.id.value to it.channel.name }.toSet(),
        )

        compose
            .onNodeWithText(context.getString(TakeoutR.string.takeout_import_done))
            .performClick()

        val caughtUp =
            context.resources.getQuantityString(
                InboxR.plurals.inbox_caught_up_body,
                CHANNELS.size,
                CHANNELS.size,
            )
        waitForNode(hasText(caughtUp))
    }

    @Test
    fun importFromTheEmptyChannelsScreenGoesBackThere() {
        val channelsBar =
            hasText(context.getString(R.string.navigation_channels)) and isSelectable()
        // The Channels screen's own line, which the inbox's "No channels yet" doesn't have.
        val emptyChannels = hasText(context.getString(ChannelsR.string.channels_no_channels_body))
        waitForNode(channelsBar)
        compose.onNode(channelsBar).performClick()
        waitForNode(emptyChannels)

        compose
            .onNodeWithText(context.getString(ChannelsR.string.channels_import_button))
            .performClick()
        waitForNode(hasText(context.getString(TakeoutR.string.takeout_import_title)))
        compose.onAllNodes(channelsBar).assertCountEquals(0)
        compose
            .onNodeWithContentDescription(context.getString(TakeoutR.string.takeout_import_back))
            .performClick()

        waitForNode(emptyChannels)
        compose.onNode(channelsBar).assertIsSelected()
    }

    /** Waits until a node matches [matcher], for up to [SCREEN_TIMEOUT_MS]. */
    private fun waitForNode(matcher: SemanticsMatcher) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
