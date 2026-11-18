package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamType

class NewPipeVideoDetailsTest {
    private val lines = mutableListOf<String>()
    private val skipped = SkippedParts(YouTubeCall.VIDEO_DETAILS)

    @BeforeTest
    fun setUp() {
        setUpNewPipeWithoutRequests()
    }

    @Test
    fun `a video's details map every field`() {
        val avatars = listOf(image(width = 48, height = 48))
        val thumbnails = listOf(image(width = 1280, height = 720))
        val date = DateWrapper(Instant.parse("2005-04-24T03:31:52Z"), false)
        val page =
            FakeStreamPage(
                videoId = VIDEO_A,
                name = { "Title" },
                uploaderLink = channelLink(CHANNEL_A),
                uploaderName = "Channel",
                description = { Description("Line one<br>Line two", Description.Type.HTML) },
                length = { 19 },
                uploadDate = { date },
                uploaderAvatars = { avatars },
                thumbnails = { thumbnails },
            )

        assertEquals(
            VideoDetails(
                id = VideoId(VIDEO_A),
                title = "Title",
                channelId = ChannelId(CHANNEL_A),
                channelName = "Channel",
                channelAvatars = listOf(Thumbnail(avatars[0].url, width = 48, height = 48)),
                description = "Line one\nLine two",
                duration = 19.seconds,
                published = date.toPublishedTime(),
                thumbnails = listOf(Thumbnail(thumbnails[0].url, width = 1280, height = 720)),
                isLive = false,
            ),
            videoDetailsOf(VideoId(VIDEO_A), page, skipped),
        )
    }

    @Test
    fun `a live stream has no length and is live`() {
        val page = FakeStreamPage(streamType = StreamType.LIVE_STREAM, length = { 0 })

        val details = videoDetailsOf(VideoId(VIDEO_A), page, skipped)

        assertTrue(details.isLive)
        assertNull(details.duration)
    }

    @Test
    fun `a live stream that has just ended is still live`() {
        val page = FakeStreamPage(streamType = StreamType.POST_LIVE_STREAM, length = { 3600 })

        val details = videoDetailsOf(VideoId(VIDEO_A), page, skipped)

        assertTrue(details.isLive)
        assertNull(details.duration)
    }

    @Test
    fun `an unknown length is null, and missing optional parts log nothing`() {
        val details = videoDetailsOf(VideoId(VIDEO_A), FakeStreamPage(length = { 0 }), skipped)
        skipped.warnIfAny(recordingLogger(lines).withTag(LOG_AREA))

        assertFalse(details.isLive)
        assertNull(details.duration)
        assertNull(details.published)
        assertEquals("", details.description)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `an unreadable optional part is left empty and counted`() {
        val page =
            FakeStreamPage(
                uploadDate = { throw ParsingException("Could not parse") },
                uploaderAvatars = { throw ParsingException("Could not get avatars") },
            )

        val details = videoDetailsOf(VideoId(VIDEO_A), page, skipped)
        skipped.warnIfAny(recordingLogger(lines).withTag(LOG_AREA))

        assertNull(details.published)
        assertEquals(emptyList(), details.channelAvatars)
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=VIDEO_DETAILS dropped=0 " +
                    "parseErrors=2"
            ),
            lines,
        )
    }

    @Test
    fun `a video without a channel ID or a channel name fails`() {
        val pages =
            listOf(
                FakeStreamPage(uploaderLink = "https://www.youtube.com/@handle"),
                FakeStreamPage(uploaderLink = ""),
                FakeStreamPage(uploaderName = " "),
            )

        for (page in pages) {
            assertFailsWith<MissingFieldException> {
                videoDetailsOf(VideoId(VIDEO_A), page, skipped)
            }
        }
    }

    @Test
    fun `a title that NewPipeExtractor can't read fails the call`() {
        val page = FakeStreamPage(name = { throw ParsingException("Could not get name") })

        assertFailsWith<ParsingException> { videoDetailsOf(VideoId(VIDEO_A), page, skipped) }
    }
}
