package io.github.dewfallapp.feature.inbox.ui

import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoSummary

/**
 * What the inbox's rows need for their download status: [statusOf] a video, or null while the
 * statuses can't be read, [onDownload] for the download button, and [onPause] for the progress
 * ring.
 */
internal class RowDownloads(
    val statusOf: (VideoId) -> DownloadStatus?,
    val onDownload: (VideoSummary) -> Unit,
    val onPause: (VideoId) -> Unit,
)
