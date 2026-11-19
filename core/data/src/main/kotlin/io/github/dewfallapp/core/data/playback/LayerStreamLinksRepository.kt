package io.github.dewfallapp.core.data.playback

import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import javax.inject.Inject

/** Reads a video's stream links through the YouTube layer, and returns its result unchanged. */
internal class LayerStreamLinksRepository
@Inject
constructor(private val linksSource: StreamLinksRemoteDataSource) : StreamLinksRepository {
    override suspend fun streamLinks(videoId: VideoId): YouTubeResult<StreamLinks> =
        linksSource.streamLinks(videoId)
}
