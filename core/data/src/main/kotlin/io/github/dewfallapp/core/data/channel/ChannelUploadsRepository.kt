package io.github.dewfallapp.core.data.channel

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Reads a channel and its latest uploads for the channel page. Features reach YouTube only through
 * repositories like this one, never through the YouTube layer itself. See
 * docs/architecture.md#reading-from-youtube.
 *
 * Main-safe, and any problem comes back as a [YouTubeResult.Failure]. It throws only
 * CancellationException, when the calling coroutine is cancelled.
 */
fun interface ChannelUploadsRepository {
    /**
     * The channel with [channelId] and its latest long-form videos and live streams, as YouTube
     * gave them and the YouTube layer ordered them. The channel's ID can differ from [channelId],
     * because YouTube sends some channels on to another. The ID and the answer are user data, so
     * never log them.
     */
    suspend fun latestUploads(channelId: ChannelId): YouTubeResult<ChannelUploads>
}
