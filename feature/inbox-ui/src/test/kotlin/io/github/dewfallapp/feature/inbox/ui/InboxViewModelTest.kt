package io.github.dewfallapp.feature.inbox.ui

import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.feature.inbox.logic.InboxContent
import io.github.dewfallapp.feature.inbox.logic.InboxGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private val KETTLE =
    Channel(ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"), "Kettle and Crumb", emptyList(), "")
private val SUBSCRIBED_AT = Instant.parse("2026-11-16T09:00:00Z")

private val VIDEO =
    VideoSummary(
        id = VideoId("aaaaaaaaaa1"),
        title = "Wild yeasts",
        channelId = KETTLE.id,
        channelName = KETTLE.name,
        duration = 54.minutes,
        published = null,
        thumbnails = emptyList(),
        kind = VideoKind.VIDEO,
    )

/** Subscriptions the test sets. Only the list is read; the rest isn't used by the inbox. */
private class FakeSubscriptions : SubscriptionsRepository {
    val channels = MutableStateFlow<List<SubscribedChannel>>(emptyList())

    override fun subscribedChannels(): Flow<List<SubscribedChannel>> = channels

    override fun channelOverviews(): Flow<List<ChannelOverview>> = error("Not used")

    override fun subscribedChannel(channelId: ChannelId) = error("Not used")

    override fun isSubscribed(channelId: ChannelId) = error("Not used")

    override suspend fun subscribe(channel: Channel) = error("Not used")

    override suspend fun unsubscribe(channelId: ChannelId) = error("Not used")
}

/** The inbox's videos the test sets. Saving isn't used by the inbox. */
private class FakeStoredVideos : StoredVideosRepository {
    val inbox = MutableStateFlow<List<VideoSummary>>(emptyList())

    /** When true, the read throws, as a damaged database would. */
    var fails = false

    /** How many times a read of the inbox's videos started. */
    var starts = 0

    override suspend fun saveUploads(uploads: ChannelUploads) = error("Not used")

    override fun inboxVideos(): Flow<List<VideoSummary>> =
        (if (fails) flow { error("Damaged database") } else inbox).onStart { starts++ }
}

/**
 * Tests that the ViewModel turns the two reads into the inbox's state, and reads only while its
 * state has a collector, and for 5 seconds after. The reducer's own tests cover the grouping.
 */
// setMain, resetMain, UnconfinedTestDispatcher, runCurrent and advanceTimeBy are still marked
// experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    private val subscriptions = FakeSubscriptions()
    private val storedVideos = FakeStoredVideos()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun readers() =
        subscriptions.channels.subscriptionCount.value + storedVideos.inbox.subscriptionCount.value

    @Test
    fun `nothing is read while nothing shows the state`() = runTest {
        val viewModel = InboxViewModel(subscriptions, storedVideos)
        runCurrent()

        assertEquals(0, readers())
        assertNull(viewModel.state.value.content)
    }

    @Test
    fun `a shown inbox turns the reads into its content`() = runTest {
        val viewModel = InboxViewModel(subscriptions, storedVideos)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(InboxContent.NoChannels, viewModel.state.value.content)

        subscriptions.channels.value = listOf(SubscribedChannel(KETTLE, SUBSCRIBED_AT))
        runCurrent()
        assertEquals(InboxContent.CaughtUp(1), viewModel.state.value.content)

        storedVideos.inbox.value = listOf(VIDEO)
        runCurrent()
        assertEquals(
            InboxContent.Videos(listOf(InboxGroup(KETTLE.id, KETTLE.name, listOf(VIDEO)))),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `reading stops 5 seconds after the last collector leaves and starts again`() = runTest {
        val viewModel = InboxViewModel(subscriptions, storedVideos)
        val shown = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(2, readers())

        shown.cancel()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(2, readers())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(0, readers())

        storedVideos.inbox.value = listOf(VIDEO)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(2, readers())
        assertEquals(
            InboxContent.Videos(listOf(InboxGroup(KETTLE.id, KETTLE.name, listOf(VIDEO)))),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `a collector that comes back within 5 seconds keeps the reads going`() = runTest {
        val viewModel = InboxViewModel(subscriptions, storedVideos)
        val first = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        first.cancel()
        advanceTimeBy(1_000)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(2, readers())
        assertEquals(1, storedVideos.starts, "The read stopped and started again")
    }

    @Test
    fun `a read that throws shows unreadable instead of crashing`() = runTest {
        storedVideos.fails = true
        val viewModel = InboxViewModel(subscriptions, storedVideos)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(InboxContent.Unreadable, viewModel.state.value.content)
    }

    @Test
    fun `after a failed read, the inbox reads again when it shows again`() = runTest {
        storedVideos.fails = true
        val viewModel = InboxViewModel(subscriptions, storedVideos)
        val shown = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        shown.cancel()
        advanceTimeBy(5_001)
        runCurrent()

        storedVideos.fails = false
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(InboxContent.NoChannels, viewModel.state.value.content)
    }
}
