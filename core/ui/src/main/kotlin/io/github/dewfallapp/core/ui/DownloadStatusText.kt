package io.github.dewfallapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadStatus

/** "Download". */
@Composable fun downloadText(): String = stringResource(R.string.core_ui_download)

/** "Waiting to download". */
@Composable fun downloadWaitingText(): String = stringResource(R.string.core_ui_download_waiting)

/** "Downloading, 42%", or "Downloading" while the size is unknown. */
@Composable
fun downloadingText(percent: Int?): String =
    if (percent == null) {
        stringResource(R.string.core_ui_downloading)
    } else {
        stringResource(R.string.core_ui_downloading_percent, percent)
    }

/** "Paused, 42%", or "Paused" while the size is unknown. */
@Composable
fun downloadPausedText(percent: Int?): String =
    if (percent == null) {
        stringResource(R.string.core_ui_download_paused)
    } else {
        stringResource(R.string.core_ui_download_paused_percent, percent)
    }

/** "Not enough space on this phone" when [noSpace], else "Download failed". */
@Composable
fun downloadFailedText(noSpace: Boolean): String =
    stringResource(
        if (noSpace) R.string.core_ui_download_no_space else R.string.core_ui_download_failed,
    )

/** "Downloaded". */
@Composable fun downloadedText(): String = stringResource(R.string.core_ui_downloaded)

/** The words that a row's meta line shows for [status], or null for a state with no words. */
@Composable
internal fun downloadWords(status: DownloadStatus): String? =
    when (status) {
        DownloadStatus.NotDownloaded,
        is DownloadStatus.Downloaded -> null
        is DownloadStatus.Waiting -> downloadWaitingText()
        is DownloadStatus.Downloading -> downloadingText(status.progress.percent)
        is DownloadStatus.Paused -> downloadPausedText(status.progress.percent)
        is DownloadStatus.Failed -> downloadFailedText(status.reason == DownloadFailure.NO_SPACE)
    }
