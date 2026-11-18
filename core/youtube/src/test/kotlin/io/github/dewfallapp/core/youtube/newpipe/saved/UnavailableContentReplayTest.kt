package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Tests YouTube's real answers about a video or channel that can't be shown: the layer maps each to
 * its category and logs it once. A video that can't play stops after the first client's answer.
 */
class UnavailableContentReplayTest {
    private val lines = mutableListOf<String>()

    @Test
    fun `a private video is PRIVATE, after two requests`() = runTest {
        val youTube = savedYouTube(SavedSet.VIDEO_PRIVATE)

        val result = youTube.call { videoDetails(VideoId(PRIVATE_VIDEO_ID)) }

        assertEquals(YouTubeResult.Failure(YouTubeError.PRIVATE), result)
        assertEquals(listOf(failedLine("VIDEO_DETAILS", YouTubeError.PRIVATE)), lines)
        assertEquals(SavedSet.VIDEO_PRIVATE.requests, youTube.sentRequests)
        assertEquals(2, youTube.countedRequests)
    }

    @Test
    fun `an age-restricted video is UNAVAILABLE, after two requests`() = runTest {
        val youTube = savedYouTube(SavedSet.VIDEO_AGE_RESTRICTED)

        val result = youTube.call { videoDetails(VideoId(AGE_RESTRICTED_VIDEO_ID)) }

        // NewPipeExtractor v0.27.1 doesn't recognise the visionOS client's wording. See SavedSet.
        assertEquals(YouTubeResult.Failure(YouTubeError.UNAVAILABLE), result)
        assertEquals(listOf(failedLine("VIDEO_DETAILS", YouTubeError.UNAVAILABLE)), lines)
        assertEquals(SavedSet.VIDEO_AGE_RESTRICTED.requests, youTube.sentRequests)
        assertEquals(2, youTube.countedRequests)
    }

    @Test
    fun `a channel that YouTube closed is CHANNEL_CLOSED`() = runTest {
        val youTube = savedYouTube(SavedSet.CHANNEL_TERMINATED)

        // NewPipeExtractor recognises a closed channel by YouTube's English text, so this also
        // shows that the layer asks for English.
        val result = youTube.call { latestUploads(ChannelId(TERMINATED_CHANNEL_ID)) }

        assertEquals(YouTubeResult.Failure(YouTubeError.CHANNEL_CLOSED), result)
        assertEquals(listOf(failedLine("LATEST_UPLOADS", YouTubeError.CHANNEL_CLOSED)), lines)
        assertEquals(SavedSet.CHANNEL_TERMINATED.requests, youTube.sentRequests)
    }

    @Test
    fun `a deleted channel is UNAVAILABLE`() = runTest {
        val youTube = savedYouTube(SavedSet.CHANNEL_DELETED)

        val result = youTube.call { latestUploads(ChannelId(DELETED_CHANNEL_ID)) }

        assertEquals(YouTubeResult.Failure(YouTubeError.UNAVAILABLE), result)
        assertEquals(listOf(failedLine("LATEST_UPLOADS", YouTubeError.UNAVAILABLE)), lines)
        assertEquals(SavedSet.CHANNEL_DELETED.requests, youTube.sentRequests)
    }

    private fun TestScope.savedYouTube(set: SavedSet) =
        SavedYouTube(set, recordingLogger(lines), StandardTestDispatcher(testScheduler))

    /** The first line of the warning that the layer logs for a call that failed with [error]. */
    private fun failedLine(call: String, error: YouTubeError) =
        "WARN Dewfall.YouTube YouTube call failed call=$call error=$error"
}
