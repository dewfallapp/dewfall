package io.github.dewfallapp.core.download

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.DownloadProgress
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * The queue's WorkManager worker, on Android 10 to 13. It runs as a foreground service of the
 * dataSync type when the app is in the foreground, as after a tap, or on Android 10 and 11, which
 * allow it at any time. On Android 12 and 13 a run that starts while the app isn't in the
 * foreground, such as after a reboot, runs as a plain job, which Android stops after about 10
 * minutes and runs again. Nothing escapes it but CancellationException. See ADR 0010.
 */
internal class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val runner: DownloadRunner,
    private val notifications: DownloadNotifications,
    logger: Logger,
    private val inForeground: () -> Boolean = ::processInForeground,
) : CoroutineWorker(context, params) {
    private val log = logger.withTag("Downloads")

    // Expedited work must give its foreground info, which WorkManager uses before Android 12.
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result =
        try {
            val foreground = asksForForeground(Build.VERSION.SDK_INT, inForeground())
            if (foreground && !showForeground(null)) {
                // WorkManager 2.12 ignores a job's stop for a run whose foreground service
                // Android refused, so the run ends, and runs again as a plain job.
                Result.retry()
            } else {
                var shown: Int? = -1
                val outcome =
                    runner.run(
                        report = { progress ->
                            if (foreground && progress.percent != shown) {
                                shown = progress.percent
                                showForeground(progress)
                            }
                        },
                        stoppedByUser = ::isStoppedByUser,
                    )
                if (outcome == RunOutcome.RETRY) Result.retry() else Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Download job failed", error = e)
            Result.retry()
        }

    /**
     * Whether WorkManager stopped the worker because the user stopped the app, such as with the
     * Task Manager's Stop on Android 13, so the runner pauses the queue. WorkManager gives the
     * reason from Android 12 on.
     */
    private fun isStoppedByUser(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isStopByUser(stopReason)

    /**
     * Shows the notification as the run's foreground service, and says whether Android allowed it.
     */
    private suspend fun showForeground(progress: DownloadProgress?): Boolean =
        try {
            setForeground(foregroundInfo(progress))
            true
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException, from Android 12 on, extends it.
            log.info("Download foreground service refused")
            false
        }

    private fun foregroundInfo(progress: DownloadProgress?) =
        ForegroundInfo(
            DOWNLOAD_NOTIFICATION_ID,
            notifications.running(progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
}

/**
 * Whether a run on Android [sdk] asks for a foreground service. Android 12 and later refuse one to
 * an app that isn't [inForeground], with few exceptions, and WorkManager 2.12 then keeps the run in
 * its foreground list, where a job's stop never reaches it (Processor.stopWork). So such a run
 * doesn't ask, and stays a plain job that Android can stop.
 */
internal fun asksForForeground(sdk: Int, inForeground: Boolean): Boolean =
    sdk < Build.VERSION_CODES.S || inForeground

/** Whether the app's process shows an activity to the user now. */
private fun processInForeground(): Boolean {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}

/**
 * Makes DownloadWorker with what it needs from Hilt. It is written by hand, so the app needs no
 * androidx.hilt library. DewfallApplication gives it to WorkManager's configuration. It makes no
 * other worker, and WorkManager then falls back to its default factory.
 */
class DownloadWorkerFactory
@Inject
internal constructor(
    private val runner: DownloadRunner,
    private val notifications: DownloadNotifications,
    private val logger: Logger,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == DownloadWorker::class.java.name) {
            DownloadWorker(appContext, workerParameters, runner, notifications, logger)
        } else {
            null
        }
}
