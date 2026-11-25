package io.github.dewfallapp.core.download

import android.annotation.SuppressLint
import android.app.Notification
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import io.github.dewfallapp.core.logging.Logger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Whether a stop with [stopReason] is the user's, STOP_REASON_USER, such as the Task Manager's Stop
 * when Android stops the job or worker before it ends the process, which pauses every waiting and
 * running download. Android's UIDT page says the Task Manager's Stop doesn't call onStopJob, and
 * then DownloadsAfterStop pauses the queue at the next start instead. WorkInfo.STOP_REASON_USER has
 * the same value as JobParameters', 13.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun isStopByUser(stopReason: Int): Boolean = stopReason == JobParameters.STOP_REASON_USER

// WorkManager's lint check asks an app with a JobService of its own to give WorkManager a range of
// job IDs. DewfallApplication's configuration does, in :app, which this module's lint can't see.
/**
 * The queue's user-initiated data transfer job, on Android 14 and later. It gives Android the
 * download notification at once, as UIDT jobs must within 10 seconds, runs the queue, and asks to
 * run again when the runner says so. A stop, such as a lost network, cancels the run and asks to
 * run again. A stop by the user pauses the queue, whether Android tells the job, which passes it to
 * the runner, or ends the process, and the next start finds the stop in the app's exit record.
 * Nothing escapes it but CancellationException. See ADR 0010.
 */
@SuppressLint("SpecifyJobSchedulerIdRange")
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@AndroidEntryPoint
class DownloadJobService : JobService() {
    @Inject internal lateinit var runner: DownloadRunner
    @Inject internal lateinit var notifications: DownloadNotifications
    @Inject internal lateinit var logger: Logger
    @Inject @DownloadJobDispatcher internal lateinit var dispatcher: CoroutineDispatcher

    private var scope: CoroutineScope? = null
    private var run: Job? = null

    /** Whether Android said the job's stop was the user's, set before the run is cancelled. */
    @Volatile private var stoppedByUser = false

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    override fun onStartJob(params: JobParameters): Boolean {
        notify(params, notifications.running(null))
        run =
            checkNotNull(scope).launch {
                val again = runQueue(params)
                jobFinished(params, again)
            }
        return true
    }

    /**
     * The run's requests use the default network, which OkHttp follows, so a change of network
     * needs nothing here. JobService's own version logs a warning that asks for an override.
     */
    override fun onNetworkChanged(params: JobParameters) = Unit

    override fun onStopJob(params: JobParameters): Boolean {
        stoppedByUser = isStopByUser(params.stopReason)
        run?.cancel()
        return true
    }

    override fun onDestroy() {
        scope?.cancel()
        super.onDestroy()
    }

    /** Runs the queue, and says whether to run again. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runQueue(params: JobParameters): Boolean =
        try {
            var shown: Int? = -1
            var estimated: Long? = null
            runner.run(
                report = { progress ->
                    // A new notification only when the percentage changes.
                    if (progress.percent != shown) {
                        shown = progress.percent
                        notify(params, notifications.running(progress))
                    }
                    // The estimate only when the total first shows or changes.
                    val total = progress.totalBytes
                    if (total != null && total != estimated) {
                        estimated = total
                        updateEstimatedNetworkBytes(params, total, 0)
                    }
                },
                stoppedByUser = { stoppedByUser },
            ) == RunOutcome.RETRY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.withTag("Downloads").warn("Download job failed", error = e)
            true
        }

    private fun notify(params: JobParameters, notification: Notification) {
        setNotification(
            params,
            DOWNLOAD_NOTIFICATION_ID,
            notification,
            JOB_END_NOTIFICATION_POLICY_REMOVE,
        )
    }
}
