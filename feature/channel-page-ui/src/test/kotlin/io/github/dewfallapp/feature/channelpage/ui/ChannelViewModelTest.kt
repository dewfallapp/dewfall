package io.github.dewfallapp.feature.channelpage.ui

import io.github.dewfallapp.core.data.channel.ChannelUploadsRepository
import io.github.dewfallapp.core.data.subscriptions.SubscribeWithUploads
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.VideoSummary
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
 * Its flows answer at once, as Room's do once the query ran, unless the test holds a read in
 * [heldReads].
 */
private class FakeSubscriptions : SubscriptionsRepository {
    val kept = MutableStateFlow<Map<ChannelId, SubscribedChannel>>(emptyMap())
    val subscribed = mutableListOf<Channel>()
    val unsubscribed = mutableListOf<ChannelId>()
    val read = mutableListOf<ChannelId>()

    /** Reads held back until the test completes them, by channel ID. */
    val heldReads = mutableMapOf<ChannelId, CompletableDeferred<Unit>>()

    override fun subscribedChannels(): Flow<List<SubscribedChannel>> = kept.map {
        it.values.toList()
    }

    // The channel page never reads the Channels screen's list.
    override fun channelOverviews(): Flow<List<ChannelOverview>> = kept.map { channels ->
        channels.values.map { ChannelOverview(it, lastUpload = null) }
    }

