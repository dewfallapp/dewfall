package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.data.database.FixedClock
import io.github.dewfallapp.core.database.ChannelDao
import io.github.dewfallapp.core.database.ChannelEntity
import io.github.dewfallapp.core.database.ChannelOverviewRow
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.PublishedTime
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
    private val overviews: Flow<List<ChannelOverviewRow>> = flowOf(emptyList()),
    private val gate: CompletableDeferred<Unit>? = null,
) : ChannelDao {
    val subscribed = mutableListOf<ChannelEntity>()
    val batches = mutableListOf<List<ChannelEntity>>()

    /** The IDs already subscribed, which subscribeAll gives -1 for. */
    val existing = mutableSetOf<String>()
    val unsubscribed = mutableListOf<String>()
    val askedFor = mutableListOf<String>()

    override fun subscribedChannels(): Flow<List<ChannelEntity>> = all

    override fun channelOverviews(): Flow<List<ChannelOverviewRow>> = overviews

    override fun subscribedChannel(id: String): Flow<ChannelEntity?> {
        askedFor += id
        return one
    }

    override suspend fun subscribe(channel: ChannelEntity) {
        gate?.await()
        subscribed += channel
    }

    override suspend fun subscribeAll(channels: List<ChannelEntity>): List<Long> {
        gate?.await()
        batches += channels
        return channels.mapIndexed { place, row -> if (row.id in existing) -1L else place + 1L }
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
    fun `subscribing to many saves them in one batch with one time, and counts the new ones`() =
        runTest {
            val dao = FakeChannelDao()
            dao.existing += "UCbbbbbbbbbbbbbbbbbbbbbb"
            val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)
            val second =
                Channel(ChannelId("UCbbbbbbbbbbbbbbbbbbbbbb"), "Orbit Hour", emptyList(), "")
            val third = Channel(ChannelId("UCcccccccccccccccccccccc"), "Tidewater", emptyList(), "")

            val added = repository.subscribeAll(listOf(CHANNEL, second, third))

            assertEquals(2, added)
            assertEquals(1, dao.batches.size)
            assertEquals(
                listOf(
                    "UCaaaaaaaaaaaaaaaaaaaaaa",
                    "UCbbbbbbbbbbbbbbbbbbbbbb",
                    "UCcccccccccccccccccccccc",
                ),
                dao.batches.single().map { it.id },
            )
            assertEquals(setOf(NOW), dao.batches.single().map { it.subscribedAt }.toSet())
            assertEquals(ROW, dao.batches.single().first())
        }

    @Test
    fun `subscribing to no channels touches nothing`() = runTest {
        val dao = FakeChannelDao()
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)

        assertEquals(0, repository.subscribeAll(emptyList()))
        assertEquals(emptyList(), dao.batches)
    }

    @Test
    fun `a batch subscribe finishes when the caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = FakeChannelDao(gate = gate)
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)
        val caller = launch { repository.subscribeAll(listOf(CHANNEL)) }
        runCurrent()

        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(listOf(ROW)), dao.batches)
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
    fun `overview rows come back with their last upload, sorted, without repeats`() = runTest {
        val tea = ROW.copy(id = "UCbbbbbbbbbbbbbbbbbbbbbb", name = "afternoon Tea")
        val rows =
            listOf(
                ChannelOverviewRow(ROW, lastPublishedAt = NOW, lastPublishedApproximate = true),
                ChannelOverviewRow(tea, lastPublishedAt = null, lastPublishedApproximate = null),
            )
        val dao = FakeChannelDao(overviews = flowOf(rows, rows.reversed(), emptyList()))
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)

        val teaChannel =
            CHANNEL.copy(id = ChannelId("UCbbbbbbbbbbbbbbbbbbbbbb"), name = "afternoon Tea")
        assertEquals(
            listOf(
                listOf(
                    ChannelOverview(SubscribedChannel(teaChannel, NOW), lastUpload = null),
                    ChannelOverview(
                        SubscribedChannel(CHANNEL, NOW),
                        lastUpload = PublishedTime(NOW, isApproximate = true),
                    ),
                ),
                emptyList(),
            ),
            repository.channelOverviews().toList(),
        )
    }

    @Test
    fun `an exact last upload stays exact`() = runTest {
        val row = ChannelOverviewRow(ROW, lastPublishedAt = NOW, lastPublishedApproximate = false)
        val dao = FakeChannelDao(overviews = flowOf(listOf(row)))
        val repository = RoomSubscriptionsRepository(dao, FixedClock(NOW), backgroundScope)

        assertEquals(
            PublishedTime(NOW, isApproximate = false),
            repository.channelOverviews().toList().single().single().lastUpload,
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
