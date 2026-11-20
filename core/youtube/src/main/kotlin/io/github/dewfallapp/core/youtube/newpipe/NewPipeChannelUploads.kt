package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * The channel tabs that hold a channel's latest uploads, in the order their videos come: regular
 * videos and premieres, then live streams. Shorts are in a tab of their own, which isn't read.
 */
private val UPLOAD_TABS = listOf(ChannelTabs.VIDEOS, ChannelTabs.LIVESTREAMS)

/**
 * Reads a channel and the first page of its Videos and Live tabs through NewPipeExtractor. The
 * channel's own request holds its Videos tab, so most channels take two requests, and a channel
 * with no Live tab one. An age-restricted channel's page has no tabs, so NewPipeExtractor reads its
 * Videos and Live system playlists instead, two requests each. A channel that YouTube sends on to
 * another adds a request for each step.
 */
internal class NewPipeChannelUploads(private val calls: NewPipeCalls, logger: Logger) :
    ChannelUploadsRemoteDataSource {
    private val log = logger.withTag(LOG_AREA)

    override suspend fun latestUploads(channelId: ChannelId): YouTubeResult<ChannelUploads> =
        calls.run(YouTubeCall.LATEST_UPLOADS) { settings ->
            val service = ServiceList.YouTube
            val channelExtractor =
                settings.applyTo(
                    service.getChannelExtractor(
                        service.channelLHFactory.fromId("$CHANNEL_PATH/${channelId.value}"),
                    ),
                )
            channelExtractor.fetchPage()
            val skipped = SkippedParts(YouTubeCall.LATEST_UPLOADS)
            val channel = channelOf(channelExtractor, skipped)
            val pages =
                uploadTabExtractors(channelExtractor.tabs, settings).map {
                    it.fetchPage()
                    it.initialPage
                }
            for (page in pages) {
                skipped.addParseErrors(page.errors.size)
            }
            val videos = latestUploadsOf(channel, pages.map { it.items }, skipped)
            skipped.warnIfAny(log)
            ChannelUploads(channel, videos)
        }
}

/**
 * The channel that [extractor] fetched. Its ID can differ from the one asked for, because YouTube
 * sends some channels on to another channel. A missing avatar or description is left empty.
 */
internal fun channelOf(extractor: ChannelExtractor, skipped: SkippedParts): Channel {
    val id = ChannelId.parseOrNull(extractor.id)
    val name: String? = extractor.name
    if (id == null || name.isNullOrBlank()) throw MissingFieldException()
    return Channel(
        id = id,
        name = name,
        avatars = skipped.orDefault(emptyList()) { extractor.avatars.toThumbnails() },
        // Empty for an age-restricted channel, whose page has no description.
        description = skipped.orDefault("") { extractor.description.orEmpty() },
    )
}

/**
 * Extractors for the channel's Videos tab and Live tab, in that order, from the [tabs] that its
 * page lists. A channel without a Live tab gets only the Videos tab's.
 */
internal fun uploadTabExtractors(
    tabs: List<ListLinkHandler>,
    settings: RequestSettings,
): List<ChannelTabExtractor> =
    UPLOAD_TABS.mapNotNull { tab -> tabs.firstOrNull { tab in it.contentFilters } }
        .map { settings.applyTo(ServiceList.YouTube.getChannelTabExtractor(it)) }

/**
 * The videos of [channel] among [tabItems], the items of each tab in [UPLOAD_TABS] order, sorted as
 * ChannelUploads states. Each video takes the channel's ID and name, because a tab's items give the
 * tab's link as the channel's, and can lack the channel's name. A video without an ID or a title is
 * dropped and counted in [skipped].
 */
internal fun latestUploadsOf(
    channel: Channel,
    tabItems: List<List<InfoItem>>,
    skipped: SkippedParts,
): List<VideoSummary> {
    val videos = mutableListOf<VideoSummary>()
    for (item in tabItems.flatten().filterIsInstance<StreamInfoItem>()) {
        skipped.addOrDrop(videos, item.toVideoSummaryOrNull(channel.id, channel.name))
    }
    return latestUploadsOrder(videos)
}
