package io.github.dewfallapp.feature.downloads.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.ui.VideoRowItem
import io.github.dewfallapp.core.ui.downloadFailedText
import io.github.dewfallapp.core.ui.downloadPausedText
import io.github.dewfallapp.core.ui.downloadWaitingText
import io.github.dewfallapp.core.ui.downloadingText
import io.github.dewfallapp.core.ui.fileSizeOfText
import io.github.dewfallapp.core.ui.fileSizeText
import io.github.dewfallapp.feature.downloads.logic.DownloadsIntent
import java.time.ZoneOffset
import kotlin.time.Instant

/** The progress bar's height, as in the "Downloads" frame. */
private val ProgressBarHeight = 4.dp

/**
 * Where a row's buttons start: the thumbnail's width at the default font size and the gap after it.
 * With the row's own padding gone, the text button's inner padding brings its word under the row's
 * text, as in the "Downloads" frame.
 */
private val ButtonsStart = 72.dp + DewfallSpacing.md

/** The share of a whole that 100 percent is. */
private const val PERCENT = 100f

/**
 * The rows show no publish time, as in the "Downloads" frame, so VideoRowItem's clock is never
 * read. It needs one, so this fixed one stands in.
 */
private val UnusedNow = Instant.fromEpochMilliseconds(0)

/**
 * A download that isn't finished: the video, a progress bar once its size is known, its status
 * line, then its two buttons. The row's text reads as one node that opens the video, and each
 * button is a focus target of its own.
 */
@Composable
internal fun UnfinishedRow(download: Download, actions: DownloadsActions) {
    val video = download.video
    val status = download.status
    Column {
        VideoRow(download = download, actions = actions) {
            val progress = status.progress()
            progress?.percent?.let { ProgressBar(percent = it) }
            StatusLine(status = status)
        }
        Buttons(arrangement = Arrangement.Start) {
            when (status) {
                is DownloadStatus.Waiting,
                is DownloadStatus.Downloading -> {
                    TextButton(
                        onClick = { actions.onIntent(DownloadsIntent.Pause(video.id)) },
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                    ) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_pause),
                            // The button's own text names it.
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(
                            text = stringResource(R.string.downloads_pause),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                }
                is DownloadStatus.Paused -> {
                    TextButton(onClick = { actions.onIntent(DownloadsIntent.Resume(video)) }) {
                        Text(text = stringResource(R.string.downloads_resume))
                    }
                }
                is DownloadStatus.Failed -> {
                    TextButton(onClick = { actions.onIntent(DownloadsIntent.Resume(video)) }) {
                        Text(text = stringResource(R.string.downloads_try_again))
                    }
                }
                DownloadStatus.NotDownloaded,
                is DownloadStatus.Downloaded -> Unit
            }
            TextButton(
                onClick = { actions.onIntent(DownloadsIntent.Remove(video.id, finished = false)) },
            ) {
                Text(text = stringResource(R.string.downloads_cancel))
            }
        }
    }
}

/**
 * A finished download: the video with the size of its files in the meta line, then "Remove
 * download" at the end.
 */
@Composable
internal fun FinishedRow(download: Download, actions: DownloadsActions) {
    val status = download.status
    Column {
        VideoRow(
            download = download,
            actions = actions,
            statusWords =
                (status as? DownloadStatus.Downloaded)?.let {
                    {
                        Text(
                            text = fileSizeText(it.bytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
        )
        Buttons(arrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    actions.onIntent(DownloadsIntent.Remove(download.video.id, finished = true))
                },
            ) {
                Text(text = stringResource(R.string.downloads_remove), textAlign = TextAlign.End)
            }
        }
    }
}

/** The row's video, which opens the video screen, with no publish time, as the frame shows. */
@Composable
private fun VideoRow(
    download: Download,
    actions: DownloadsActions,
    statusWords: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
) {
    val video = download.video
    VideoRowItem(
        title = video.title,
        channelName = video.channelName,
        duration = video.duration,
        kind = video.kind,
        thumbnails = video.thumbnails,
        published = null,
        now = UnusedNow,
        zone = ZoneOffset.UTC,
        modifier =
            Modifier.clip(MaterialTheme.shapes.medium).clickable(
                onClickLabel = stringResource(R.string.downloads_open_video),
            ) {
                actions.onOpenVideo(video.id)
            },
        statusWords = statusWords,
        supporting = supporting?.let { { it() } },
    )
}

/** A row's buttons under its text, wrapping at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(arrangement: Arrangement.Horizontal, content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = ButtonsStart),
        horizontalArrangement = arrangement,
    ) {
        content()
    }
}

/**
 * The bar of how much is on the phone, in primary on its track, with round caps and no gap or stop
 * dot, as in the "Downloads" frame. Screen readers skip it, because the status line says the
 * percentage.
 */
@Composable
private fun ProgressBar(percent: Int) {
    LinearProgressIndicator(
        progress = { percent / PERCENT },
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = DewfallSpacing.sm)
                .height(ProgressBarHeight)
                .clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/**
 * The line under the bar: "42%, 344 of 820 MB" while it runs, "Waiting to download", "Paused, 42%",
 * or, after the error glyph, "Download failed" or "Not enough space on this phone".
 */
@Composable
private fun StatusLine(status: DownloadStatus) {
    Row(
        modifier = Modifier.padding(top = DewfallSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status is DownloadStatus.Failed) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_error),
                // The words next to it say what failed.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        Text(
            text = statusWords(status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun statusWords(status: DownloadStatus): String =
    when (status) {
        is DownloadStatus.Downloading -> runningWords(status.progress)
        is DownloadStatus.Waiting -> downloadWaitingText()
        is DownloadStatus.Paused -> downloadPausedText(status.progress.percent)
        is DownloadStatus.Failed -> downloadFailedText(status.reason == DownloadFailure.NO_SPACE)
        DownloadStatus.NotDownloaded,
        is DownloadStatus.Downloaded -> ""
    }

/** "42%, 344 of 820 MB", or "Downloading" while the size is unknown. */
@Composable
private fun runningWords(progress: DownloadProgress): String {
    val percent = progress.percent
    val total = progress.totalBytes
    return if (percent == null || total == null) {
        downloadingText(null)
    } else {
        stringResource(
            R.string.downloads_progress,
            percent,
            fileSizeOfText(progress.downloadedBytes, total),
        )
    }
}

/** The progress of a download that isn't finished, or null for one with none. */
private fun DownloadStatus.progress(): DownloadProgress? =
    when (this) {
        is DownloadStatus.Waiting -> progress
        is DownloadStatus.Downloading -> progress
        is DownloadStatus.Paused -> progress
        DownloadStatus.NotDownloaded,
        is DownloadStatus.Failed,
        is DownloadStatus.Downloaded -> null
    }
