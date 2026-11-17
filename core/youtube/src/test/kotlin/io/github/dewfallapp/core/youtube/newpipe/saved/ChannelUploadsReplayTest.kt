package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** How YouTube writes the age of a video in years, as jawed's channel page shows it. */
private val YEARS_AGO = Regex(""""(\d+) years ago"""")

class ChannelUploadsReplayTest {
    private val lines = mutableListOf<String>()

    @Test
    fun `a channel's Videos and Live tabs make one list, newest first`() = runTest {
        val uploads = latestUploads(SavedSet.UPLOADS_NASA, NASA_CHANNEL_ID)

        assertEquals(ChannelId(NASA_CHANNEL_ID), uploads.channel.id)
        assertEquals("NASA", uploads.channel.name)
        assertTrue(uploads.channel.avatars.isNotEmpty())
        assertTrue(uploads.channel.description.isNotEmpty())
        assertKeepsChannelUploadsPromises(uploads)
        // A stream on air has no length.
        assertTrue(uploads.videos.filter { it.kind == VideoKind.LIVE }.all { it.duration == null })
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a channel with a Live tab takes two requests`() = runTest {
        val youTube = savedYouTube(SavedSet.UPLOADS_NASA)

        youTube.call { latestUploads(ChannelId(NASA_CHANNEL_ID)) }

        assertEquals(SavedSet.UPLOADS_NASA.requests, youTube.sentRequests)
        assertEquals(2, youTube.countedRequests)
    }

    @Test
    fun `a channel without a Live tab takes one request`() = runTest {
        val youTube = savedYouTube(SavedSet.UPLOADS_JAWED)

        val uploads = youTube.call { latestUploads(ChannelId(JAWED_CHANNEL_ID)) }.valueOrFail()

        assertEquals("jawed", uploads.channel.name)
        val video = uploads.videos.single()
        assertEquals(VideoId(ME_AT_THE_ZOO_ID), video.id)
        assertEquals(ME_AT_THE_ZOO_TITLE, video.title)
        assertEquals(ME_AT_THE_ZOO_DURATION, video.duration)
        assertEquals(VideoKind.VIDEO, video.kind)
        assertEquals(SavedSet.UPLOADS_JAWED.requests, youTube.sentRequests)
        assertEquals(1, youTube.countedRequests)
    }

    @Test
    fun `a relative publish time is an estimate that counts back from the day the test runs`() =
        runTest {
            val youTube = savedYouTube(SavedSet.UPLOADS_JAWED)
            // The saved answer gives the age that YouTube showed on the day it was recorded.
            val savedAge =
                youTube.saved.exchanges.firstNotNullOf { YEARS_AGO.find(it.response.body) }

            val before = Clock.System.now()
            val uploads = youTube.call { latestUploads(ChannelId(JAWED_CHANNEL_ID)) }.valueOrFail()
            val after = Clock.System.now()

            assertAgo(
                uploads.videos.single().published,
                savedAge.groupValues[1].toLong(),
                ChronoUnit.YEARS,
                before,
                after,
            )
        }

    @Test
    fun `an age-restricted channel's uploads come from its system playlists`() = runTest {
        val youTube = savedYouTube(SavedSet.UPLOADS_AGE_RESTRICTED)

        val uploads =
            youTube.call { latestUploads(ChannelId(AGE_RESTRICTED_CHANNEL_ID)) }.valueOrFail()

        assertEquals("Laphroaig Whisky", uploads.channel.name)
        // An age-restricted channel's page has no description.
        assertEquals("", uploads.channel.description)
        assertKeepsChannelUploadsPromises(uploads)
        // The channel's page, then two requests for each of the two system playlists.
        assertEquals(SavedSet.UPLOADS_AGE_RESTRICTED.requests, youTube.sentRequests)
        assertEquals(5, youTube.countedRequests)
        assertEquals(emptyList(), lines)
    }

    private suspend fun TestScope.latestUploads(set: SavedSet, channelId: String): ChannelUploads =
        savedYouTube(set).call { latestUploads(ChannelId(channelId)) }.valueOrFail()

    private fun TestScope.savedYouTube(set: SavedSet) =
        SavedYouTube(set, recordingLogger(lines), StandardTestDispatcher(testScheduler))

    /**
     * Asserts what ChannelUploads promises about its videos: each one takes the channel's ID and
     * name, none is a Short, none appears twice, and they are sorted newest first, with the videos
     * that have no publish time last.
     */
    private fun assertKeepsChannelUploadsPromises(uploads: ChannelUploads) {
        val videos = uploads.videos
        assertTrue(videos.isNotEmpty())
        assertTrue(
            videos.all { it.channelId == uploads.channel.id },
            "A video names another channel",
        )
        assertTrue(videos.all { it.channelName == uploads.channel.name })
        assertTrue(videos.none { it.kind == VideoKind.SHORT })
        assertEquals(videos.size, videos.map { it.id }.toSet().size)
        val times = videos.map { it.published?.instant }
        val dated = times.takeWhile { it != null }.filterNotNull()
        assertTrue(
            times.drop(dated.size).all { it == null },
            "A video with a publish time comes after one without",
        )
        assertEquals(dated.sortedDescending(), dated)
    }
}
