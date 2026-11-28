package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.model.DownloadProgress

/** Where a backup run's notification goes: the job's own notification, and whether it shows. */
internal interface NoticeShade {
    /** Gives Android the download notification at [progress], tied to the job. */
    fun set(progress: DownloadProgress)

    /** Whether the download notification is among the app's active notifications. */
    fun isShown(): Boolean
}

/**
 * A backup run's notification. Android may not mark it as a user-initiated job's, and then the user
 * can swipe it away, and each later update would bring it back. So it is set at the first progress
 * report, once the run has claimed a download, and then on each percent change only while it shows
 * among the app's active notifications. It isn't set again while it doesn't show: the user may have
 * swiped it away before any report looked, and without the notification permission it never shows.
 * A first one is never lost on its way, because Android sheds only updates of a posted notification
 * when an app posts too fast (NotificationManagerService at 6e47c707, lines 7228-7243), so it shows
 * later, and the next percent change sets it again. One for each run. See ADR 0010's note of
 * November 28, 2026.
 */
internal class BackupNotice(private val shade: NoticeShade) {
    private var set = false
    private var percent: Int? = null

    /** Hears the run's [progress]. */
    fun report(progress: DownloadProgress) {
        if (set && progress.percent == percent) return
        val first = !set
        set = true
        percent = progress.percent
        if (first || shade.isShown()) shade.set(progress)
    }
}
