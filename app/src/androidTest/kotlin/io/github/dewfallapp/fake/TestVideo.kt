package io.github.dewfallapp.fake

import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * The one video that the fake YouTube layer knows. Everything here is made up for the emulator
 * tests, and its files are the test pattern and the tone in the test APK's assets. See
 * docs/testing.md#emulator-tests.
 */
object TestVideo {
    /** What the test types into the search field. Any other search finds nothing. */
    const val QUERY = "test pattern"

    const val TITLE = "Dewfall test pattern"
    const val CHANNEL_NAME = "Dewfall test channel"

    /** Only the video screen shows it, so seeing it shows that the screen got the details. */
    const val DESCRIPTION = "A test pattern and a tone, made for Dewfall's emulator tests."

    /** The picture's size in video.mp4. */
    const val WIDTH = 320
    const val HEIGHT = 180

    /**
     * The links of the two files. The reserved .invalid domain never resolves, and AssetMedia
     * answers them without the network.
     */
    const val VIDEO_URL = "https://media.dewfall.invalid/video.mp4"
    const val AUDIO_URL = "https://media.dewfall.invalid/audio.m4a"

    /** The files in the test APK's assets. */
    const val VIDEO_ASSET = "media/video.mp4"
    const val AUDIO_ASSET = "media/audio.m4a"

    /** Made up, in the shape that VideoId and ChannelId check. */
    val id = VideoId("dewfallTest")
    val channelId = ChannelId("UCdewfallTestChannel0000")

    private val length = 30.seconds

    val summary: VideoSummary
        get() =
            VideoSummary(
                id = id,
                title = TITLE,
                channelId = channelId,
                channelName = CHANNEL_NAME,
                duration = length,
                published = null,
                thumbnails = emptyList(),
                kind = VideoKind.VIDEO,
            )

    val details: VideoDetails
        get() =
            VideoDetails(
                id = id,
                title = TITLE,
                channelId = channelId,
                channelName = CHANNEL_NAME,
                channelAvatars = emptyList(),
                description = DESCRIPTION,
                duration = length,
                published = null,
                thumbnails = emptyList(),
                isLive = false,
            )

    /**
     * A video-only and an audio-only format, as most YouTube videos have, so the player merges two
     * files, as it does for real videos. [videoLength] and [audioLength] are the files' sizes.
     */
    fun streamLinks(videoLength: Long, audioLength: Long): StreamLinks =
        StreamLinks(
            videoId = id,
            audioFormats =
                listOf(
                    AudioFormat(
                        url = AUDIO_URL,
                        itag = 140,
                        mimeType = "audio/mp4",
                        codec = "mp4a.40.2",
                        bitrate = 48_000,
                        contentLength = audioLength,
                        track = null,
                    ),
                ),
            videoFormats =
                listOf(
                    VideoFormat(
                        url = VIDEO_URL,
                        itag = 160,
                        mimeType = "video/mp4",
                        codec = "avc1.42C01E",
                        bitrate = 120_000,
                        contentLength = videoLength,
                        width = WIDTH,
                        height = HEIGHT,
                        frameRate = 30,
                        includesAudio = false,
                    ),
                ),
            hlsManifestUrl = null,
            expiresAt = Clock.System.now() + 6.hours,
        )
}
