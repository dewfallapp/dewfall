package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Reads a channel and its latest uploads, for the channel page and the inbox.
 *
 * Main-safe: call it from any thread, including the main thread. The implementation moves blocking
 * work to its own dispatcher. Any problem with YouTube or the network comes back as a
 * [YouTubeResult.Failure], never as an exception. Cancelling the calling coroutine cancels the
 * request, and the function then throws CancellationException, the only exception it throws.
 */
fun interface ChannelUploadsRemoteDataSource {
    /**
     * The channel with [channelId], with its latest long-form videos and live streams, newest
     * first, and never Shorts. A premiere or live stream that hasn't started is marked
     * [VideoKind.UPCOMING].
     */
    suspend fun latestUploads(channelId: ChannelId): YouTubeResult<ChannelUploads>
}
