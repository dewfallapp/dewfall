package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

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

private fun audio(itag: Int, bitrate: Int?, track: AudioTrack? = null, codec: String? = "opus") =
    AudioFormat(
        url = "https://rr1---sn-masked.googlevideo.com/videoplayback?itag=$itag",
        itag = itag,
        mimeType = "audio/webm",
        codec = codec,
        bitrate = bitrate,
        contentLength = null,
        track = track,
    )

private fun links(
    videos: List<VideoFormat>,
    audios: List<AudioFormat> = listOf(audio(251, 160_000)),
    manifest: String? = null,
) = StreamLinks(VideoId("aqz-KE-bpKQ"), audios, videos, manifest, expiresAt = null)

private val ladder =
    listOf(
        video(160, 256, 144),
        video(135, 854, 480),
        video(136, 1280, 720),
        video(137, 1920, 1080),
        video(313, 3840, 2160),
    )

private fun allHardware(@Suppress("UNUSED_PARAMETER") format: VideoFormat) = Decoding.HARDWARE

private fun choose(
    links: StreamLinks,
    metered: Boolean = false,
    videoDecoding: (VideoFormat) -> Decoding = ::allHardware,
    audioDecodes: (AudioFormat) -> Boolean = { true },
) = chooseFormats(links, metered, videoDecoding, audioDecodes)

private fun chosenVideo(choice: FormatChoice): Int =
    when (choice) {
        is FormatChoice.Separate -> choice.video.itag
        is FormatChoice.Combined -> choice.video.itag
        else -> error("No video format: $choice")
    }

class FormatChoiceTest {
    @Test
    fun `the largest format within the limit`() {
        assertEquals(135, chosenVideo(choose(links(ladder), metered = true)))
        assertEquals(137, chosenVideo(choose(links(ladder), metered = false)))
    }

    @Test
    fun `a portrait video counts its shorter side`() {
        val portrait = listOf(video(1, 1080, 1920), video(2, 2160, 3840), video(3, 480, 854))

        assertEquals(1, chosenVideo(choose(links(portrait))))
        assertEquals(3, chosenVideo(choose(links(portrait), metered = true)))
    }

    @Test
    fun `hardware comes before a larger software format, then the lowest bitrate`() {
        val formats =
            listOf(
                video(1, 1920, 1080, codec = "vp9"),
                video(2, 1280, 720, bitrate = 900_000),
                video(3, 1280, 720, bitrate = 700_000),
            )
        val decoding = { format: VideoFormat ->
            if (format.codec == "vp9") Decoding.SOFTWARE else Decoding.HARDWARE
        }

        assertEquals(3, chosenVideo(choose(links(formats), videoDecoding = decoding)))
    }

    @Test
    fun `software formats count when no hardware format is within the limit`() {
        val formats = listOf(video(1, 1920, 1080), video(2, 1280, 720))

        assertEquals(1, chosenVideo(choose(links(formats), videoDecoding = { Decoding.SOFTWARE })))
    }

    @Test
    fun `a codec the phone can't decode is skipped`() {
        val formats = listOf(video(1, 1920, 1080, codec = "av01.0.08M.08"), video(2, 1280, 720))
        val decoding = { format: VideoFormat ->
            if (format.codec!!.startsWith("av01")) Decoding.NONE else Decoding.HARDWARE
        }

        assertEquals(2, chosenVideo(choose(links(formats), videoDecoding = decoding)))
    }

    @Test
    fun `with nothing within the limit, the smallest decodable format`() {
        val formats = listOf(video(1, 1920, 1080), video(2, 1280, 720))

        assertEquals(2, chosenVideo(choose(links(formats), metered = true)))
    }

    @Test
    fun `a format with no size or no codec`() {
        val formats =
            listOf(video(1, null, null), video(2, 854, 480, codec = null), video(3, 640, 360))

        assertEquals(3, chosenVideo(choose(links(formats))))
    }

    @Test
    fun `no decodable video or audio format`() {
        assertEquals(
            FormatChoice.NotDecodable,
            choose(links(ladder), videoDecoding = { Decoding.NONE }),
        )
        assertEquals(FormatChoice.NotDecodable, choose(links(ladder), audioDecodes = { false }))
    }

    @Test
    fun `the original track among dubbed ones, at the bitrate the network allows`() {
        val original = AudioTrack("en.4", "English original", "en", AudioTrackKind.ORIGINAL)
        val dubbed = AudioTrack("de.3", "German", "de", AudioTrackKind.DUBBED)
        val audios =
            listOf(
                audio(1, 160_000, dubbed),
                audio(2, 50_000, original),
                audio(3, 160_000, original),
            )

        val metered = choose(links(ladder, audios), metered = true) as FormatChoice.Separate
        val unmetered = choose(links(ladder, audios)) as FormatChoice.Separate

        assertEquals(2, metered.audio.itag)
        assertEquals(3, unmetered.audio.itag)
    }

    @Test
    fun `no original track takes the first, and no tracks takes them all`() {
        val dubbed = AudioTrack("de.3", "German", "de", AudioTrackKind.DUBBED)
        val other = AudioTrack("fr.2", "French", "fr", AudioTrackKind.DUBBED)
        val withTracks = listOf(audio(1, 160_000, dubbed), audio(2, 160_000, other))
        val noTracks = listOf(audio(3, 70_000), audio(4, 130_000))

        assertEquals(1, (choose(links(ladder, withTracks)) as FormatChoice.Separate).audio.itag)
        assertEquals(4, (choose(links(ladder, noTracks)) as FormatChoice.Separate).audio.itag)
    }

    @Test
    fun `a format with sound and picture plays alone`() {
        val combined = listOf(video(18, 640, 360, includesAudio = true))

        assertEquals(FormatChoice.Combined(combined.single()), choose(links(combined)))
    }

    @Test
    fun `links with only a manifest count as live`() {
        val manifest = "https://manifest.googlevideo.com/api/manifest/hls_variant/id/MASKED"

        assertEquals(
            FormatChoice.Live(manifest),
            choose(links(emptyList(), emptyList(), manifest)),
        )
    }

    @Test
    fun `live variants within the limit by their shorter side`() {
        val sizes = listOf(1920 to 1080, 1280 to 720, 854 to 480, null)

        assertEquals(listOf(0, 1, 2), liveVariantsWithin(sizes, UNMETERED_LIMIT))
        assertEquals(listOf(2), liveVariantsWithin(sizes, METERED_LIMIT))
        assertEquals(listOf(1), liveVariantsWithin(listOf(1920 to 1080, 1280 to 720), 480))
    }

    @Test
    fun `each YouTubeError has a playback category, and only some offer Try again`() {
        assertEquals(PlaybackFailure.YOUTUBE, PlaybackFailure.of(YouTubeError.BROKEN))
        assertEquals(PlaybackFailure.NETWORK, PlaybackFailure.of(YouTubeError.NETWORK))
        assertEquals(PlaybackFailure.PRIVATE, PlaybackFailure.of(YouTubeError.PRIVATE))
        assertEquals(
            setOf(PlaybackFailure.NETWORK, PlaybackFailure.YOUTUBE, PlaybackFailure.OTHER),
            PlaybackFailure.entries.filter { it.offersTryAgain }.toSet(),
        )
    }
}
