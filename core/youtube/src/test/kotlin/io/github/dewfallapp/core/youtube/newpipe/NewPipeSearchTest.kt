package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor

class NewPipeSearchTest {
    private val lines = mutableListOf<String>()
    private val log = recordingLogger(lines).withTag(LOG_AREA)

    @BeforeTest
    fun setUp() {
        setUpNewPipeWithoutRequests()
    }

    @Test
    fun `results keep YouTube's order and leave out playlists`() {
        val items =
            listOf(
                streamItem(videoId = VIDEO_A),
                channelItem(channelId = CHANNEL_A),
                PlaylistInfoItem(ServiceList.YouTube.serviceId, "https://playlist", "Playlist"),
                streamItem(videoId = VIDEO_B),
                channelItem(channelId = CHANNEL_B),
            )

        val results = searchResultsOf(items, SkippedParts(YouTubeCall.SEARCH))

        assertEquals(listOf(VideoId(VIDEO_A), VideoId(VIDEO_B)), results.videos.map { it.id })
        assertEquals(
            listOf(ChannelId(CHANNEL_A), ChannelId(CHANNEL_B)),
            results.channels.map { it.id },
        )
    }

    @Test
    fun `items without a required field are dropped and counted in one warning`() {
        val items =
            listOf(
                streamItem(videoId = VIDEO_A),
                streamItem(uploaderLink = null),
                channelItem(name = null),
            )
        val skipped = SkippedParts(YouTubeCall.SEARCH)
        skipped.addParseErrors(2)

        val results = searchResultsOf(items, skipped)
        skipped.warnIfAny(log)

        assertEquals(1, results.videos.size)
        assertEquals(0, results.channels.size)
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=SEARCH dropped=2 " +
                    "parseErrors=2"
            ),
            lines,
        )
    }

    @Test
    fun `a search that drops nothing logs nothing`() {
        val skipped = SkippedParts(YouTubeCall.SEARCH)

        searchResultsOf(listOf(streamItem(), channelItem()), skipped)
        skipped.warnIfAny(log)

        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a blank query returns no results without a request`() = runTest {
        val calls = NewPipeCalls(NoRequests, Logger.Silent, StandardTestDispatcher(testScheduler))
        val search = NewPipeSearch(calls, Logger.Silent)

        assertEquals(
            YouTubeResult.Success(SearchResults(emptyList(), emptyList())),
            search.search(""),
        )
        assertEquals(
            YouTubeResult.Success(SearchResults(emptyList(), emptyList())),
            search.search(" \n"),
        )
    }

    @Test
    fun `a search that finds nothing has no first page`() {
        assertNull(NothingFoundSearch().firstPageOrNull())
    }

    /** A search extractor whose first page reports, as YouTube's does, that it found nothing. */
    private class NothingFoundSearch :
        SearchExtractor(ServiceList.YouTube, ServiceList.YouTube.searchQHFactory.fromQuery("q")) {
        override fun onFetchPage(downloader: Downloader) = Unit

        override fun getSearchSuggestion(): String = ""

        override fun isCorrectedSearch(): Boolean = false

        override fun getMetaInfo(): List<MetaInfo> = emptyList()

        override fun getInitialPage(): InfoItemsPage<InfoItem> =
            throw NothingFoundException("No results")

        override fun getPage(page: Page?): InfoItemsPage<InfoItem> = InfoItemsPage.emptyPage()
    }
}
