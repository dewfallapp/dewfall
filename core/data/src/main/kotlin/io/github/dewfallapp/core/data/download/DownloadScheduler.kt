package io.github.dewfallapp.core.data.download

/**
 * Starts a run of the download queue. `:core:download` binds it, by Android version, to a
 * user-initiated data transfer job or a WorkManager worker. See ADR 0010.
 */
interface DownloadScheduler {
    /**
     * Starts a run when none is active in the process and a download waits, after turning any
     * download that an ended process left as running back to waiting. It returns at once and does
     * its work in the background, so it is safe to call from the main thread, and calling it while
     * a run is active does nothing: that run takes every download that waits before it ends.
     */
    fun start()
}
