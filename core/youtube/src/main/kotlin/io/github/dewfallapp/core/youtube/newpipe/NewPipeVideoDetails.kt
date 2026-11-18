package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import kotlin.time.Duration.Companion.seconds
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * The stream types of a live stream that is on air, or that has just ended and still plays live.
 */
private val LIVE_OR_JUST_ENDED_TYPES =
    LIVE_STREAM_TYPES + setOf(StreamType.POST_LIVE_STREAM, StreamType.POST_LIVE_AUDIO_STREAM)

/** Reads what the video screen shows about one video, from its fetched page. */
internal class NewPipeVideoDetails(private val calls: NewPipeCalls, logger: Logger) :
    VideoDetailsRemoteDataSource {
    private val log = logger.withTag(LOG_AREA)

    override suspend fun videoDetails(videoId: VideoId): YouTubeResult<VideoDetails> =
        calls.run(YouTubeCall.VIDEO_DETAILS) { settings ->
            val extractor = fetchedStreamExtractor(videoId, settings)
            val skipped = SkippedParts(YouTubeCall.VIDEO_DETAILS)
            val details = videoDetailsOf(videoId, extractor, skipped)
            skipped.warnIfAny(log)
            details
        }
}

/**
 * A stream extractor for the video with [videoId], with its page fetched. NewPipeExtractor v0.27.1
 * sends five requests for that: a visitor ID and a player request for each of YouTube's visionOS
 * and web clients, and one for the page's other data. It stops after the visionOS client's two for
 * a video that YouTube says can't be played.
 */
internal fun fetchedStreamExtractor(videoId: VideoId, settings: RequestSettings): StreamExtractor {
    val service = ServiceList.YouTube
    val extractor =
        settings.applyTo(service.getStreamExtractor(service.streamLHFactory.fromId(videoId.value)))
    extractor.fetchPage()
    return extractor
}

/**
 * The details of the video with [videoId] that [extractor] fetched. A missing title, channel ID or
 * channel name throws, which fails the call with BROKEN. A missing optional part is left empty, and
 * one that NewPipeExtractor can't parse is counted in [skipped].
 */
internal fun videoDetailsOf(
    videoId: VideoId,
    extractor: StreamExtractor,
    skipped: SkippedParts,
): VideoDetails {
    // NewPipeExtractor itself throws ParsingException for a missing title, channel link or channel
    // name. A link without a channel ID, or a blank name, is missing too.
    val channelId = channelIdFromLink(extractor.uploaderUrl) ?: throw MissingFieldException()
    val channelName = extractor.uploaderName.ifBlank { throw MissingFieldException() }
    val isLive = extractor.streamType in LIVE_OR_JUST_ENDED_TYPES
    return VideoDetails(
        id = videoId,
        title = extractor.name,
        channelId = channelId,
        channelName = channelName,
        channelAvatars =
            skipped.orDefault(emptyList()) { extractor.uploaderAvatars.toThumbnails() },
        description = skipped.orDefault("") { extractor.description.toPlainText() },
        // NewPipeExtractor gives 0 for a length that it doesn't know.
        duration =
            if (isLive) {
                null
            } else {
                skipped.orDefault(null) { extractor.length.takeIf { it > 0 }?.seconds }
            },
        published = skipped.orDefault(null) { extractor.uploadDate?.toPublishedTime() },
        thumbnails = skipped.orDefault(emptyList()) { extractor.thumbnails.toThumbnails() },
        isLive = isLive,
    )
}
