package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The Blender Foundation's channel, which published Big Buck Bunny. */
private const val BLENDER_CHANNEL_ID = "UCSMOQeBJ2RAnuFungnQOxLg"

class VideoDetailsReplayTest {
    private val lines = mutableListOf<String>()

    @Test
    fun `a video's details come from its page`() = runTest {
        val details = savedYouTube().call { videoDetails(VideoId(BIG_BUCK_BUNNY_ID)) }.valueOrFail()

        assertEquals(VideoId(BIG_BUCK_BUNNY_ID), details.id)
        assertEquals(
            "Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film",
            details.title,
        )
        assertEquals(ChannelId(BLENDER_CHANNEL_ID), details.channelId)
        assertEquals("Blender", details.channelName)
        assertTrue(details.channelAvatars.isNotEmpty())
        assertEquals(10.minutes + 35.seconds, details.duration)
        // The page gives the exact time, so it isn't an estimate.
        assertEquals(
            PublishedTime(Instant.parse("2014-11-10T14:05:55Z"), isApproximate = false),
            details.published,
        )
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
                    "Bunny. More info:  https://"
            ),
            details.description,
        )
        assertTrue(
            details.description.contains(
                "\n\nLearn more about this UHD high frame-rate version at: " +
                    "http://bbb3d.renderfarming.net"
            ),
            details.description,
        )
        assertFalse(details.description.contains("<"), details.description)
    }

    @Test
    fun `a video's details take seven requests`() = runTest {
        val youTube = savedYouTube()

        youTube.call { videoDetails(VideoId(BIG_BUCK_BUNNY_ID)) }

        assertEquals(SavedSet.VIDEO_BIG_BUCK_BUNNY.requests, youTube.sentRequests)
        assertEquals(7, youTube.countedRequests)
    }

    private fun TestScope.savedYouTube() =
        SavedYouTube(
            SavedSet.VIDEO_BIG_BUCK_BUNNY,
            recordingLogger(lines),
            StandardTestDispatcher(testScheduler),
        )
}
