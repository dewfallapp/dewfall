package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.database.toChannelOverview
import io.github.dewfallapp.core.data.database.toEntity
import io.github.dewfallapp.core.data.database.toSubscribedChannel
import io.github.dewfallapp.core.database.ChannelDao
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.SubscribedChannel
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Keeps the subscriptions in the database's `channels` table. */
internal class RoomSubscriptionsRepository
@Inject
constructor(
    private val channelDao: ChannelDao,
    private val clock: Clock,
    @ApplicationScope private val externalScope: CoroutineScope,
) : SubscriptionsRepository {
    override fun subscribedChannels(): Flow<List<SubscribedChannel>> =
        channelDao
            .subscribedChannels()
            .map { rows -> rows.map { it.toSubscribedChannel() } }
            .distinctUntilChanged()

    override fun channelOverviews(): Flow<List<ChannelOverview>> =
        channelDao
            .channelOverviews()
            .map { rows -> sortedByName(rows.map { it.toChannelOverview() }, Locale.getDefault()) }
            .distinctUntilChanged()

    override fun subscribedChannel(channelId: ChannelId): Flow<SubscribedChannel?> =
        channelDao
            .subscribedChannel(channelId.value)
            .map { it?.toSubscribedChannel() }
            .distinctUntilChanged()

    override fun isSubscribed(channelId: ChannelId): Flow<Boolean> =
        subscribedChannel(channelId).map { it != null }.distinctUntilChanged()

    override suspend fun subscribe(channel: Channel) {
        val row = channel.toEntity(subscribedAt = clock.now())
        externalScope.async { channelDao.subscribe(row) }.await()
    }

    override suspend fun subscribeAll(channels: List<Channel>): Int {
        if (channels.isEmpty()) return 0
        val now = clock.now()
        val rows = channels.map { it.toEntity(subscribedAt = now) }
        return externalScope.async { channelDao.subscribeAll(rows).count { it != -1L } }.await()
    }

    override suspend fun unsubscribe(channelId: ChannelId) {
        externalScope.async { channelDao.unsubscribe(channelId.value) }.await()
    }
}
