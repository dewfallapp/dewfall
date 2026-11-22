package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.data.database.FixedClock
import io.github.dewfallapp.core.database.ChannelDao
import io.github.dewfallapp.core.database.ChannelEntity
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.Thumbnail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val NOW = Instant.parse("2026-11-22T12:00:00Z")
private val CHANNEL =
    Channel(
        id = ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"),
        name = "Kettle and Crumb",
        avatars = listOf(Thumbnail("https://yt3.example.invalid/a", 88, 88)),
        description = "Bread.",
    )
private val ROW =
    ChannelEntity(
        id = "UCaaaaaaaaaaaaaaaaaaaaaa",
        name = "Kettle and Crumb",
        description = "Bread.",
        avatars = listOf(Thumbnail("https://yt3.example.invalid/a", 88, 88)),
        subscribedAt = NOW,
    )

/** Records what it was asked, and returns the rows each test gives it. */
private class FakeChannelDao(
    private val all: Flow<List<ChannelEntity>> = flowOf(emptyList()),
    private val one: Flow<ChannelEntity?> = flowOf(null),
    private val gate: CompletableDeferred<Unit>? = null,
) : ChannelDao {
    val subscribed = mutableListOf<ChannelEntity>()
    val unsubscribed = mutableListOf<String>()
    val askedFor = mutableListOf<String>()

    override fun subscribedChannels(): Flow<List<ChannelEntity>> = all

    override fun subscribedChannel(id: String): Flow<ChannelEntity?> {
        askedFor += id
        return one
    }

    override suspend fun subscribe(channel: ChannelEntity) {
        gate?.await()
        subscribed += channel
    }

    override suspend fun unsubscribe(id: String) {
        gate?.await()
        unsubscribed += id
    }
}

// runCurrent is still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSubscriptionsRepositoryTest {
    @Test
    fun `subscribing saves the channel with the clock's time`() = runTest {
        val dao = FakeChannelDao()
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)

        repository.subscribe(CHANNEL)

        assertEquals(listOf(ROW), dao.subscribed)
    }

    @Test
    fun `unsubscribing passes the ID`() = runTest {
        val dao = FakeChannelDao()
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)

        repository.unsubscribe(CHANNEL.id)

        assertEquals(listOf("UCaaaaaaaaaaaaaaaaaaaaaa"), dao.unsubscribed)
    }

    @Test
    fun `rows come back as subscribed channels, without repeats`() = runTest {
        val dao = FakeChannelDao(all = flowOf(listOf(ROW), listOf(ROW), emptyList()))
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)

        assertEquals(
            listOf(listOf(SubscribedChannel(CHANNEL, NOW)), emptyList()),
            repository.subscribedChannels().toList(),
        )
    }

    @Test
    fun `one channel and whether it is subscribed`() = runTest {
        val dao = FakeChannelDao(one = flowOf(null, ROW, ROW.copy(name = "Renamed"), null))
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)

        assertEquals(
            listOf(
                null,
                SubscribedChannel(CHANNEL, NOW),
                SubscribedChannel(CHANNEL.copy(name = "Renamed"), NOW),
                null,
            ),
            repository.subscribedChannel(CHANNEL.id).toList(),
        )
        assertEquals(listOf(false, true, false), repository.isSubscribed(CHANNEL.id).toList())
        assertEquals(List(2) { "UCaaaaaaaaaaaaaaaaaaaaaa" }, dao.askedFor)
    }

    @Test
    fun `a subscribe finishes when the caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = FakeChannelDao(gate = gate)
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)
        val caller = launch { repository.subscribe(CHANNEL) }
        runCurrent()

        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(ROW), dao.subscribed)
    }

    @Test
    fun `an unsubscribe finishes when the caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = FakeChannelDao(gate = gate)
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)
        val caller = launch { repository.unsubscribe(CHANNEL.id) }
        runCurrent()

        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("UCaaaaaaaaaaaaaaaaaaaaaa"), dao.unsubscribed)
    }
}
