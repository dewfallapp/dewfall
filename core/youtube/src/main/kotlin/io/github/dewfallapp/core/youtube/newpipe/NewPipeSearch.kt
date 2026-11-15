package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private val NO_RESULTS = SearchResults(channels = emptyList(), videos = emptyList())

/**
 * Searches YouTube through NewPipeExtractor's search extractor, with one request. The query is user
 * data, so nothing here logs it.
 */
internal class NewPipeSearch(private val calls: NewPipeCalls, logger: Logger) :
    SearchRemoteDataSource {
    private val log = logger.withTag(LOG_AREA)

    override suspend fun search(query: String): YouTubeResult<SearchResults> {
        if (query.isBlank()) return YouTubeResult.Success(NO_RESULTS)
        return calls.run(YouTubeCall.SEARCH) { settings ->
            val extractor = settings.applyTo(ServiceList.YouTube.getSearchExtractor(query))
            extractor.fetchPage()
            val page = extractor.firstPageOrNull()
            if (page == null) {
                NO_RESULTS
            } else {
                val skipped = SkippedParts(YouTubeCall.SEARCH)
                skipped.addParseErrors(page.errors.size)
                searchResultsOf(page.items, skipped).also { skipped.warnIfAny(log) }
            }
        }
    }
}

/**
 * The first page of results, or null when YouTube found nothing, which NewPipeExtractor reports as
 * an exception.
 */
// Finding nothing is an answer, not an error, so the exception carries nothing to keep.
@Suppress("SwallowedException")
internal fun SearchExtractor.firstPageOrNull(): InfoItemsPage<InfoItem>? =
    try {
        initialPage
    } catch (e: SearchExtractor.NothingFoundException) {
        null
    }

/**
 * The channels and videos among [items], each list in YouTube's order. Playlists are left out, as
 * SearchResults says. An item that misses a required field is dropped and counted in [skipped].
 */
internal fun searchResultsOf(items: List<InfoItem>, skipped: SkippedParts): SearchResults {
    val channels = mutableListOf<Channel>()
    val videos = mutableListOf<VideoSummary>()
    for (item in items) {
        when (item) {
            is StreamInfoItem -> skipped.addOrDrop(videos, item.toVideoSummaryOrNull())
            is ChannelInfoItem -> skipped.addOrDrop(channels, item.toChannelOrNull())
        }
    }
    return SearchResults(channels, videos)
}
