package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.AudioTrack
import io.github.dewfallapp.core.model.AudioTrackKind
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoId
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** YouTube's itags for an M4A audio format, an MP4 format with sound, and an MP4 one without. */
private const val AUDIO_ITAG = 140
private const val VIDEO_WITH_AUDIO_ITAG = 18
private const val VIDEO_ONLY_ITAG = 137

class NewPipeStreamLinksTest {
    private val lines = mutableListOf<String>()
    private val skipped = SkippedParts(YouTubeCall.STREAM_LINKS)

    @BeforeTest
    fun setUp() {
        setUpNewPipeWithoutRequests()
    }

    @Test
    fun `formats that play as a single file map every field`() {
        val page =
            FakeStreamPage(
                audioStreams = { listOf(audioStream(link("a", expire = 2000))) },
                videoStreams = { listOf(videoStream(link("v", expire = 2000), videoOnly = false)) },
                videoOnlyStreams = {
                    listOf(videoStream(link("vo", expire = 2000), videoOnly = true))
                },
            )

        val links = streamLinksOf(VideoId(VIDEO_A), page, skipped)

        assertEquals(
            listOf(
                AudioFormat(
                    url = link("a", expire = 2000),
                    itag = AUDIO_ITAG,
                    mimeType = "audio/mp4",
                    codec = "mp4a.40.2",
                    bitrate = 130_000,
                    contentLength = 4_000_000,
                    track = null,
                ),
            ),
            links.audioFormats,
        )
        assertEquals(
            listOf(
                VideoFormat(
                    url = link("v", expire = 2000),
                    itag = VIDEO_WITH_AUDIO_ITAG,
                    mimeType = "video/mp4",
                    codec = "avc1.42001E",
                    bitrate = 500_000,
                    contentLength = 4_000_000,
                    width = 640,
                    height = 360,
                    frameRate = 30,
                    includesAudio = true,
                ),
                VideoFormat(
                    url = link("vo", expire = 2000),
                    itag = VIDEO_ONLY_ITAG,
                    mimeType = "video/mp4",
                    codec = "avc1.42001E",
                    bitrate = 500_000,
                    contentLength = 4_000_000,
                    width = 640,
                    height = 360,
                    frameRate = 30,
                    includesAudio = false,
                ),
            ),
            links.videoFormats,
        )
        assertEquals(VideoId(VIDEO_A), links.videoId)
        assertNull(links.hlsManifestUrl)
    }

