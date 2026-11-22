package io.github.dewfallapp.core.data.watch

import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.database.toWatchState
import io.github.dewfallapp.core.database.WatchStateDao
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.WatchState
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Keeps the watch states in the database's `watch_state` table. */
internal class RoomWatchStateRepository
@Inject
constructor(
    private val watchStateDao: WatchStateDao,
    private val clock: Clock,
    @ApplicationScope private val externalScope: CoroutineScope,
) : WatchStateRepository {
    override fun watchState(videoId: VideoId): Flow<WatchState?> =
        watchStateDao.watchState(videoId.value).map { it?.toWatchState() }.distinctUntilChanged()

    override suspend fun savePosition(videoId: VideoId, position: Duration) {
        val now = clock.now()
        externalScope
            .async { watchStateDao.savePosition(videoId.value, position.inWholeMilliseconds, now) }
            .await()
    }

    override suspend fun markWatched(videoId: VideoId) {
        val now = clock.now()
        externalScope.async { watchStateDao.markWatched(videoId.value, now) }.await()
    }

    override suspend fun markUnwatched(videoId: VideoId) {
        val now = clock.now()
        externalScope.async { watchStateDao.markUnwatched(videoId.value, now) }.await()
    }
}
