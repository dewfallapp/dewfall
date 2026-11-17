package io.github.dewfallapp.feature.youtubedebug.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.core.model.AudioTrackKind
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.feature.youtubedebug.logic.AudioFormatLine
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus
import io.github.dewfallapp.feature.youtubedebug.logic.ChannelLine
import io.github.dewfallapp.feature.youtubedebug.logic.SearchSummary
import io.github.dewfallapp.feature.youtubedebug.logic.Section
import io.github.dewfallapp.feature.youtubedebug.logic.StreamLinksSummary
import io.github.dewfallapp.feature.youtubedebug.logic.VideoFormatLine
import io.github.dewfallapp.feature.youtubedebug.logic.VideoLine
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugState
import kotlin.time.Duration
import kotlin.time.Instant

// Previews of the debug screen, in light, dark and large font, with made-up answers. Dewfall's
// own palette keeps them the same on every machine.

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun YouTubeDebugScreenPreview() {
    DewfallTheme(dynamicColor = false) {
        YouTubeDebugScreen(state = previewState(), onIntent = {})
    }
}

private fun previewState(): YouTubeDebugState =
    YouTubeDebugState(
        search = Section(input = "forest", status = CallStatus.Succeeded(previewSearch())),
        videoDetails =
            Section(input = "AAAAAAAAAAA", status = CallStatus.Running(VideoId("AAAAAAAAAAA"))),
        latestUploads =
            Section(
                input = "UCAAAAAAAAAAAAAAAAAAAAAA",
                status = CallStatus.Failed(YouTubeError.BLOCKED),
            ),
        streamLinks =
            Section(input = "AAAAAAAAAAA", status = CallStatus.Succeeded(previewStreamLinks())),
    )

private fun previewSearch(): SearchSummary {
    val video =
        VideoLine(
            title = "A long walk through an example forest",
            channelName = "Example channel",
            kind = VideoKind.VIDEO,
            duration = Duration.parse("12m 30s"),
            published = PublishedTime(Instant.parse("2026-11-01T00:00:00Z"), isApproximate = true),
            thumbnailCount = 4,
        )
    return SearchSummary(
        channels = listOf(ChannelLine(name = "Example channel", avatarCount = 3)),
        videos = listOf(video, video.copy(kind = VideoKind.SHORT, duration = null)),
        videoKinds =
            mapOf(
                VideoKind.VIDEO to 1,
                VideoKind.SHORT to 1,
                VideoKind.LIVE to 0,
                VideoKind.UPCOMING to 0,
            ),
    )
}

private fun previewStreamLinks(): StreamLinksSummary =
    StreamLinksSummary(
        audioFormats =
            listOf(
                AudioFormatLine(
                    itag = 140,
                    mimeType = "audio/mp4",
                    codec = "mp4a.40.2",
                    bitrate = 130_000,
                    contentLength = 12_000_000,
                    trackKind = AudioTrackKind.ORIGINAL,
                    trackLanguageTag = "en",
                )
            ),
        videoFormats =
            listOf(
                VideoFormatLine(
                    itag = 137,
                    mimeType = "video/mp4",
                    codec = "avc1.640028",
                    width = 1920,
                    height = 1080,
                    frameRate = 30,
                    bitrate = null,
                    contentLength = null,
                    includesAudio = false,
                )
            ),
        videoFormatsWithSound = 0,
        formatsPerMimeType = mapOf("audio/mp4" to 1, "video/mp4" to 1),
        hasHlsManifest = false,
        expiresAt = Instant.parse("2026-11-01T06:00:00Z"),
    )
