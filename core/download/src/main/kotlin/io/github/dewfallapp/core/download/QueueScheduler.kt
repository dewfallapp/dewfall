package io.github.dewfallapp.core.download

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.download.DownloadQueue
import io.github.dewfallapp.core.data.download.DownloadScheduler
import io.github.dewfallapp.core.data.download.hasQueued
import io.github.dewfallapp.core.logging.Logger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** The ID of the queue's user-initiated job, in the app's default JobScheduler namespace. */
internal const val QUEUE_JOB_ID = 54

/** The unique name of the queue's WorkManager work. It names nothing about a video. */
internal const val QUEUE_WORK_NAME = "downloads"

/** The first wait before a run that asked to run again does, which then doubles. */
internal const val BACKOFF_SECONDS = 30L

/** Asks Android to run the queue's job. */
internal fun interface RunLauncher {
    /**
     * Returns false when Android refused, as it does for a UIDT job while the app is hidden, or
     * when WorkManager couldn't enqueue the work.
     */
    suspend fun launch(): Boolean
}

/**
 * A user-initiated data transfer job on any network, persisted so it survives a reboot, with the
 * same backoff as the worker. See ADR 0010.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class JobRunLauncher
@Inject
constructor(@ApplicationContext private val context: Context) : RunLauncher {
    override suspend fun launch(): Boolean {
        val job =
            JobInfo.Builder(QUEUE_JOB_ID, ComponentName(context, DownloadJobService::class.java))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(
                    TimeUnit.SECONDS.toMillis(BACKOFF_SECONDS),
                    JobInfo.BACKOFF_POLICY_EXPONENTIAL,
                )
                .build()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }
}

/**
 * Expedited WorkManager work on a connected network, replacing any that waits: start() enqueues
 * only while no run is active in the process, so it never replaces a run that still downloads. KEEP
 * would drop a tap that comes while a finished run is returning. See ADR 0010.
 */
internal class WorkRunLauncher
@Inject
constructor(@ApplicationContext private val context: Context) : RunLauncher {
    override suspend fun launch(): Boolean {
        val request =
            OneTimeWorkRequest.Builder(DownloadWorker::class.java)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        return WorkManager.getInstance(context)
            .enqueueUniqueWork(QUEUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            .enqueued()
    }
}

/**
 * Whether WorkManager enqueued the work. The operation ends after enqueueUniqueWork returns, and it
 * can fail, such as when WorkManager's database throws, so the scheduler waits for it, and a
 * failure leaves the queue ready for the next start.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun Operation.enqueued(): Boolean =
    try {
        await()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

/**
 * Starts a run of the queue when none is active in the process and a download waits, as
 * DownloadScheduler says, or hands a backup job's run back to the user-initiated job. A tap's write
 * calls it, and so does MainActivity's `onStart`, which starts again a queue that Android refused
 * to schedule while the app was hidden. A start while a run is running marks the ask, so that run
 * claims again before it ends instead of leaving the download to the job's backoff. BootReceiver
 * calls [resumeAfterBoot].
 */
@Singleton
internal class QueueScheduler
@Inject
constructor(
    private val queue: DownloadQueue,
    private val runState: DownloadRunState,
    private val launcher: RunLauncher,
    private val backups: BackupJobs,
    private val stopCheck: StopCheck,
    @ApplicationScope private val scope: CoroutineScope,
    logger: Logger,
) : DownloadScheduler {
    private val log = logger.withTag("Downloads")
    private val failed = CoroutineExceptionHandler { _, error ->
        log.warn("Download start failed", error = error)
    }

    override fun start() {
        scope.launch(failed) { startNow() }
    }

    /** What [start] does, which tests call directly. */
    internal suspend fun startNow() {
        stopCheck.await()
        runState.mutex.withLock {
            if (runState.phase != RunPhase.IDLE) {
                // A scheduled run that hasn't begun claims every download that waits anyway.
                if (runState.phase == RunPhase.RUNNING) runState.askedDuringRun = true
                // A backup job's run shows in no Task Manager and may stop after minutes, so the
                // app's return or a tap hands its download back to the user-initiated job, which
                // replaces the one that waits idle and stops the backup when it starts. The rows
                // stay as they are, because a run is active. Once per backup run, unless Android
                // refused it. A tap that comes once the stopped backup run went IDLE schedules the
                // job again, which may replace one that waits for the run lock: harmless, as when
                // Android starts the job again by itself. See ADR 0010's note of November 28,
                // 2026.
                if (runState.backupRun && launcher.launch()) runState.backupRun = false
                return
            }
            scheduleWaiting(launch = { launcher.launch() }) {
                log.warn("Download run not scheduled")
            }
        }
    }

    /**
     * What BootReceiver does after a boot. When Android restored the queue's user-initiated job, it
     * leaves it to run. Android restores persisted jobs before it sends the boot broadcast, but it
     * drops a user-initiated job that a backoff gave a delay, so otherwise a waiting download asks
     * for that job again. Android refuses it while the app isn't visible, so then it arms the first
     * backup job, which goes on with the download 30 seconds or more later, and the app's return
     * hands it back to the user-initiated job, as after a kill. Below Android 14 no backup exists,
     * and it does what [startNow] does. See ADR 0010's note on #280.
     */
    internal suspend fun resumeAfterBoot() {
        stopCheck.await()
        runState.mutex.withLock {
            if (runState.phase != RunPhase.IDLE || backups.queueJobScheduled()) return
            scheduleWaiting(launch = { launchAfterBoot() }) {
                when {
                    !backups.available -> log.warn("Download run not scheduled")
                    backups.arm(FIRST_BACKUP_JOB_ID) -> log.info("Download backup armed after boot")
                    // The download waits, and the app's next start schedules it.
                    else -> log.warn("Download backup not armed after boot")
                }
            }
        }
    }

    /**
     * Asks for the run after a boot, and says false when Android refuses or the launch throws, such
     * as when the user denied RUN_USER_INITIATED_JOBS, so a backup still goes on with the download.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun launchAfterBoot(): Boolean =
        try {
            launcher.launch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Download run after boot failed", error = e)
            false
        }

    /**
     * Schedules a run with [launch] when a download waits, or calls [refused]. Call it under the
     * run lock while no run is active in the process.
     */
    private suspend inline fun scheduleWaiting(
        launch: suspend () -> Boolean,
        refused: () -> Unit,
    ) {
        // With no run in the process, a running row can only be left over.
        queue.resetDownloading()
        if (!queue.hasQueued()) return
        if (launch()) runState.phase = RunPhase.SCHEDULED else refused()
    }
}
