package io.github.dewfallapp.core.data.playback

import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Reads the links that play a video, for the player. Features and the player reach YouTube only
 * through repositories like this one, never through the YouTube layer itself. See
 * docs/architecture.md#reading-from-youtube.
 *
 * Main-safe, and any problem comes back as a [YouTubeResult.Failure]. It throws only
 * CancellationException, when the calling coroutine is cancelled.
 */
fun interface StreamLinksRepository {
    /**
     * The links of the video with [videoId], as YouTube gave them. They expire, so fetch them just
     * before playing. The ID and the links are user data, so never log them.
     */
    suspend fun streamLinks(videoId: VideoId): YouTubeResult<StreamLinks>
}
