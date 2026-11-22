package io.github.dewfallapp.core.data.video

import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.database.toDetails
import io.github.dewfallapp.core.data.database.toEntities
import io.github.dewfallapp.core.database.VideoDao
import io.github.dewfallapp.core.model.ChannelUploads
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/** Keeps the videos in the database's `videos` table. */
internal class RoomStoredVideosRepository
@Inject
constructor(
    private val videoDao: VideoDao,
    private val clock: Clock,
    @ApplicationScope private val externalScope: CoroutineScope,
) : StoredVideosRepository {
    override suspend fun saveUploads(uploads: ChannelUploads) {
        val details = uploads.channel.toDetails()
        val videos = uploads.toEntities(now = clock.now())
        externalScope.async { videoDao.saveChannelUploads(details, videos) }.await()
    }
}
