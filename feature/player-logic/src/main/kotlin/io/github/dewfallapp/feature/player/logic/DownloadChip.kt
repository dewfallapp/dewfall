package io.github.dewfallapp.feature.player.logic

import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadStatus

/**
 * What the video screen's download chip shows, after the video's DownloadStatus, as the player
 * spec's "The video screen downloads the video" says. Sizes aren't user data, but the chip shows
 * only a percentage.
 */
sealed interface DownloadChip {
    /** "Download". A tap starts the download. */
    data object NotDownloaded : DownloadChip

    /** "Waiting to download". The chip isn't clickable. */
    data object Waiting : DownloadChip

    /** "Downloading, 42%", or "Downloading" while the size is unknown. A tap pauses it. */
    data class Downloading(val percent: Int?) : DownloadChip

    /** "Paused, 42%", or "Paused" while the size is unknown. A tap goes on. */
    data class Paused(val percent: Int?) : DownloadChip

    /** "Download failed", or "Not enough space on this phone" when [noSpace]. A tap tries again. */
    data class Failed(val noSpace: Boolean) : DownloadChip

    /** "Downloaded", selected. The chip isn't clickable. */
    data object Downloaded : DownloadChip
}

/** The chip for [status]. */
fun chipOf(status: DownloadStatus): DownloadChip =
    when (status) {
        DownloadStatus.NotDownloaded -> DownloadChip.NotDownloaded
        is DownloadStatus.Waiting -> DownloadChip.Waiting
        is DownloadStatus.Downloading -> DownloadChip.Downloading(status.progress.percent)
        is DownloadStatus.Paused -> DownloadChip.Paused(status.progress.percent)
        is DownloadStatus.Failed -> DownloadChip.Failed(status.reason == DownloadFailure.NO_SPACE)
        is DownloadStatus.Downloaded -> DownloadChip.Downloaded
    }

/**
 * The download chip that the screen shows: none until the details show, YouTube's or the ones saved
 * with the download, for a live stream, which can't be downloaded, or while the video's download
 * can't be read or hasn't answered yet.
 */
val PlayerState.shownDownload: DownloadChip?
    get() {
        val page = details.page ?: return null
        return if (page.isLive) null else download
    }
