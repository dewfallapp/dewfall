package io.github.dewfallapp.feature.downloads.logic

import io.github.dewfallapp.core.model.Download
import io.github.dewfallapp.core.model.DownloadStatus
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The Downloads screen's reducer. Each read replaces the list: an empty one says there are no
 * downloads, and any other is split into its two sections. A failed read says the downloads can't
 * be shown. A tap changes nothing here: the screen shows what the phone holds, which the next read
 * brings. A finished download's removal shows "Download removed".
 */
object DownloadsReducer : Reducer<DownloadsState, DownloadsIntent, DownloadsEffect> {
    override fun reduce(
        state: DownloadsState,
        intent: DownloadsIntent,
    ): Next<DownloadsState, DownloadsEffect> =
        when (intent) {
            is DownloadsIntent.DownloadsRead -> Next(DownloadsState(listOf(intent.downloads)))
            DownloadsIntent.ReadFailed -> Next(DownloadsState(DownloadsList.Unreadable))
            is DownloadsIntent.Pause,
            is DownloadsIntent.Resume,
            is DownloadsIntent.Remove -> Next(state)
            DownloadsIntent.Removed -> Next(state, listOf(DownloadsEffect.ShowRemoved))
        }
}

/**
 * [downloads], in the queue's order, as the screen lists them: every unfinished one in that order,
 * and every finished one, the most recently finished first, ties in the queue's order.
 */
private fun listOf(downloads: List<Download>): DownloadsList {
    if (downloads.isEmpty()) return DownloadsList.NoDownloads
    val (finished, unfinished) = downloads.partition { it.status is DownloadStatus.Downloaded }
    // sortedByDescending is stable, so ties keep the queue's order.
    return DownloadsList.Shown(
        downloading = unfinished,
        onPhone = finished.sortedByDescending { it.finishedAt },
    )
}
