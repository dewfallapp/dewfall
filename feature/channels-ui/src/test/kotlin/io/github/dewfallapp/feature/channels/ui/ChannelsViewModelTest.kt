package io.github.dewfallapp.feature.channels.ui

import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.feature.channels.logic.ChannelsList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private val MONDAY = Instant.parse("2026-11-16T09:00:00Z")

private val KETTLE =
    ChannelOverview(
        SubscribedChannel(
            Channel(ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"), "Kettle and Crumb", emptyList(), ""),
            MONDAY,
        ),
        PublishedTime(MONDAY, isApproximate = false),
    )
private val ORBIT =
    ChannelOverview(
        SubscribedChannel(
            Channel(ChannelId("UCbbbbbbbbbbbbbbbbbbbbbb"), "Orbit Hour", emptyList(), ""),
            MONDAY,
        ),
        lastUpload = null,
    )

/** The overviews the test sets. Nothing else is read by the Channels screen. */
private class FakeSubscriptions : SubscriptionsRepository {
    val overviews = MutableStateFlow<List<ChannelOverview>>(emptyList())

    /** When true, the read throws, as a damaged database would. */
    var fails = false

    /** How many times a read of the overviews started. */
    var starts = 0

    override fun channelOverviews(): Flow<List<ChannelOverview>> {
        starts++
        return if (fails) flow { throw IllegalStateException("Damaged database") } else overviews
    }

    override fun subscribedChannels() = error("Not used")

    override fun subscribedChannel(channelId: ChannelId) = error("Not used")

    override fun isSubscribed(channelId: ChannelId) = error("Not used")

    override suspend fun subscribe(channel: Channel) = error("Not used")

    override suspend fun unsubscribe(channelId: ChannelId) = error("Not used")
}

/**
 * Tests that the ViewModel turns the read into the screen's state, and reads only while its state
 * has a collector, and for 5 seconds after. The reducer's own tests cover the lists.
 */
// setMain, resetMain, UnconfinedTestDispatcher, runCurrent and advanceTimeBy are still marked
// experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModelTest {
    private val subscriptions = FakeSubscriptions()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun readers() = subscriptions.overviews.subscriptionCount.value

    @Test
    fun `nothing is read while nothing shows the state`() = runTest {
        val viewModel = ChannelsViewModel(subscriptions)
        runCurrent()

        assertEquals(0, readers())
        assertEquals(ChannelsList.Loading, viewModel.state.value.list)
    }

    @Test
    fun `a shown screen turns each read into its list`() = runTest {
        val viewModel = ChannelsViewModel(subscriptions)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(ChannelsList.NoChannels, viewModel.state.value.list)

        subscriptions.overviews.value = listOf(KETTLE, ORBIT)
        runCurrent()
        assertEquals(ChannelsList.Shown(listOf(KETTLE, ORBIT)), viewModel.state.value.list)

        subscriptions.overviews.value = listOf(ORBIT)
        runCurrent()
        assertEquals(ChannelsList.Shown(listOf(ORBIT)), viewModel.state.value.list)
    }

    @Test
    fun `reading stops 5 seconds after the last collector leaves and starts again`() = runTest {
        val viewModel = ChannelsViewModel(subscriptions)
        val shown = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(1, readers())

        shown.cancel()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, readers())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(0, readers())

        subscriptions.overviews.value = listOf(KETTLE)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(1, readers())
        assertEquals(ChannelsList.Shown(listOf(KETTLE)), viewModel.state.value.list)
    }

    @Test
    fun `a collector that comes back within 5 seconds keeps the read going`() = runTest {
        val viewModel = ChannelsViewModel(subscriptions)
        val first = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        first.cancel()
        advanceTimeBy(1_000)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, readers())
    }

    @Test
    fun `a read that throws shows unreadable instead of crashing`() = runTest {
        subscriptions.fails = true
        val viewModel = ChannelsViewModel(subscriptions)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(ChannelsList.Unreadable, viewModel.state.value.list)
    }

    @Test
    fun `after a failed read, the screen reads again when it shows again`() = runTest {
        subscriptions.fails = true
        val viewModel = ChannelsViewModel(subscriptions)
        val shown = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        shown.cancel()
        advanceTimeBy(5_001)
        runCurrent()

        subscriptions.fails = false
        subscriptions.overviews.value = listOf(KETTLE)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(ChannelsList.Shown(listOf(KETTLE)), viewModel.state.value.list)
    }

    @Test
    fun `a failed read starts again when the screen comes back within 5 seconds`() = runTest {
        subscriptions.fails = true
        val viewModel = ChannelsViewModel(subscriptions)
        val shown = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(ChannelsList.Unreadable, viewModel.state.value.list)
        shown.cancel()
        advanceTimeBy(1_000)
        runCurrent()

        subscriptions.fails = false
        subscriptions.overviews.value = listOf(KETTLE)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(ChannelsList.Shown(listOf(KETTLE)), viewModel.state.value.list)
    }

    @Test
    fun `a healthy read isn't started again when the screen comes back within 5 seconds`() =
        runTest {
            val viewModel = ChannelsViewModel(subscriptions)
            val shown = backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            val startsBefore = subscriptions.starts
            shown.cancel()
            advanceTimeBy(1_000)
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()

            assertEquals(startsBefore, subscriptions.starts)
        }
}
