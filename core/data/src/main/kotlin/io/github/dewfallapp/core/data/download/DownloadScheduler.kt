package io.github.dewfallapp.core.data.download

/**
 * Starts a run of the download queue. `:core:download` binds it, by Android version, to a
 * user-initiated data transfer job or a WorkManager worker. See ADR 0010.
 */
interface DownloadScheduler {
    /**
     * Starts a run when none is active in the process and a download waits, after turning any
     * download that an ended process left as running back to waiting. It returns at once and does
     * its work in the background, so it is safe to call from the main thread. Calling it while a
     * run is active starts no new run: that run claims again, in the order asked, before it ends,
     * even when its own download waits for the network or for YouTube. A download behind one that
     * waits again can still wait for the next run. On Android 14 and later, a call during a backup
     * run also hands that run back to the user-initiated job, as ADR 0010 says.
     */
    fun start()
}
