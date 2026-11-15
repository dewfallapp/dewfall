package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

// Well-formed IDs for tests. Each names no real video or channel that a test depends on.
internal const val VIDEO_A = "aaaaaaaaaaa"
internal const val VIDEO_B = "bbbbbbbbbbb"
internal const val VIDEO_C = "ccccccccccc"
internal const val VIDEO_D = "ddddddddddd"
internal const val CHANNEL_A = "UCaaaaaaaaaaaaaaaaaaaaaa"
internal const val CHANNEL_B = "UCbbbbbbbbbbbbbbbbbbbbbb"

internal fun watchLink(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

internal fun channelLink(channelId: String) = "https://www.youtube.com/channel/$channelId"

/** A downloader for tests that must never send a request. */
internal object NoRequests : Downloader() {
    override fun execute(request: Request): Response = throw AssertionError("A test sent a request")
}

/**
 * Sets NewPipeExtractor up with [NoRequests], as NewPipeCalls does on its first call. An extractor
 * takes NewPipe's downloader when it is built, so a test that builds one calls this first. Then the
 * test passes alone and in any order.
 */
internal fun setUpNewPipeWithoutRequests() {
    NewPipe.init(NoRequests, REQUEST_LOCALIZATION)
}

/** A logger that adds the first line of each log line to [lines], without the error. */
internal fun recordingLogger(lines: MutableList<String>) =
    Logger({ level, tag, text -> lines += "$level $tag ${text.lines().first()}" }, LogLevel.DEBUG)

/** A video item as NewPipeExtractor's item extractors build one. */
internal fun streamItem(
    videoId: String = VIDEO_A,
    title: String? = "Title",
    uploaderLink: String? = channelLink(CHANNEL_A),
    uploaderName: String? = "Channel",
    streamType: StreamType = StreamType.VIDEO_STREAM,
    durationSeconds: Long = 60,
    uploadDate: DateWrapper? = null,
    isShort: Boolean = false,
    availability: ContentAvailability = ContentAvailability.AVAILABLE,
    thumbnails: List<Image> = emptyList(),
): StreamInfoItem =
    StreamInfoItem(ServiceList.YouTube.serviceId, watchLink(videoId), title, streamType).apply {
        uploaderUrl = uploaderLink
        this.uploaderName = uploaderName
        duration = durationSeconds
        this.uploadDate = uploadDate
        isShortFormContent = isShort
        contentAvailability = availability
        this.thumbnails = thumbnails
    }

/** A channel item as NewPipeExtractor's search builds one. */
internal fun channelItem(
    channelId: String = CHANNEL_A,
    name: String? = "Channel",
    description: String? = "About",
): ChannelInfoItem =
    ChannelInfoItem(ServiceList.YouTube.serviceId, channelLink(channelId), name).apply {
        this.description = description
    }

internal fun image(width: Int, height: Int) =
    Image("https://i.ytimg.com/vi/x/$width.jpg", height, width, Image.ResolutionLevel.UNKNOWN)
