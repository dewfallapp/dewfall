package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.video.StoredVideosRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelUploads
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/** Runs the two repositories' writes, in order, in the app-wide scope. */
internal class RepositoriesSubscribeWithUploads
@Inject
constructor(
    private val subscriptions: SubscriptionsRepository,
    private val storedVideos: StoredVideosRepository,
    @ApplicationScope private val externalScope: CoroutineScope,
) : SubscribeWithUploads {
    override suspend fun subscribe(channel: Channel, uploads: ChannelUploads?) {
        externalScope
            .async {
                subscriptions.subscribe(channel)
                if (uploads != null) storedVideos.saveUploads(uploads)
            }
            .await()
    }
}