    override fun subscribedChannel(channelId: ChannelId): Flow<SubscribedChannel?> {
        read += channelId
        val held = heldReads[channelId]
        return flow {
            held?.await()
            emitAll(kept.map { it[channelId] })
        }
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
 * Records each save of uploads, with how many subscriptions [subscriptions] had kept by then, so a
 * test can check that a save on Subscribe comes after the subscription.
 */
private class FakeStoredVideos(private val subscriptions: FakeSubscriptions) :
    StoredVideosRepository {
    val saves = mutableListOf<Pair<ChannelUploads, Int>>()

    /** When true, each save throws, as a full disk would. */
    var fails = false

    override suspend fun saveUploads(uploads: ChannelUploads) {
        if (fails) error("Disk full")
        saves += uploads to subscriptions.subscribed.size
    }

    override fun inboxVideos(): Flow<List<VideoSummary>> = emptyFlow()
}

/**
 * Records each call, and does what the real one does with the two fakes: subscribe, then save. The
 * real one's own test covers that both finish after the caller is cancelled.
 */
private class FakeSubscribeWithUploads(
    private val subscriptions: FakeSubscriptions,
    private val storedVideos: FakeStoredVideos,
) : SubscribeWithUploads {
    val calls = mutableListOf<Pair<Channel, ChannelUploads?>>()

    override suspend fun subscribe(channel: Channel, uploads: ChannelUploads?) {
        calls += channel to uploads
        subscriptions.subscribe(channel)
        if (uploads != null) storedVideos.saveUploads(uploads)
    }
}

/**
 * Tests which loads the ViewModel starts, that their answers come back through the reducer, and
 * when it subscribes and saves the loaded uploads. The reducer's own tests cover the rest.
 */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {
    private val uploads = FakeUploads()
    private val subscriptions = FakeSubscriptions()
    private val storedVideos = FakeStoredVideos(subscriptions)
    private val subscribeWithUploads = FakeSubscribeWithUploads(subscriptions, storedVideos)

    private fun viewModel() =
        ChannelViewModel(
            ChannelArgs(OPENED),
            uploads,
            subscriptions,
            storedVideos,
            subscribeWithUploads,
        )

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

    @Test
    fun `Subscribe saves the shown uploads once, after the subscription is kept`() = runTest {
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(uploads())

        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals(listOf(uploads().value to 1), storedVideos.saves)
    }

    @Test
    fun `a subscribed channel's page saves its uploads when they load`() = runTest {
        subscriptions.subscribe(channel())
        viewModel()

        uploads.loads.single().answer.complete(uploads())

        assertEquals(listOf(uploads().value), storedVideos.saves.map { it.first })
    }

    @Test
    fun `uploads that loaded before the subscription was read are saved once it is`() = runTest {
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(uploads())
        assertEquals(emptyList(), storedVideos.saves)

        subscriptions.subscribe(channel())

        assertIs<Subscription.Subscribed>(viewModel.state.value.subscription)
        assertEquals(listOf(uploads().value), storedVideos.saves.map { it.first })
    }

    @Test
    fun `each load of a subscribed channel's page is saved once`() = runTest {
        subscriptions.subscribe(channel())
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        assertEquals(emptyList(), storedVideos.saves)

        viewModel.dispatch(ChannelIntent.TryAgain)
        uploads.loads.last().answer.complete(uploads())
        // The database answers again for the same load, as Room does after any write.
        subscriptions.subscribe(channel(MOVED_TO))

        assertEquals(listOf(uploads().value), storedVideos.saves.map { it.first })
    }

    @Test
    fun `a channel that isn't subscribed is never saved`() = runTest {
        viewModel()
        uploads.loads.single().answer.complete(uploads())

        assertEquals(emptyList(), storedVideos.saves)
    }

    @Test
    fun `Subscribe offline saves nothing`() = runTest {
        subscriptions.subscribe(channel())
        subscriptions.subscribed.clear()
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.dispatch(ChannelIntent.UnsubscribeTapped)
        viewModel.dispatch(ChannelIntent.UnsubscribeConfirmed)

        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals(listOf(channel()), subscriptions.subscribed)
        assertEquals(emptyList(), storedVideos.saves)
    }

    @Test
    fun `Subscribe hands the shown channel and uploads to SubscribeWithUploads`() = runTest {
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(uploads())

        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals<List<Pair<Channel, ChannelUploads?>>>(
            listOf(channel() to uploads().value),
            subscribeWithUploads.calls,
        )
    }

    @Test
    fun `Subscribe with no uploads shown hands none`() = runTest {
        subscriptions.subscribe(channel())
        subscriptions.subscribed.clear()
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.dispatch(ChannelIntent.UnsubscribeTapped)
        viewModel.dispatch(ChannelIntent.UnsubscribeConfirmed)

        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals<List<Pair<Channel, ChannelUploads?>>>(
            listOf(channel() to null),
            subscribeWithUploads.calls,
        )
    }

    @Test
    fun `a subscription under a moved channel's old ID saves under the answered ID`() = runTest {
        subscriptions.subscribe(channel(OPENED))
        viewModel()

        uploads.loads.single().answer.complete(uploads(MOVED_TO))

        assertEquals(listOf(uploads(MOVED_TO).value), storedVideos.saves.map { it.first })
    }

    @Test
    fun `a save that throws changes nothing on the page`() = runTest {
        subscriptions.subscribe(channel())
        storedVideos.fails = true
        val viewModel = viewModel()

        uploads.loads.single().answer.complete(uploads())

        assertEquals(emptyList(), storedVideos.saves)
        assertIs<Subscription.Subscribed>(viewModel.state.value.subscription)
        assertIs<LoadArea.Loaded>(viewModel.state.value.load)
    }

    @Test
    fun `a subscribe with uploads that throws changes nothing on the page`() = runTest {
        val viewModel = viewModel()
        uploads.loads.single().answer.complete(uploads())
        storedVideos.fails = true

        viewModel.dispatch(ChannelIntent.Subscribe)

        assertEquals(listOf(channel()), subscriptions.subscribed)
        assertIs<Subscription.Subscribed>(viewModel.state.value.subscription)
    }

    @Test
    fun `a redirect's unknown card saves nothing, nor once it reads not subscribed`() = runTest {
        val held = CompletableDeferred<Unit>()
        subscriptions.heldReads[MOVED_TO] = held
        val viewModel = viewModel()

        uploads.loads.single().answer.complete(uploads(MOVED_TO))
        assertEquals(Subscription.Unknown, viewModel.state.value.subscription)
        assertEquals(emptyList(), storedVideos.saves)

        held.complete(Unit)

        assertEquals(Subscription.NotSubscribed, viewModel.state.value.subscription)
        assertEquals(emptyList(), storedVideos.saves)
    }
}
