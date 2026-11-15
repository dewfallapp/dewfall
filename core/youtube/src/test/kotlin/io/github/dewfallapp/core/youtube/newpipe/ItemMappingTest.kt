package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinInstant
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.StreamType

class ItemMappingTest {
    @Test
    fun `a video's ID comes from its watch link`() {
        assertEquals(VideoId(VIDEO_A), videoIdFromLink(watchLink(VIDEO_A)))
    }

    @Test
    fun `a link without a well-formed video ID gives no ID`() {
        val links =
            listOf(
                "https://www.youtube.com/watch?v=short",
                "https://www.youtube.com/watch",
                "https://www.youtube.com/shorts/$VIDEO_A",
                "not a link",
                null,
            )

        for (link in links) {
            assertNull(videoIdFromLink(link), link)
        }
    }

    @Test
    fun `a channel's ID comes from its link, with or without a tab`() {
        assertEquals(ChannelId(CHANNEL_A), channelIdFromLink(channelLink(CHANNEL_A)))
        assertEquals(ChannelId(CHANNEL_A), channelIdFromLink("${channelLink(CHANNEL_A)}/streams"))
    }

    @Test
    fun `a link that isn't a channel ID link gives no ID`() {
        val links =
            listOf(
                "https://www.youtube.com/@handle",
                "https://www.youtube.com/user/name",
                "https://www.youtube.com/channel/notAnId",
                "https://www.youtube.com/channel",
                "not a link",
                null,
            )

        for (link in links) {
            assertNull(channelIdFromLink(link), link)
        }
    }

    @Test
    fun `thumbnails keep every size, and an unknown size is null`() {
        val thumbnails = listOf(image(width = 120, height = 90), image(width = -1, height = -1))

        assertEquals(
            listOf(
                Thumbnail(thumbnails[0].url, width = 120, height = 90),
                Thumbnail(thumbnails[1].url, width = null, height = null),
            ),
            thumbnails.toThumbnails(),
        )
    }

    @Test
    fun `a publish time keeps its instant and whether it is approximate`() {
        val instant = Instant.parse("2026-10-01T12:00:00Z")

        assertEquals(
            PublishedTime(instant.toKotlinInstant(), isApproximate = true),
            DateWrapper(instant, true).toPublishedTime(),
        )
    }

    @Test
    fun `an upcoming item is UPCOMING, even when it is marked live or short`() {
        val upcoming = ContentAvailability.UPCOMING

        assertEquals(VideoKind.UPCOMING, streamItem(availability = upcoming).kind())
        assertEquals(
            VideoKind.UPCOMING,
            streamItem(availability = upcoming, streamType = StreamType.LIVE_STREAM).kind(),
        )
        assertEquals(VideoKind.UPCOMING, streamItem(availability = upcoming, isShort = true).kind())
    }

    @Test
    fun `a live item is LIVE, even when it is marked short`() {
        assertEquals(VideoKind.LIVE, streamItem(streamType = StreamType.LIVE_STREAM).kind())
        assertEquals(VideoKind.LIVE, streamItem(streamType = StreamType.AUDIO_LIVE_STREAM).kind())
        assertEquals(
            VideoKind.LIVE,
            streamItem(streamType = StreamType.LIVE_STREAM, isShort = true).kind(),
        )
    }

    @Test
    fun `a Short is SHORT, and anything else is VIDEO`() {
        assertEquals(VideoKind.SHORT, streamItem(isShort = true).kind())
        assertEquals(VideoKind.VIDEO, streamItem().kind())
    }

    @Test
    fun `a video item maps every field`() {
        val date = DateWrapper(Instant.parse("2026-10-01T12:00:00Z"), false)
        val item =
            streamItem(
                videoId = VIDEO_A,
                title = "Title",
                uploaderLink = channelLink(CHANNEL_A),
                uploaderName = "Channel",
                durationSeconds = 90,
                uploadDate = date,
                thumbnails = listOf(image(width = 120, height = 90)),
            )

        assertEquals(
            VideoSummary(
                id = VideoId(VIDEO_A),
                title = "Title",
                channelId = ChannelId(CHANNEL_A),
                channelName = "Channel",
                duration = 90.seconds,
                published = date.toPublishedTime(),
                thumbnails = listOf(Thumbnail(item.thumbnails[0].url, width = 120, height = 90)),
                kind = VideoKind.VIDEO,
            ),
            item.toVideoSummaryOrNull(),
        )
    }

    @Test
    fun `only a video that can be watched to the end has a length`() {
        assertEquals(
            30.seconds,
            streamItem(isShort = true, durationSeconds = 30).summary().duration,
        )
        assertNull(streamItem(durationSeconds = -1).summary().duration)
        assertNull(streamItem(streamType = StreamType.LIVE_STREAM).summary().duration)
        assertNull(streamItem(availability = ContentAvailability.UPCOMING).summary().duration)
    }

    @Test
    fun `a video item without a required field is left out`() {
        val items =
            listOf(
                streamItem(videoId = "not an ID"),
                streamItem(title = null),
                streamItem(uploaderLink = null),
                streamItem(uploaderLink = "https://www.youtube.com/@handle"),
                streamItem(uploaderName = null),
                streamItem(uploaderName = " "),
            )

        for (item in items) {
            assertNull(item.toVideoSummaryOrNull(), item.url)
        }
    }

    @Test
    fun `an untitled video keeps its empty title`() {
        assertEquals("", streamItem(title = "").summary().title)
    }

    @Test
    fun `a channel tab's item takes the channel's ID and name`() {
        val item =
            streamItem(uploaderLink = "${channelLink(CHANNEL_B)}/streams", uploaderName = "Tab")

        val video = item.toVideoSummaryOrNull(ChannelId(CHANNEL_A), "Channel")

        assertEquals(ChannelId(CHANNEL_A), video?.channelId)
        assertEquals("Channel", video?.channelName)
    }

    @Test
    fun `a channel item maps every field, and a missing description is empty`() {
        val avatars = listOf(image(width = 88, height = 88))
        val item = channelItem(channelId = CHANNEL_A, name = "Channel", description = null)
        item.thumbnails = avatars

        assertEquals(
            Channel(
                ChannelId(CHANNEL_A),
                "Channel",
                listOf(Thumbnail(avatars[0].url, width = 88, height = 88)),
                description = "",
            ),
            item.toChannelOrNull(),
        )
    }

    @Test
    fun `a channel item without an ID or a name is left out`() {
        assertNull(channelItem(channelId = "notAnId").toChannelOrNull())
        assertNull(channelItem(name = null).toChannelOrNull())
        assertNull(channelItem(name = "").toChannelOrNull())
    }

    private fun org.schabi.newpipe.extractor.stream.StreamInfoItem.summary(): VideoSummary =
        checkNotNull(toVideoSummaryOrNull())
}
