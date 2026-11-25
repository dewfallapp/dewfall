package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Made-up formats; the links are placeholders, never real ones.
private fun video(
    itag: Int,
    width: Int?,
    height: Int?,
    codec: String? = "avc1.640028",
    bitrate: Int? = 1_000_000,
    includesAudio: Boolean = false,
) =
    VideoFormat(
        url = "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=$itag",
        itag = itag,
        mimeType = "video/mp4",
        codec = codec,
        bitrate = bitrate,
        contentLength = null,
        width = width,
        height = height,
        frameRate = 30,
        includesAudio = includesAudio,
    )

private fun audio(itag: Int, bitrate: Int?, track: AudioTrack? = null) =
    AudioFormat(
        url = "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=$itag",
        itag = itag,
        mimeType = "audio/webm",
        codec = "opus",
        bitrate = bitrate,
        contentLength = null,
        track = track,
    )

private fun links(
    videos: List<VideoFormat>,
    audios: List<AudioFormat> = listOf(audio(250, 70_000), audio(251, 160_000)),
    manifest: String? = null,
) = StreamLinks(VideoId("aqz-KE-bpKQ"), audios, videos, manifest, expiresAt = null)

private val ladder =
    listOf(
        video(160, 256, 144),
        video(134, 640, 360),
        video(136, 1280, 720),
        video(137, 1920, 1080),
    )

private fun choose(
    links: StreamLinks,
    videoDecoding: (VideoFormat) -> Decoding = { Decoding.HARDWARE },
    audioDecodes: (AudioFormat) -> Boolean = { true },
) = chooseDownloadFormats(links, videoDecoding, audioDecodes)

class DownloadFormatChoiceTest {
    @Test
    fun `the largest format within 720 pixels, with the highest audio bitrate`() {
        val choice = assertIs<FormatChoice.Separate>(choose(links(ladder)))

        assertEquals(136, choice.video.itag)
        assertEquals(251, choice.audio.itag)
    }

    @Test
    fun `hardware comes before software, then the lowest bitrate`() {
        val formats =
            listOf(
                video(1, 1280, 720, codec = "vp9"),
                video(2, 640, 360),
                video(3, 640, 360, bitrate = 500_000),
            )
        val decoding = { format: VideoFormat ->
            if (format.codec == "vp9") Decoding.SOFTWARE else Decoding.HARDWARE
        }

        val choice =
            assertIs<FormatChoice.Separate>(choose(links(formats), videoDecoding = decoding))

        assertEquals(3, choice.video.itag)
    }

    @Test
    fun `a format with both sound and picture`() {
        val formats = listOf(video(18, 640, 360, includesAudio = true))

        val choice = assertIs<FormatChoice.Combined>(choose(links(formats)))

        assertEquals(18, choice.video.itag)
    }

    @Test
    fun `nothing decodable`() {
        assertEquals(
            FormatChoice.NotDecodable,
            choose(links(ladder), videoDecoding = { Decoding.NONE }),
        )
        assertEquals(FormatChoice.NotDecodable, choose(links(ladder), audioDecodes = { false }))
    }

    @Test
    fun `links with only an HLS manifest keep nothing`() {
        val live =
            links(emptyList(), audios = emptyList(), manifest = "https://masked.example/m3u8")

        assertEquals(FormatChoice.NotDecodable, choose(live))
    }

    @Test
    fun `with nothing within the limit, a download keeps nothing`() {
        // Playback would fall back to the 1080 pixel one; the downloads spec allows at most 720.
        val formats = listOf(video(1, 1920, 1080), video(2, 2560, 1440))

        assertEquals(FormatChoice.NotDecodable, choose(links(formats)))
    }

    @Test
    fun `a format of unknown size doesn't count as within the limit`() {
        val formats = listOf(video(1, width = null, height = null))

        assertEquals(FormatChoice.NotDecodable, choose(links(formats)))
    }

    @Test
    fun `a video-only format with no audio format keeps nothing`() {
        assertEquals(FormatChoice.NotDecodable, choose(links(ladder, audios = emptyList())))
    }

    @Test
    fun `the original audio track`() {
        val original = AudioTrack("en.4", name = null, languageTag = "en", AudioTrackKind.ORIGINAL)
        val dubbed = AudioTrack("de.3", name = null, languageTag = "de", AudioTrackKind.DUBBED)
        val audios = listOf(audio(1, 200_000, dubbed), audio(2, 120_000, original))

        val choice = assertIs<FormatChoice.Separate>(choose(links(ladder, audios)))

        assertEquals(2, choice.audio.itag)
    }
}
