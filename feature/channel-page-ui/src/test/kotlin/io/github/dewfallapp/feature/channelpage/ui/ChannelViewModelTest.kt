package io.github.dewfallapp.feature.channelpage.ui

import io.github.dewfallapp.core.data.channel.ChannelUploadsRepository
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.feature.channelpage.logic.ChannelIntent
import io.github.dewfallapp.feature.channelpage.logic.LoadArea
import io.github.dewfallapp.feature.channelpage.logic.Subscription
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

// Made-up IDs in a channel ID's form, never real channels'.
private val OPENED = ChannelId("UC" + "0".repeat(21) + "1")
private val MOVED_TO = ChannelId("UC" + "0".repeat(21) + "2")

private fun channel(id: ChannelId = OPENED) =
    Channel(id = id, name = "Kettle and Crumb", avatars = emptyList(), description = "")

private fun uploads(id: ChannelId = OPENED) =
    YouTubeResult.Success(ChannelUploads(channel(id), videos = emptyList()))

/** One load the fake repository got. It waits until the test completes its answer. */
private class FakeLoad(val channelId: ChannelId) {
    val answer = CompletableDeferred<YouTubeResult<ChannelUploads>>()
}

/** A repository whose loads wait for the test, so a test decides when each one finishes. */
private class FakeUploads : ChannelUploadsRepository {
    val loads = mutableListOf<FakeLoad>()

    override suspend fun latestUploads(channelId: ChannelId): YouTubeResult<ChannelUploads> {
        val load = FakeLoad(channelId).also { loads += it }
        return load.answer.await()
    }
}

/**
 * A repository that keeps subscriptions in memory, and records which channels each write was for.
 * Its flows answer at once, as Room's do once the query ran.
 */
private class FakeSubscriptions : SubscriptionsRepository {
    val kept = MutableStateFlow<Map<ChannelId, SubscribedChannel>>(emptyMap())
    val subscribed = mutableListOf<Channel>()
    val unsubscribed = mutableListOf<ChannelId>()
    val read = mutableListOf<ChannelId>()

    override fun subscribedChannels(): Flow<List<SubscribedChannel>> = kept.map {
        it.values.toList()
    }

    override fun subscribedChannel(channelId: ChannelId): Flow<SubscribedChannel?> {
        read += channelId
        return kept.map { it[channelId] }
    }

    override fun isSubscribed(channelId: ChannelId): Flow<Boolean> = kept.map { channelId in it }

    override suspend fun subscribe(channel: Channel) {
        subscribed += channel
        kept.value +=
            channel.id to SubscribedChannel(channel, Instant.parse("2026-11-22T08:00:00Z"))
    }

    override suspend fun unsubscribe(channelId: ChannelId) {
        unsubscribed += channelId
        kept.value -= channelId
    }
}

/**
 * Tests which loads the ViewModel starts, and that their answers come back through the reducer. The
 * reducer's own tests cover the rest.
 */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {
    private val uploads = FakeUploads()
    private val subscriptions = FakeSubscriptions()

    private fun viewModel() = ChannelViewModel(ChannelArgs(OPENED), uploads, subscriptions)

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which local tests don't have. An unconfined
        // dispatcher starts each load at once, so the test sees it running right after dispatch.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the page loads its channel once when it opens, and the answer shows`() = runTest {
        val viewModel = viewModel()

        assertEquals(listOf(OPENED), uploads.loads.map { it.channelId })
        uploads.loads.single().answer.complete(uploads())

        assertEquals(LoadArea.Loaded(1, emptyList()), viewModel.state.value.load)
        assertEquals(channel(), viewModel.state.value.shownChannel)
    }

    @Test
    fun `Try again after a failure loads once more`() = runTest {
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))

        viewModel.dispatch(ChannelIntent.TryAgain)
        viewModel.dispatch(ChannelIntent.TryAgain)

        assertEquals(2, uploads.loads.size)
        assertEquals(LoadArea.Loading(2), viewModel.state.value.load)
    }

    @Test
    fun `Try again with nothing to try sends nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.dispatch(ChannelIntent.TryAgain)
        uploads.loads.single().answer.complete(YouTubeResult.Failure(YouTubeError.CHANNEL_CLOSED))
        viewModel.dispatch(ChannelIntent.TryAgain)

        assertEquals(1, uploads.loads.size)
    }

    @Test
    fun `Subscribe keeps the shown channel, and the card follows the database`() = runTest {
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(uploads())
        assertEquals(Subscription.NotSubscribed, viewModel.state.value.subscription)

        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals(listOf(channel()), subscriptions.subscribed)
        assertEquals(Subscription.Subscribed(channel()), viewModel.state.value.subscription)
    }

    @Test
    fun `Subscribe does nothing before the card shows, or while subscribed`() = runTest {
        val viewModel = viewModel()
        viewModel.dispatch(ChannelIntent.Subscribe)
        uploads.loads.single().answer.complete(uploads())
        viewModel.dispatch(ChannelIntent.Subscribe)
        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals(listOf(channel()), subscriptions.subscribed)
    }

    @Test
    fun `Unsubscribe removes the subscription only after the dialog`() = runTest {
        subscriptions.subscribe(channel())
        subscriptions.subscribed.clear()
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(uploads())

        viewModel.dispatch(ChannelIntent.UnsubscribeTapped)
        assertEquals(emptyList(), subscriptions.unsubscribed)
        viewModel.dispatch(ChannelIntent.UnsubscribeCancelled)
        assertEquals(emptyList(), subscriptions.unsubscribed)

        viewModel.dispatch(ChannelIntent.UnsubscribeTapped)
        viewModel.dispatch(ChannelIntent.UnsubscribeConfirmed)

        assertEquals(listOf(OPENED), subscriptions.unsubscribed)
        assertEquals(Subscription.NotSubscribed, viewModel.state.value.subscription)
    }

    @Test
    fun `offline, a followed channel can be unsubscribed and subscribed again`() = runTest {
        subscriptions.subscribe(channel())
        subscriptions.subscribed.clear()
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))

        viewModel.dispatch(ChannelIntent.UnsubscribeTapped)
        viewModel.dispatch(ChannelIntent.UnsubscribeConfirmed)
        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals(listOf(OPENED), subscriptions.unsubscribed)
        assertEquals(listOf(channel()), subscriptions.subscribed)
        assertIs<Subscription.Subscribed>(viewModel.state.value.subscription)
    }

    @Test
    fun `a redirect reads the new ID too, and Unsubscribe removes the old subscription`() =
        runTest {
            subscriptions.subscribe(channel(OPENED))
            subscriptions.subscribed.clear()
            val viewModel = viewModel()

            uploads.loads.single().answer.complete(uploads(MOVED_TO))

            assertEquals(setOf(OPENED, MOVED_TO), subscriptions.read.toSet())
            assertEquals(
                Subscription.Subscribed(channel(OPENED)),
                viewModel.state.value.subscription,
            )
            viewModel.dispatch(ChannelIntent.UnsubscribeTapped)
            viewModel.dispatch(ChannelIntent.UnsubscribeConfirmed)

            assertEquals(listOf(OPENED), subscriptions.unsubscribed)
            assertEquals(Subscription.NotSubscribed, viewModel.state.value.subscription)
        }

    @Test
    fun `after a redirect, Subscribe keeps the channel that YouTube answered for`() = runTest {
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(uploads(MOVED_TO))

        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals(listOf(channel(MOVED_TO)), subscriptions.subscribed)
    }
}
