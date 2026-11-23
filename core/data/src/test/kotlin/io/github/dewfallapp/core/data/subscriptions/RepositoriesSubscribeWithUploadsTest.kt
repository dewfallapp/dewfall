package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val CHANNEL =
    Channel(ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"), "Kettle and Crumb", emptyList(), "")
private val UPLOADS = ChannelUploads(CHANNEL, videos = emptyList())

/** Records both repositories' writes in one list, in the order they ran. */
private class Writes {
    val done = mutableListOf<String>()
}

/** Records subscribes, and can hold one until the test lets it go. */
private class FakeSubscriptions(
    private val writes: Writes,
    private val gate: CompletableDeferred<Unit>? = null,
) : SubscriptionsRepository {
    override fun subscribedChannels(): Flow<List<SubscribedChannel>> = emptyFlow()

    override fun channelOverviews(): Flow<List<ChannelOverview>> = emptyFlow()

    override fun subscribedChannel(channelId: ChannelId): Flow<SubscribedChannel?> = emptyFlow()

    override fun isSubscribed(channelId: ChannelId): Flow<Boolean> = emptyFlow()

    override suspend fun subscribe(channel: Channel) {
        gate?.await()
        writes.done += "subscribe"
    }

    override suspend fun subscribeAll(channels: List<Channel>): Int = error("Not used")

    override suspend fun unsubscribe(channelId: ChannelId) = error("Not used")

    override suspend fun moveSubscription(from: ChannelId, to: Channel) = error("Not used")
}

/** Records saves. */
private class FakeStoredVideos(private val writes: Writes) : StoredVideosRepository {
    override suspend fun saveUploads(uploads: ChannelUploads) {
        writes.done += "save"
    }

    override fun inboxVideos(): Flow<List<VideoSummary>> = emptyFlow()
}

// runCurrent is still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoriesSubscribeWithUploadsTest {
    private val writes = Writes()

    @Test
    fun `it subscribes, then saves the uploads`() = runTest {
        val subscribe =
            RepositoriesSubscribeWithUploads(
                FakeSubscriptions(writes),
                FakeStoredVideos(writes),
                backgroundScope,
            )

        subscribe.subscribe(CHANNEL, UPLOADS)

        assertEquals(listOf("subscribe", "save"), writes.done)
    }

    @Test
    fun `with no uploads it only subscribes`() = runTest {
        val subscribe =
            RepositoriesSubscribeWithUploads(
                FakeSubscriptions(writes),
                FakeStoredVideos(writes),
                backgroundScope,
            )

        subscribe.subscribe(CHANNEL, uploads = null)

        assertEquals(listOf("subscribe"), writes.done)
    }

    @Test
    fun `both writes finish when the caller is cancelled during the first`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val subscribe =
            RepositoriesSubscribeWithUploads(
                FakeSubscriptions(writes, gate),
                FakeStoredVideos(writes),
                backgroundScope,
            )
        val caller = launch { subscribe.subscribe(CHANNEL, UPLOADS) }
        runCurrent()

        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("subscribe", "save"), writes.done)
    }
}
