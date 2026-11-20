package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class VideoDetailsReplayTest {
    private val lines = mutableListOf<String>()

    @Test
    fun `a video's details come from its page`() = runTest {
        val details = savedYouTube().call { videoDetails(VideoId(BIG_BUCK_BUNNY_ID)) }.valueOrFail()

        assertEquals(VideoId(BIG_BUCK_BUNNY_ID), details.id)
        assertEquals(BIG_BUCK_BUNNY_TITLE, details.title)
        assertEquals(ChannelId(BLENDER_CHANNEL_ID), details.channelId)
        assertEquals("Blender", details.channelName)
        assertTrue(details.channelAvatars.isNotEmpty())
        assertEquals(BIG_BUCK_BUNNY_DURATION, details.duration)
        // The page gives the exact time, so it isn't an estimate.
        assertEquals(BIG_BUCK_BUNNY_PUBLISHED, details.published)
        assertTrue(details.thumbnails.all { it.width != null && it.height != null })
        assertTrue(details.thumbnails.isNotEmpty())
        assertFalse(details.isLive)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a video's description is plain text, with its line breaks and runs of spaces`() = runTest {
        val details = savedYouTube().call { videoDetails(VideoId(BIG_BUCK_BUNNY_ID)) }.valueOrFail()

        assertTrue(
            details.description.startsWith(
                "UHD High Frame rate version of the iconic short film by Blender, Big Buck " +
                    "Bunny. More info:  https://",
            ),
            details.description,
        )
        assertTrue(
            details.description.contains(
                "\n\nLearn more about this UHD high frame-rate version at: " +
                    "http://bbb3d.renderfarming.net",
            ),
            details.description,
        )
        assertFalse(details.description.contains("<"), details.description)
    }

    @Test
    fun `a video's details take five requests`() = runTest {
        val youTube = savedYouTube()

        youTube.call { videoDetails(VideoId(BIG_BUCK_BUNNY_ID)) }

        assertEquals(SavedSet.VIDEO_BIG_BUCK_BUNNY.requests, youTube.sentRequests)
        assertEquals(5, youTube.countedRequests)
    }

    private fun TestScope.savedYouTube() =
        SavedYouTube(
            SavedSet.VIDEO_BIG_BUCK_BUNNY,
            recordingLogger(lines),
            StandardTestDispatcher(testScheduler),
        )
}
