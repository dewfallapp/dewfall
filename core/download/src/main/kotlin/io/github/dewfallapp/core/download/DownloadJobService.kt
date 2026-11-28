package io.github.dewfallapp.core.download

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.DownloadProgress
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * The queue's jobs on Android 14 and later: the user-initiated data transfer job, and the backup
 * jobs that go on with a download when Android doesn't start that job again after its process
 * ended, as ADR 0010's note of November 28, 2026 explains.
 *
 * The user-initiated job gives Android the download notification at once, as UIDT jobs must within
 * 10 seconds, runs the queue, and asks to run again when the runner says so. While it runs it keeps
 * the first backup armed 30 seconds ahead, and cancels the second. A backup job runs the queue as a
 * plain job, with the same notification once it claims a download, and keeps the other backup
 * armed; it does nothing while another run is registered. A stop, such as a lost network, cancels
 * the run and asks to run again, and leaves the backup armed. A stop by the user pauses the queue,
 * whether Android tells the job, which passes it to the runner, or ends the process, and the next
 * start finds the stop in the app's exit record. Nothing escapes it but CancellationException.
 */
@SuppressLint("SpecifyJobSchedulerIdRange")
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@AndroidEntryPoint
class DownloadJobService : JobService() {
    @Inject internal lateinit var runner: DownloadRunner
    @Inject internal lateinit var notifications: DownloadNotifications
    @Inject internal lateinit var logger: Logger
    @Inject internal lateinit var backups: BackupJobs
    @Inject internal lateinit var marker: BackupMarker
    @Inject internal lateinit var runState: DownloadRunState
    @Inject internal lateinit var stopCheck: StopCheck
    @Inject @DownloadJobDispatcher internal lateinit var dispatcher: CoroutineDispatcher
    @Inject @DownloadDispatcher internal lateinit var io: CoroutineDispatcher

    private var scope: CoroutineScope? = null
    private lateinit var runs: JobRuns

    /** The runs in this service, by job ID, which only the main thread reads and writes. */
    private val running = HashMap<Int, Running>()

    /** One run of a job. */
    private class Running(val params: JobParameters) {
        var job: Job? = null

        /** Whether Android said the job's stop was the user's, set before the run is cancelled. */
        @Volatile var stoppedByUser = false

        /** Whether the stop disarms the run's backup, set before the run is cancelled. */
        @Volatile var disarms = false
    }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        runs = JobRuns(backups, runState, marker, stopCheck)
    }

    override fun onStartJob(params: JobParameters): Boolean =
        if (params.jobId == QUEUE_JOB_ID) {
            startQueueJob(params)
            true
        } else {
            startBackup(params)
        }

    private fun startQueueJob(params: JobParameters) {
        // The notification comes before the backup's arm, so a backup that shares it leaves it up
        // as it ends. queueRun registers the run before it arms or cancels a backup, and the
        // scope's Main.immediate dispatcher starts it at once, within onStartJob.
        notify(params, notifications.running(null))
        val run = Running(params)
        running[QUEUE_JOB_ID] = run
        run.job =
            checkNotNull(scope).launch {
                try {
                    var shown: Int? = -1
                    var estimated: Long? = null
                    val outcome =
                        runs.queueRun(io, disarmOnCancel = { run.disarms }) {
                            runQueue(run) { progress ->
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
                            }
                        }
                    jobFinished(params, outcome == RunOutcome.RETRY)
                } finally {
                    end(run)
                }
            }
    }

    /** Starts a backup run and says true, or says false when another run is registered. */
    private fun startBackup(params: JobParameters): Boolean {
        val id = params.jobId
        if (!runs.startBackup(id)) return false
        logger.withTag("Downloads").info("Download backup run started")
        val run = Running(params)
        running[id] = run
        val notice = BackupNotice(shade(params))
        // On the main thread, where runs register, as JobRuns.backupBegins asks.
        val begin: suspend () -> Unit = { withContext(dispatcher) { runs.backupBegins() } }
        run.job =
            checkNotNull(scope).launch {
                try {
                    val outcome =
                        keepingArmed(
                            backups,
                            backupFor(id),
                            io,
                            disarmOnCancel = { run.disarms },
                            keptElsewhere = { runs.keptByAnother(id) },
                        ) {
                            runQueue(run, begin) { notice.report(it) }
                        }
                    if (outcome == RunOutcome.DONE) runs.afterBackupDone()
                    jobFinished(params, outcome == RunOutcome.RETRY)
                } finally {
                    end(run)
                }
            }
        return true
    }

    /**
     * The run's requests use the default network, which OkHttp follows, so a change of network
     * needs nothing here. JobService's own version logs a warning that asks for an override.
     */
    override fun onNetworkChanged(params: JobParameters) = Unit

    override fun onStopJob(params: JobParameters): Boolean {
        val run = running[params.jobId] ?: return false
        run.stoppedByUser = isStopByUser(params.stopReason)
        run.disarms = runs.disarmsAfterStop(params.jobId, params.stopReason)
        run.job?.cancel()
        return true
    }

    override fun onDestroy() {
        scope?.cancel()
        super.onDestroy()
    }

    /** Ends [run], unless a later run of the same job took its place. */
    private fun end(run: Running) {
        val id = run.params.jobId
        if (running[id] === run) {
            running.remove(id)
            runs.end(id)
        }
    }

    /** Runs the queue, and says how the run ended; a run that throws asks to run again. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runQueue(
        run: Running,
        begin: suspend () -> Unit = {},
        report: RunReport,
    ): RunOutcome =
        try {
            runner.run(stoppedByUser = { run.stoppedByUser }, begin = begin, report = report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.withTag("Downloads").warn("Download job failed", error = e)
            RunOutcome.RETRY
        }

    /** A backup run's notification, which Android ties to the job, as it does a UIDT job's. */
    private fun shade(params: JobParameters) =
        object : NoticeShade {
            private val manager = getSystemService(NotificationManager::class.java)

            override fun set(progress: DownloadProgress) =
                notify(params, notifications.running(progress))

            override fun isShown() =
                manager.activeNotifications.any { it.id == DOWNLOAD_NOTIFICATION_ID }
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
