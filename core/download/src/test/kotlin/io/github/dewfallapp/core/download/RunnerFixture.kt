package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.DownloadFileKind
import io.github.dewfallapp.core.data.download.DownloadFiles
import io.github.dewfallapp.core.data.playback.StreamLinksRepository
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.Decoding
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import java.nio.file.Files
import javax.inject.Provider
import kotlin.time.TestTimeSource
import kotlinx.coroutines.Dispatchers

internal val FIRST = VideoId("aaaaaaaaaaa")
internal val SECOND = VideoId("bbbbbbbbbbb")

/** A made-up link of [itag], at [version] of the video's links. */
internal fun link(itag: Int, version: Int = 1) =
    "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=$itag&v=$version"

internal fun videoFormat(itag: Int, height: Int, size: Long?, version: Int = 1) =
    VideoFormat(
        url = link(itag, version),
        itag = itag,
        mimeType = "video/mp4",
        codec = "avc1.640028",
        bitrate = 1_000_000,
        contentLength = size,
        width = height * 16 / 9,
        height = height,
        frameRate = 30,
        includesAudio = false,
    )

internal fun audioFormat(itag: Int, size: Long?, version: Int = 1) =
    AudioFormat(
        url = link(itag, version),
        itag = itag,
        mimeType = "audio/webm",
        codec = "opus",
        bitrate = 160_000,
        contentLength = size,
        track = null,
    )

/** Free space that is always [bytes], with no cache to clear. */
internal class SpaceOf(private val bytes: () -> Long) : FreeSpace {
    override fun makeRoom(folder: java.io.File, bytes: Long) = this.bytes() >= bytes

    override fun usableBytes(folder: java.io.File) = bytes()
}

/** The phone decodes every format. */
internal object AllDecode : FormatDecoding {
    override fun videoDecoding(format: VideoFormat) = Decoding.HARDWARE

    override fun audioDecodes(format: AudioFormat) = true
}

/** The runner with fakes for everything it uses, which the runner tests share. */
internal abstract class RunnerFixture {
    protected val video = fileOf(250 * 1024)
    protected val audio = fileOf(60 * 1024)
    protected val root = Files.createTempDirectory("downloads").toFile()
    protected val files = DownloadFiles(root)
    protected val queue = FakeQueue()
    protected val runState = DownloadRunState()
    protected val lines = mutableListOf<String>()
    protected var free = Long.MAX_VALUE
    protected val clock = TestTimeSource()

    /** Each video's links, version 1 by default, as YouTube gives them now. */
    protected var version = 1
    protected var videoItags = listOf(136, 137)
    protected val linkAnswers = ArrayDeque<YouTubeResult<StreamLinks>>()
    protected var linkCalls = 0

    protected fun links(id: VideoId): StreamLinks =
        StreamLinks(
            id,
            audioFormats = listOf(audioFormat(251, audio.size.toLong(), version)),
            videoFormats =
                videoItags.map {
                    videoFormat(it, if (it == 137) 1080 else 720, video.size.toLong(), version)
                },
            hlsManifestUrl = null,
            expiresAt = null,
        )

    protected val streamLinks = StreamLinksRepository { id ->
        linkCalls++
        linkAnswers.removeFirstOrNull() ?: YouTubeResult.Success(links(id))
    }

    protected fun media(vararg versions: Int, video: FakeMedia = FakeMedia(this.video)) =
        RoutedMedia(
            versions
                .flatMap { v ->
                    listOf(
                        link(136, v) to video,
                        link(137, v) to FakeMedia(fileOf(1)),
                        link(140, v) to video,
                        link(251, v) to FakeMedia(audio),
                    )
                }
                .toMap(),
        )

    protected var routed = media(1)

    protected fun runner(
        stopCheck: StopCheck = StopCheck {},
        links: StreamLinksRepository = streamLinks,
    ): DownloadRunner {
        val logger = Logger(LogWriter { _, _, text -> lines += text }, LogLevel.DEBUG)
        val downloader =
            VideoDownloader(
                queue = queue,
                streamLinks = links,
                files = files,
                mediaCalls = Provider { routed.calls },
                decoding = AllDecode,
                freeSpace = SpaceOf { free },
                writer = FormatWriter(Dispatchers.IO, rangeBytes = 100L * 1024),
                outcomes = DownloadOutcomes(queue, files, logger),
                logger = logger,
                time = clock,
            )
        return DownloadRunner(queue, downloader, runState, stopCheck, logger, Dispatchers.IO)
    }

    protected val reports = mutableListOf<DownloadProgress>()

    protected suspend fun run(stopCheck: StopCheck = StopCheck {}) =
        runner(stopCheck).run { reports += it }

    protected fun waiting(id: VideoId, folder: String = id.value + "-folder") =
        queue.add(FakeRow(id, folder))

    protected fun complete(id: VideoId, kind: DownloadFileKind, mime: String) =
        files.complete(id.value + "-folder", kind, mime)

    protected fun part(id: VideoId, kind: DownloadFileKind) = files.part(id.value + "-folder", kind)
}
