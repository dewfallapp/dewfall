package io.github.dewfallapp.core.data.video

import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Reads what the video screen shows about one video. Features reach YouTube only through
 * repositories like this one, never through the YouTube layer itself. See
 * docs/architecture.md#reading-from-youtube.
 *
 * Main-safe, and any problem comes back as a [YouTubeResult.Failure]. It throws only
 * CancellationException, when the calling coroutine is cancelled.
 */
fun interface VideoDetailsRepository {
    /**
     * The details of the video with [videoId], as YouTube gave them. A premiere or live stream that
     * hasn't started fails with [YouTubeError.UNAVAILABLE]. The ID and the details are user data,
     * so never log them.
     */
    suspend fun videoDetails(videoId: VideoId): YouTubeResult<VideoDetails>
}
