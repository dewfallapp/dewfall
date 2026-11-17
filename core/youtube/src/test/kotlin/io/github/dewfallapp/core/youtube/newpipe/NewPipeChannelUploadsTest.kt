package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ReadyChannelTabListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeChannelTabExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeChannelTabPlaylistExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory

class NewPipeChannelUploadsTest {
    private val channel = Channel(ChannelId(CHANNEL_A), "Channel", emptyList(), "About")

    @BeforeTest
    fun setUp() {
        setUpNewPipeWithoutRequests()
    }

    @Test
    fun `every video takes the channel's ID and name`() {
        val liveTabItem =
            streamItem(
                videoId = VIDEO_A,
                uploaderLink = "${channelLink(CHANNEL_B)}/streams",
                uploaderName = "Live",
            )

        val videos =
            latestUploadsOf(
                channel,
                listOf(emptyList(), listOf(liveTabItem)),
                SkippedParts(YouTubeCall.LATEST_UPLOADS),
            )

        assertEquals(listOf(ChannelId(CHANNEL_A)), videos.map { it.channelId })
        assertEquals(listOf("Channel"), videos.map { it.channelName })
    }

    @Test
    fun `the Videos tab comes before the Live tab, and a video in both is kept once`() {
        val sameDay = DateWrapper(Instant.parse("2026-10-01T00:00:00Z"), true)
        val videosTab =
            listOf(
                streamItem(videoId = VIDEO_A, uploadDate = sameDay),
                streamItem(videoId = VIDEO_B, uploadDate = sameDay),
            )
        val liveTab =
            listOf(
                streamItem(videoId = VIDEO_C, uploadDate = sameDay),
                streamItem(videoId = VIDEO_A, uploadDate = sameDay),
            )

        val videos =
            latestUploadsOf(
                channel,
                listOf(videosTab, liveTab),
                SkippedParts(YouTubeCall.LATEST_UPLOADS),
            )

        assertEquals(listOf(VIDEO_A, VIDEO_B, VIDEO_C).map(::VideoId), videos.map { it.id })
    }

