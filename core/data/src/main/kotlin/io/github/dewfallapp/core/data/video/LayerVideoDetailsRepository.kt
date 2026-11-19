package io.github.dewfallapp.core.data.video

import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import javax.inject.Inject

/**
 * Reads a video's details through the YouTube layer, and returns its result unchanged. Later phases
 * give it work of its own, such as reading the details of a downloaded video from the database.
 */
internal class LayerVideoDetailsRepository
@Inject
constructor(private val detailsSource: VideoDetailsRemoteDataSource) : VideoDetailsRepository {
    override suspend fun videoDetails(videoId: VideoId): YouTubeResult<VideoDetails> =
        detailsSource.videoDetails(videoId)
}
