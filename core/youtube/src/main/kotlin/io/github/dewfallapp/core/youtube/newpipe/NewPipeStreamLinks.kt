package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.StreamExtractor

/**
 * Thrown when a video's page gives no format that plays as a single file and no HLS manifest. It
 * maps to BROKEN. NewPipeExtractor leaves out a format whose link it can't decode, without an
 * error, so an empty answer is the only sign that it couldn't read the links.
 */
internal class NoStreamLinksException : Exception("YouTube's answer has no link that plays")

/** Reads the links that play or download one video, from its fetched page. */
internal class NewPipeStreamLinks(
    private val calls: NewPipeCalls,
    logger: Logger,
    private val userAgents: MediaUserAgents,
) : StreamLinksRemoteDataSource {
    private val log = logger.withTag(LOG_AREA)

    override suspend fun streamLinks(videoId: VideoId): YouTubeResult<StreamLinks> =
        calls.run(YouTubeCall.STREAM_LINKS) { settings ->
            val extractor = fetchedStreamExtractor(videoId, settings)
            val skipped = SkippedParts(YouTubeCall.STREAM_LINKS)
            val links = streamLinksOf(videoId, extractor, skipped)
            // Read here, while the call holds the Mutex, so MediaRequests calls no
            // NewPipeExtractor code on the player's threads. See ADR 0009.
            userAgents.rememberVisionOs(
                YoutubeParsingHelper.getVisionOsUserAgent(settings.localization)
            )
            skipped.warnIfAny(log)
            links
        }
}

/**
 * The links of the video with [videoId] that [extractor] fetched. Only formats that play as a
 * single file are kept, and a format without an itag or a media type is dropped and counted in
 * [skipped]. The links expire at the earliest time that any of them names.
 */
internal fun streamLinksOf(
    videoId: VideoId,
    extractor: StreamExtractor,
    skipped: SkippedParts,
): StreamLinks {
    val audioFormats = mutableListOf<AudioFormat>()
    for (stream in skipped.orDefault(emptyList()) { extractor.audioStreams }) {
        if (stream.isSingleFile()) {
            skipped.addOrDrop(audioFormats, stream.toAudioFormatOrNull())
        }
    }
    // Formats with sound come first, then the video-only ones.
    val videoFormats = mutableListOf<VideoFormat>()
    val videoStreams =
        skipped.orDefault(emptyList()) { extractor.videoStreams } +
            skipped.orDefault(emptyList()) { extractor.videoOnlyStreams }
    for (stream in videoStreams) {
        if (stream.isSingleFile()) {
            skipped.addOrDrop(videoFormats, stream.toVideoFormatOrNull())
        }
    }
    val hlsManifestUrl = skipped.orDefault("") { extractor.hlsUrl }.ifEmpty { null }
    if (audioFormats.isEmpty() && videoFormats.isEmpty() && hlsManifestUrl == null) {
        throw NoStreamLinksException()
    }
    val links =
        audioFormats.map { it.url } + videoFormats.map { it.url } + listOfNotNull(hlsManifestUrl)
    return StreamLinks(
        videoId = videoId,
        audioFormats = audioFormats,
        videoFormats = videoFormats,
        hlsManifestUrl = hlsManifestUrl,
        expiresAt = links.mapNotNull(::expiryOf).minOrNull(),
    )
}