    @Test
    fun `a video without an ID or a title is dropped and counted`() {
        val lines = mutableListOf<String>()
        val skipped = SkippedParts(YouTubeCall.LATEST_UPLOADS)
        val items =
            listOf(
                streamItem(videoId = VIDEO_A),
                streamItem(videoId = "x"),
                streamItem(title = null),
            )

        val videos = latestUploadsOf(channel, listOf(items), skipped)
        skipped.warnIfAny(recordingLogger(lines).withTag(LOG_AREA))

        assertEquals(listOf(VideoId(VIDEO_A)), videos.map { it.id })
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=LATEST_UPLOADS dropped=2 " +
                    "parseErrors=0"
            ),
            lines,
        )
    }

    @Test
    fun `the layer reads the Videos tab, then the Live tab, and never the Shorts`() {
        val tabs =
            listOf(ChannelTabs.LIVESTREAMS, ChannelTabs.SHORTS, ChannelTabs.VIDEOS).map { tab ->
                YoutubeChannelTabLinkHandlerFactory.getInstance()
                    .fromQuery(CHANNEL_A, listOf(tab), "")
            }

        val extractors = uploadTabExtractors(tabs, RequestSettings(ContentCountry("FR")))

        assertEquals(
            listOf(ChannelTabs.VIDEOS, ChannelTabs.LIVESTREAMS),
            extractors.map { it.name },
        )
        extractors.forEach { assertIs<YoutubeChannelTabExtractor>(it) }
    }

    @Test
    fun `a channel without a Live tab gets only its Videos tab`() {
        val tabs =
            listOf(
                YoutubeChannelTabLinkHandlerFactory.getInstance()
                    .fromQuery(CHANNEL_A, listOf(ChannelTabs.VIDEOS), "")
            )

        val extractors = uploadTabExtractors(tabs, RequestSettings(ContentCountry("FR")))

        assertEquals(listOf(ChannelTabs.VIDEOS), extractors.map { it.name })
    }

    @Test
    fun `an age-restricted channel's tabs are read from its system playlists`() {
        // NewPipeExtractor gives an age-restricted channel these tabs, because its page lists none.
        val tabs =
            listOf(ChannelTabs.VIDEOS, ChannelTabs.SHORTS, ChannelTabs.LIVESTREAMS).map { tab ->
                ReadyChannelTabListLinkHandler(
                    "${channelLink(CHANNEL_A)}/$tab",
                    CHANNEL_A,
                    tab,
                    ::YoutubeChannelTabPlaylistExtractor,
                )
            }

        val extractors = uploadTabExtractors(tabs, RequestSettings(ContentCountry("FR")))

        assertEquals(2, extractors.size)
        extractors.forEach { assertIs<YoutubeChannelTabPlaylistExtractor>(it) }
    }

    @Test
    fun `each tab asks for the call's language and country`() {
        val tabs =
            listOf(
                YoutubeChannelTabLinkHandlerFactory.getInstance()
                    .fromQuery(CHANNEL_A, listOf(ChannelTabs.VIDEOS), "")
            )

        val extractor = uploadTabExtractors(tabs, RequestSettings(ContentCountry("FR"))).single()

        assertEquals(REQUEST_LOCALIZATION, extractor.extractorLocalization)
        assertEquals(ContentCountry("FR"), extractor.extractorContentCountry)
    }

    @Test
    fun `the channel comes from its page, with the ID after any redirect`() {
        val avatars = listOf(image(width = 88, height = 88))
        val page = FakeChannelPage(id = CHANNEL_B, avatars = { avatars })

        assertEquals(
            Channel(
                ChannelId(CHANNEL_B),
                "Channel",
                listOf(Thumbnail(avatars[0].url, width = 88, height = 88)),
                "About",
            ),
            channelOf(page, SkippedParts(YouTubeCall.LATEST_UPLOADS)),
        )
    }

    @Test
    fun `a channel's missing description is empty, and an unreadable avatar is counted`() {
        val lines = mutableListOf<String>()
        val skipped = SkippedParts(YouTubeCall.LATEST_UPLOADS)
        val page =
            FakeChannelPage(description = null, avatars = { throw ParsingException("Changed") })

        val read = channelOf(page, skipped)
        skipped.warnIfAny(recordingLogger(lines).withTag(LOG_AREA))

        assertEquals("", read.description)
        assertEquals(emptyList(), read.avatars)
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=LATEST_UPLOADS dropped=0 " +
                    "parseErrors=1"
            ),
            lines,
        )
    }

    @Test
    fun `a channel without a well-formed ID or a name fails`() {
        val skipped = SkippedParts(YouTubeCall.LATEST_UPLOADS)

        assertFailsWith<MissingFieldException> { channelOf(FakeChannelPage(id = "x"), skipped) }
        assertFailsWith<MissingFieldException> { channelOf(FakeChannelPage(name = " "), skipped) }
    }

    /** A channel page as NewPipeExtractor reads it, without any request. */
    private class FakeChannelPage(
        private val id: String = CHANNEL_A,
        private val name: String = "Channel",
        private val description: String? = "About",
        private val avatars: () -> List<Image> = { emptyList() },
    ) :
        ChannelExtractor(
            ServiceList.YouTube,
            ServiceList.YouTube.channelLHFactory.fromId("$CHANNEL_PATH/$CHANNEL_A"),
        ) {
        override fun onFetchPage(downloader: Downloader) = Unit

        override fun getId(): String = id

        override fun getName(): String = name

        override fun getAvatars(): List<Image> = avatars()

        override fun getBanners(): List<Image> = emptyList()

        override fun getFeedUrl(): String = ""

        override fun getSubscriberCount(): Long = UNKNOWN_SUBSCRIBER_COUNT

        override fun getDescription(): String? = description

        override fun getParentChannelName(): String = ""

        override fun getParentChannelUrl(): String = ""

        override fun getParentChannelAvatars(): List<Image> = emptyList()

        override fun isVerified(): Boolean = false

        override fun getTabs(): List<ListLinkHandler> = emptyList()
    }
}
