package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Reads what the video screen shows about one video.
 *
 * Main-safe: call it from any thread, including the main thread. The implementation moves blocking
 * work to an injected dispatcher. Any problem with YouTube or the network comes back as a
 * [YouTubeResult.Failure], never as an exception. Cancelling the calling coroutine cancels the
 * request, and the function then throws CancellationException, the only exception it throws.
 */
fun interface VideoDetailsRemoteDataSource {
    /**
     * The details of the video with [videoId]. The layer can't read the details of a premiere or
     * live stream that hasn't started, so that call fails with [YouTubeError.UNAVAILABLE].
     */
    suspend fun videoDetails(videoId: VideoId): YouTubeResult<VideoDetails>
}
