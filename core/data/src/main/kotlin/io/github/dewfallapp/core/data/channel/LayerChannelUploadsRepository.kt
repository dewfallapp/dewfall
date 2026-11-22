package io.github.dewfallapp.core.data.channel

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import javax.inject.Inject

/**
 * Reads a channel's latest uploads through the YouTube layer, and returns its result unchanged.
 * Later tasks may give it work of its own, such as reading a channel that the phone keeps.
 */
internal class LayerChannelUploadsRepository
@Inject
constructor(private val uploadsSource: ChannelUploadsRemoteDataSource) : ChannelUploadsRepository {
    override suspend fun latestUploads(channelId: ChannelId): YouTubeResult<ChannelUploads> =
        uploadsSource.latestUploads(channelId)
}
