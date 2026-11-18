package io.github.dewfallapp.core.youtube.newpipe

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * A video's page as NewPipeExtractor reads it, without any request. Each part is a function, so a
 * test can make it throw as NewPipeExtractor does when it can't parse the part.
 */
internal class FakeStreamPage(
    videoId: String = VIDEO_A,
    private val name: () -> String = { "Title" },
    private val uploaderLink: String = channelLink(CHANNEL_A),
    private val uploaderName: String = "Channel",
    private val streamType: StreamType = StreamType.VIDEO_STREAM,
    private val description: () -> Description = { Description.EMPTY_DESCRIPTION },
    private val length: () -> Long = { 0 },
    private val uploadDate: () -> DateWrapper? = { null },
    private val uploaderAvatars: () -> List<Image> = { emptyList() },
    private val thumbnails: () -> List<Image> = { emptyList() },
    private val audioStreams: () -> List<AudioStream> = { emptyList() },
    private val videoStreams: () -> List<VideoStream> = { emptyList() },
    private val videoOnlyStreams: () -> List<VideoStream> = { emptyList() },
    private val hlsUrl: () -> String = { "" },
    private val subtitles: (MediaFormat) -> List<SubtitlesStream> = { emptyList() },
) : StreamExtractor(ServiceList.YouTube, ServiceList.YouTube.streamLHFactory.fromId(videoId)) {
    override fun onFetchPage(downloader: Downloader) = Unit

    override fun getName(): String = name()

    override fun getUploaderUrl(): String = uploaderLink

    override fun getUploaderName(): String = uploaderName

    override fun getStreamType(): StreamType = streamType

    override fun getDescription(): Description = description()

    override fun getLength(): Long = length()

    override fun getUploadDate(): DateWrapper? = uploadDate()

    override fun getUploaderAvatars(): List<Image> = uploaderAvatars()

    override fun getThumbnails(): List<Image> = thumbnails()

    override fun getAudioStreams(): List<AudioStream> = audioStreams()

    override fun getVideoStreams(): List<VideoStream> = videoStreams()

    override fun getVideoOnlyStreams(): List<VideoStream> = videoOnlyStreams()

    override fun getHlsUrl(): String = hlsUrl()

    override fun getSubtitles(format: MediaFormat): List<SubtitlesStream> = subtitles(format)
}
