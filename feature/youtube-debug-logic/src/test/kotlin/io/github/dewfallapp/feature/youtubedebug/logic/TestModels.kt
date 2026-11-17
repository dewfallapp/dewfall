package io.github.dewfallapp.feature.youtubedebug.logic

import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.AudioTrack
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary

// Made-up models for the tests. The IDs only need the right format.

internal val TEST_VIDEO_ID = VideoId("AAAAAAAAAAA")
internal val OTHER_VIDEO_ID = VideoId("BBBBBBBBBBB")
internal val TEST_CHANNEL_ID = ChannelId("UCAAAAAAAAAAAAAAAAAAAAAA")
internal val OTHER_CHANNEL_ID = ChannelId("UCBBBBBBBBBBBBBBBBBBBBBB")

internal fun thumbnails(count: Int): List<Thumbnail> =
    List(count) {
        Thumbnail(url = "https://i.ytimg.com/vi/test/$it.jpg", width = null, height = null)
    }

internal fun channel(name: String = "A channel", avatarCount: Int = 0): Channel =
    Channel(id = TEST_CHANNEL_ID, name = name, avatars = thumbnails(avatarCount), description = "")

internal fun video(title: String = "A video", kind: VideoKind = VideoKind.VIDEO): VideoSummary =
    VideoSummary(
        id = TEST_VIDEO_ID,
        title = title,
        channelId = TEST_CHANNEL_ID,
        channelName = "A channel",
        duration = null,
        published = null,
        thumbnails = thumbnails(2),
        kind = kind,
    )

internal fun audioFormat(mimeType: String = "audio/mp4", track: AudioTrack? = null): AudioFormat =
    AudioFormat(
        url = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=140",
        itag = 140,
        mimeType = mimeType,
        codec = "mp4a.40.2",
        bitrate = null,
        contentLength = null,
        track = track,
    )

internal fun videoFormat(
    mimeType: String = "video/mp4",
    includesAudio: Boolean = false,
): VideoFormat =
    VideoFormat(
        url = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=137",
        itag = 137,
        mimeType = mimeType,
        codec = "avc1.640028",
        bitrate = null,
        contentLength = null,
        width = 1920,
        height = 1080,
        frameRate = 30,
        includesAudio = includesAudio,
    )