    @Test
    fun `formats that need a manifest are left out without a warning`() {
        val page =
            FakeStreamPage(
                audioStreams = {
                    listOf(
                        audioStream(link("dash"), delivery = DeliveryMethod.DASH),
                        audioStream(link("otf"), isUrl = false),
                        audioStream(link("kept")),
                    )
                },
            )

        val links = streamLinksOf(VideoId(VIDEO_A), page, skipped)
        skipped.warnIfAny(recordingLogger(lines).withTag(LOG_AREA))

        assertEquals(listOf(link("kept")), links.audioFormats.map { it.url })
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `an unknown content length and an empty codec are null`() {
        val page =
            FakeStreamPage(
                audioStreams = {
                    listOf(
                        audioStream(
                            link("a"),
                            contentLength = ItagItem.CONTENT_LENGTH_UNKNOWN,
                            codec = "",
                        ),
                    )
                },
            )

        val format = streamLinksOf(VideoId(VIDEO_A), page, skipped).audioFormats.single()

        assertNull(format.contentLength)
        assertNull(format.codec)
    }

    @Test
    fun `an unknown bitrate, width, height and frame rate are null`() {
        val page =
            FakeStreamPage(
                audioStreams = { listOf(audioStream(link("a"), bitrate = 0)) },
                videoOnlyStreams = {
                    listOf(
                        videoStream(
                            link("vo"),
                            videoOnly = true,
                            bitrate = 0,
                            width = 0,
                            height = 0,
                            frameRate = ItagItem.FPS_NOT_APPLICABLE_OR_UNKNOWN,
                        ),
                    )
                },
            )

        val links = streamLinksOf(VideoId(VIDEO_A), page, skipped)

        assertNull(links.audioFormats.single().bitrate)
        val video = links.videoFormats.single()
        assertNull(video.bitrate)
        assertNull(video.width)
        assertNull(video.height)
        assertNull(video.frameRate)
    }

    @Test
    fun `a format without an itag or a media type is dropped and counted`() {
        val page =
            FakeStreamPage(
                audioStreams = {
                    listOf(
                        audioStream(link("no itag"), withItag = false),
                        audioStream(link("no type"), withMediaType = false),
                        audioStream(link("kept")),
                    )
                },
            )

        val links = streamLinksOf(VideoId(VIDEO_A), page, skipped)
        skipped.warnIfAny(recordingLogger(lines).withTag(LOG_AREA))

        assertEquals(listOf(link("kept")), links.audioFormats.map { it.url })
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=STREAM_LINKS dropped=2 " +
                    "parseErrors=0",
            ),
            lines,
        )
    }

    @Test
    fun `each sound track maps its ID, name, language and kind`() {
        val tracks =
            listOf(
                AudioTrackType.ORIGINAL to AudioTrackKind.ORIGINAL,
                AudioTrackType.DUBBED to AudioTrackKind.DUBBED,
                AudioTrackType.DESCRIPTIVE to AudioTrackKind.DESCRIPTIVE,
                AudioTrackType.SECONDARY to AudioTrackKind.OTHER,
                null to AudioTrackKind.OTHER,
            )

        for ((type, kind) in tracks) {
            val stream =
                audioStream(
                    link("a"),
                    trackId = "pt-BR.3",
                    trackName = "Portuguese",
                    trackLocale = Locale.forLanguageTag("pt-BR"),
                    trackType = type,
                )

            assertEquals(
                AudioTrack("pt-BR.3", "Portuguese", "pt-BR", kind),
                stream.toAudioFormatOrNull()?.track,
            )
        }
    }

    @Test
    fun `the links expire at the earliest time that any of them names`() {
        val page =
            FakeStreamPage(
                audioStreams = { listOf(audioStream(link("a", expire = 3000))) },
                videoOnlyStreams = {
                    listOf(videoStream(link("vo", expire = 2000), videoOnly = true))
                },
                hlsUrl = { hlsLink(expire = 2500) },
            )

        assertEquals(
            Instant.fromEpochSeconds(2000),
            streamLinksOf(VideoId(VIDEO_A), page, skipped).expiresAt,
        )
    }

    @Test
    fun `a live stream plays only through its HLS manifest, which says when it expires`() {
        val page =
            FakeStreamPage(
                streamType = StreamType.LIVE_STREAM,
                audioStreams = { listOf(audioStream(link("a"), delivery = DeliveryMethod.DASH)) },
                hlsUrl = { hlsLink(expire = 1500) },
            )

        val links = streamLinksOf(VideoId(VIDEO_A), page, skipped)

        assertEquals(emptyList(), links.audioFormats)
        assertEquals(hlsLink(expire = 1500), links.hlsManifestUrl)
        assertEquals(Instant.fromEpochSeconds(1500), links.expiresAt)
    }

    @Test
    fun `links that name no expiry expire at no known time`() {
        val page = FakeStreamPage(audioStreams = { listOf(audioStream(link("a"))) })

        assertNull(streamLinksOf(VideoId(VIDEO_A), page, skipped).expiresAt)
    }

    @Test
    fun `an expiry is read from a query parameter or an HLS path segment`() {
        assertEquals(Instant.fromEpochSeconds(1700000000), expiryOf(link("a", expire = 1700000000)))
        assertEquals(Instant.fromEpochSeconds(1700000000), expiryOf(hlsLink(expire = 1700000000)))
        assertNull(expiryOf("https://rr1.googlevideo.com/videoplayback?expire=soon"))
        assertNull(expiryOf("not a link"))
    }

    @Test
    fun `no format that plays and no HLS manifest fails`() {
        val page =
            FakeStreamPage(
                audioStreams = { listOf(audioStream(link("a"), delivery = DeliveryMethod.DASH)) },
            )

        assertFailsWith<NoStreamLinksException> { streamLinksOf(VideoId(VIDEO_A), page, skipped) }
    }

    @Test
    fun `an unreadable list of formats is counted, and the other formats still play`() {
        val page =
            FakeStreamPage(
                audioStreams = { throw ParsingException("Could not get audio streams") },
                videoStreams = { listOf(videoStream(link("v"), videoOnly = false)) },
            )

        val links = streamLinksOf(VideoId(VIDEO_A), page, skipped)
        skipped.warnIfAny(recordingLogger(lines).withTag(LOG_AREA))

        assertEquals(1, links.videoFormats.size)
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=STREAM_LINKS dropped=0 " +
                    "parseErrors=1",
            ),
            lines,
        )
    }

    private fun link(name: String, expire: Long? = null): String =
        "https://rr1.googlevideo.com/videoplayback?id=$name" + (expire?.let { "&expire=$it" } ?: "")

    private fun hlsLink(expire: Long): String =
        "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/$expire/id/x/file/index.m3u8"

    private fun audioStream(
        link: String,
        delivery: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP,
        isUrl: Boolean = true,
        contentLength: Long = 4_000_000,
        codec: String = "mp4a.40.2",
        bitrate: Int = 130_000,
        withItag: Boolean = true,
        withMediaType: Boolean = true,
        trackId: String? = null,
        trackName: String? = null,
        trackLocale: Locale? = null,
        trackType: AudioTrackType? = null,
    ): AudioStream {
        val itag = itagItem(AUDIO_ITAG, contentLength, codec, bitrate)
        return AudioStream.Builder()
            .setId(AUDIO_ITAG.toString())
            .setContent(link, isUrl)
            .setMediaFormat(if (withMediaType) itag.mediaFormat else null)
            .setDeliveryMethod(delivery)
            .setItagItem(if (withItag) itag else null)
            .setAudioTrackId(trackId)
            .setAudioTrackName(trackName)
            .setAudioLocale(trackLocale)
            .setAudioTrackType(trackType)
            .build()
    }

    private fun videoStream(
        link: String,
        videoOnly: Boolean,
        bitrate: Int = 500_000,
        width: Int = 640,
        height: Int = 360,
        frameRate: Int = 30,
    ): VideoStream {
        val itagId = if (videoOnly) VIDEO_ONLY_ITAG else VIDEO_WITH_AUDIO_ITAG
        val itag =
            itagItem(itagId, contentLength = 4_000_000, codec = "avc1.42001E", bitrate).apply {
                this.width = width
                this.height = height
                // The setter, not the public field of the same name, which is deprecated.
                setFps(frameRate)
            }
        return VideoStream.Builder()
            .setId(itagId.toString())
            .setContent(link, true)
            .setMediaFormat(itag.mediaFormat)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(videoOnly)
            .setResolution("360p")
            .setItagItem(itag)
            .build()
    }

    private fun itagItem(id: Int, contentLength: Long, codec: String, bitrate: Int): ItagItem =
        ItagItem.getItag(id).apply {
            this.contentLength = contentLength
            this.codec = codec
            this.bitrate = bitrate
        }
}
