package io.github.dewfallapp.feature.youtubedebug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.feature.youtubedebug.logic.SearchSummary
import io.github.dewfallapp.feature.youtubedebug.logic.StreamLinksSummary
import io.github.dewfallapp.feature.youtubedebug.logic.UploadsSummary
import io.github.dewfallapp.feature.youtubedebug.logic.VideoDetailsSummary
import io.github.dewfallapp.feature.youtubedebug.logic.VideoLine

// What each section shows of a successful answer. The summaries hold no URLs, so nothing here can
// show one.

@Composable
internal fun SearchResult(summary: SearchSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        ResultLine(
            stringResource(
                R.string.youtube_debug_search_counts,
                summary.channels.size,
                summary.videos.size,
            )
        )
        ResultLine(
            stringResource(R.string.youtube_debug_video_kinds, kindCountsText(summary.videoKinds))
        )
        for (channel in summary.channels) {
            ResultLine(
                stringResource(
                    R.string.youtube_debug_channel_line,
                    channel.name,
                    channel.avatarCount,
                )
            )
        }
        for (video in summary.videos) {
            VideoItem(video)
        }
    }
}

@Composable
internal fun VideoDetailsResult(summary: VideoDetailsSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        ResultLine(stringResource(R.string.youtube_debug_title_line, summary.title))
        ResultLine(
            stringResource(
                R.string.youtube_debug_channel_with_id,
                summary.channelName,
                summary.channelId.value,
            )
        )
        ResultLine(stringResource(R.string.youtube_debug_length, durationText(summary.duration)))
        ResultLine(
            stringResource(R.string.youtube_debug_published, publishedText(summary.published))
        )
        ResultLine(stringResource(R.string.youtube_debug_live, yesNoText(summary.isLive)))
        ResultLine(
            stringResource(
                R.string.youtube_debug_description,
                summary.descriptionLength,
                summary.descriptionLineCount,
            )
        )
        ResultLine(
            stringResource(
                R.string.youtube_debug_images,
                summary.thumbnailCount,
                summary.channelAvatarCount,
            )
        )
    }
}

@Composable
internal fun UploadsResult(summary: UploadsSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        ResultLine(
            stringResource(
                R.string.youtube_debug_channel_with_id,
                summary.channelName,
                summary.channelId.value,
            )
        )
        ResultLine(
            stringResource(
                R.string.youtube_debug_channel_asked_for,
                yesNoText(summary.isChannelAskedFor),
            )
        )
        ResultLine(stringResource(R.string.youtube_debug_video_count, summary.videos.size))
        ResultLine(
            stringResource(R.string.youtube_debug_video_kinds, kindCountsText(summary.videoKinds))
        )
        for (video in summary.videos) {
            VideoItem(video)
        }
    }
}

@Composable
internal fun StreamLinksResult(summary: StreamLinksSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        ResultLine(stringResource(R.string.youtube_debug_audio_formats, summary.audioFormats.size))
        ResultLine(
            stringResource(
                R.string.youtube_debug_video_formats,
                summary.videoFormats.size,
                summary.videoFormatsWithSound,
            )
        )
        ResultLine(
            stringResource(
                R.string.youtube_debug_formats_per_type,
                countsText(summary.formatsPerMimeType),
            )
        )
        ResultLine(
            stringResource(R.string.youtube_debug_hls_manifest, yesNoText(summary.hasHlsManifest))
        )
        ResultLine(
            stringResource(
                R.string.youtube_debug_expires,
                summary.expiresAt?.toString() ?: stringResource(R.string.youtube_debug_unknown),
            )
        )
        for (format in summary.audioFormats) {
            ResultLine(audioFormatText(format))
        }
        for (format in summary.videoFormats) {
            ResultLine(videoFormatText(format))
        }
    }
}

/** A video in a list: its title, then its other details in smaller text. */
@Composable
private fun VideoItem(video: VideoLine, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = video.title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text =
                stringResource(
                    R.string.youtube_debug_video_meta,
                    video.channelName,
                    video.kind.name,
                    durationText(video.duration),
                    publishedText(video.published),
                    video.thumbnailCount,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultLine(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.bodyMedium)
}
