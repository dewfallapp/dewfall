package io.github.dewfallapp.feature.youtubedebug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus
import io.github.dewfallapp.feature.youtubedebug.logic.SearchSummary
import io.github.dewfallapp.feature.youtubedebug.logic.StreamLinksSummary
import io.github.dewfallapp.feature.youtubedebug.logic.SubtitleLinesSummary
import io.github.dewfallapp.feature.youtubedebug.logic.SubtitleTracksSummary
import io.github.dewfallapp.feature.youtubedebug.logic.TrackLine
import io.github.dewfallapp.feature.youtubedebug.logic.TrackPick
import io.github.dewfallapp.feature.youtubedebug.logic.UploadsSummary
import io.github.dewfallapp.feature.youtubedebug.logic.VideoDetailsSummary
import io.github.dewfallapp.feature.youtubedebug.logic.VideoLine
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent

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

/**
 * A video's subtitle tracks, each with a button that reads its lines, and the lines of the track
 * being read.
 */
@Composable
internal fun SubtitleTracksResult(
    summary: SubtitleTracksSummary,
    lines: CallStatus<TrackPick, SubtitleLinesSummary>,
    onIntent: (YouTubeDebugIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        ResultLine(
            stringResource(
                R.string.youtube_debug_subtitle_tracks,
                summary.tracks.size,
                summary.writtenCount,
                summary.autoGeneratedCount,
            )
        )
        for (track in summary.tracks) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
            ) {
                ResultLine(trackText(track), modifier = Modifier.weight(1f))
                TextButton(onClick = { onIntent(YouTubeDebugIntent.ReadLines(track.number)) }) {
                    Text(text = stringResource(R.string.youtube_debug_read_lines))
                }
            }
        }
        SubtitleLinesStatus(lines = lines, onIntent = onIntent)
    }
}

/** Where the reading of a track's lines stands, with a Cancel button while it runs. */
@Composable
private fun SubtitleLinesStatus(
    lines: CallStatus<TrackPick, SubtitleLinesSummary>,
    onIntent: (YouTubeDebugIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        when (lines) {
            CallStatus.Idle,
            CallStatus.InvalidInput -> Unit
            is CallStatus.Running -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onIntent(YouTubeDebugIntent.CancelLines) }) {
                    Text(text = stringResource(R.string.youtube_debug_cancel))
                }
            }
            CallStatus.Cancelled -> Text(text = stringResource(R.string.youtube_debug_cancelled))
            is CallStatus.Failed -> {
                Text(
                    text = stringResource(R.string.youtube_debug_failed, lines.error.name),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is CallStatus.Succeeded -> SubtitleLinesResult(lines.summary)
        }
    }
}

/** A track's lines: their counts and times, then the first of them with their times. */
@Composable
private fun SubtitleLinesResult(summary: SubtitleLinesSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
        ResultLine(
            stringResource(
                R.string.youtube_debug_lines_of,
                summary.track.number,
                summary.track.languageTag,
            )
        )
        ResultLine(
            stringResource(
                R.string.youtube_debug_line_count,
                summary.lineCount,
                durationText(summary.firstStart),
                durationText(summary.latestEnd),
            )
        )
        ResultLine(
            stringResource(
                R.string.youtube_debug_line_rows,
                summary.multiRowLines,
                summary.overlappingLines,
            )
        )
        for (line in summary.shownLines) {
            ResultLine(
                stringResource(
                    R.string.youtube_debug_line,
                    line.start.toString(),
                    line.end.toString(),
                    line.text,
                )
            )
        }
        if (summary.moreLines > 0) {
            ResultLine(stringResource(R.string.youtube_debug_more_lines, summary.moreLines))
        }
    }
}

@Composable
private fun trackText(track: TrackLine): String =
    stringResource(
        if (track.isAutoGenerated) {
            R.string.youtube_debug_track_auto
        } else {
            R.string.youtube_debug_track_written
        },
        track.number,
        track.languageTag,
    )

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
