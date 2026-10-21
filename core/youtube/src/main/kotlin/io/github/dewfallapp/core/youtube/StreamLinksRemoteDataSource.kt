package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Reads the links that play or download a video.
 *
 * Main-safe: call it from any thread, including the main thread. The implementation moves blocking
 * work to its own dispatcher. Any problem with YouTube or the network comes back as a
 * [YouTubeResult.Failure], never as an exception. Cancelling the calling coroutine cancels the
 * request, and the function then throws CancellationException, the only exception it throws.
 */
fun interface StreamLinksRemoteDataSource {
    /**
     * The links for the video with [videoId]. They stop working after some time, so fetch them just
     * before use. See [StreamLinks].
     */
    suspend fun streamLinks(videoId: VideoId): YouTubeResult<StreamLinks>
}
