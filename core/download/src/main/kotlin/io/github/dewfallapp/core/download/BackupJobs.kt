package io.github.dewfallapp.core.download

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The backup job that a run of the queue's user-initiated job keeps armed, on Android 14 and later.
 * Both backup IDs sit below WorkManager's range of job IDs. See ADR 0010's note of November
 * 28, 2026.
 */
internal const val FIRST_BACKUP_JOB_ID = 55

/** The backup job that a run of the first backup job keeps armed, and the other way round. */
internal const val SECOND_BACKUP_JOB_ID = 56

/** How long after its last arm a backup job may start. */
internal val BACKUP_DELAY: Duration = BACKOFF_SECONDS.seconds

/** How often a run arms its backup again, so that while it lives the backup never starts. */
internal val BACKUP_REARM: Duration = 10.seconds

/** The backup that a run of the job with [jobId] keeps armed. */
internal fun backupFor(jobId: Int): Int =
    if (jobId == FIRST_BACKUP_JOB_ID) SECOND_BACKUP_JOB_ID else FIRST_BACKUP_JOB_ID

/** The backup jobs, and the queue's own job when a backup run finished its work. */
internal interface BackupJobs {
    /** Whether backup jobs exist here, on Android 14 and later. */
    val available: Boolean

    /**
     * Schedules backup [id] to start [BACKUP_DELAY] from now, replacing one that waits, and says
     * whether JobScheduler accepted it.
     */
    fun arm(id: Int): Boolean

    /** Cancels backup [id], and stops it if it runs. */
    fun disarm(id: Int)

    /** Cancels the queue's user-initiated job, which may wait, idle, after its process ended. */
    fun cancelQueueJob()

    /** Whether either backup job is scheduled or runs. */
    fun anyScheduled(): Boolean

    /**
     * Whether Android has the queue's user-initiated job, scheduled or running, such as after a
     * boot that restored it.
     */
    fun queueJobScheduled(): Boolean
}

/**
 * JobScheduler's backup jobs: plain jobs for DownloadJobService, on any network, with the queue's
 * backoff and PRIORITY_HIGH. They aren't persisted: after a reboot, Android restores the persisted
 * user-initiated job, and when it dropped that job because a backoff gave it a delay, BootReceiver
 * arms a backup instead, as ADR 0010's note on #280 explains.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class JobSchedulerBackups
@Inject
constructor(@ApplicationContext private val context: Context) : BackupJobs {
    private val scheduler = context.getSystemService(JobScheduler::class.java)

    override val available = true

    override fun arm(id: Int): Boolean {
        val job =
            JobInfo.Builder(id, ComponentName(context, DownloadJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(BACKUP_DELAY.inWholeMilliseconds)
                .setBackoffCriteria(
                    TimeUnit.SECONDS.toMillis(BACKOFF_SECONDS),
                    JobInfo.BACKOFF_POLICY_EXPONENTIAL,
                )
                .setPriority(JobInfo.PRIORITY_HIGH)
                .build()
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    override fun disarm(id: Int) {
        scheduler.cancel(id)
    }

    override fun cancelQueueJob() {
        scheduler.cancel(QUEUE_JOB_ID)
    }

    override fun anyScheduled(): Boolean =
        scheduler.getPendingJob(FIRST_BACKUP_JOB_ID) != null ||
            scheduler.getPendingJob(SECOND_BACKUP_JOB_ID) != null

    override fun queueJobScheduled(): Boolean = scheduler.getPendingJob(QUEUE_JOB_ID) != null
}

/** Below Android 14 no backup job exists. */
internal object NoBackups : BackupJobs {
    override val available = false

    override fun arm(id: Int) = false

    override fun disarm(id: Int) = Unit

    override fun cancelQueueJob() = Unit

    override fun anyScheduled() = false

    override fun queueJobScheduled() = false
}

/**
 * Runs [run] while keeping backup [id] armed: arms it at once and every [every] on [dispatcher].
 * When [run] returns or throws, it stops arming and then disarms the backup, so no late arm leaves
 * one behind: the job's own reschedule, which has a backoff delay, goes on from there. It doesn't
 * disarm it when [keptElsewhere] says another run keeps it armed now, such as the user-initiated
 * job's run that started while a backup run ended. When [run] is cancelled, as a job's stop does,
 * it leaves the backup armed, so the download goes on even if Android doesn't start the job again
 * by itself, unless [disarmOnCancel] says otherwise. The check and the disarm run in the caller's
 * context with no suspension between them, so on the main thread, where DownloadJobService
 * registers its runs, no run can register in between.
 */
internal suspend fun <T> keepingArmed(
    backups: BackupJobs,
    id: Int,
    dispatcher: CoroutineDispatcher,
    every: Duration = BACKUP_REARM,
    disarmOnCancel: () -> Boolean = { false },
    keptElsewhere: () -> Boolean = { false },
    run: suspend () -> T,
): T = coroutineScope {
    val keeper =
        launch(dispatcher) {
            while (true) {
                backups.arm(id)
                delay(every)
            }
        }
    var cancelled = false
    try {
        run()
    } catch (e: CancellationException) {
        cancelled = true
        throw e
    } finally {
        withContext(NonCancellable) {
            keeper.cancelAndJoin()
            val disarms = if (cancelled) disarmOnCancel() else !keptElsewhere()
            if (disarms) backups.disarm(id)
        }
    }
}

/**
 * Which runs of the download job service are registered, and what each start and stop does with the
 * backups. DownloadJobService calls it on the main thread, as JobService's callbacks come.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class JobRuns(
    private val backups: BackupJobs,
    private val runState: DownloadRunState,
    private val marker: BackupMarker,
    private val stopCheck: StopCheck,
) {
    private val registered = mutableSetOf<Int>()

    /** Whether a run of the job with [jobId] is registered. */
    operator fun contains(jobId: Int) = jobId in registered

    /**
     * Registers a run of the queue's user-initiated job, before it arms its backup, so a backup
     * that its arm or its cancel stops sees it. A backup run's hand-back has happened.
     */
    fun startQueueJob() {
        registered += QUEUE_JOB_ID
        runState.backupRun = false
    }

    /**
     * Runs [run] as the user-initiated job's run: registers it first, then cancels the second
     * backup and keeps the first armed on [dispatcher], as [keepingArmed] does, so a backup that
     * this stops always sees the run. The job's notification must be set before, so a backup that
     * shares it leaves it up as it ends. It deletes the backup marker once the stop check has read
     * it, without waiting for the run lock, which a stopped backup run may still hold: the Task
     * Manager lists the app from this job's start, so a Stop from then on must pause.
     */
    suspend fun <T> queueRun(
        dispatcher: CoroutineDispatcher,
        disarmOnCancel: () -> Boolean,
        run: suspend () -> T,
    ): T {
        startQueueJob()
        return coroutineScope {
            launch(dispatcher) { backups.disarm(SECOND_BACKUP_JOB_ID) }
            launch(dispatcher) {
                stopCheck.await()
                marker.delete()
            }
            keepingArmed(
                backups,
                FIRST_BACKUP_JOB_ID,
                dispatcher,
                disarmOnCancel = disarmOnCancel,
                run = run,
            )
        }
    }

    /**
     * Registers a run of backup [jobId] and says true, or says false when any run is registered,
     * the user-initiated job's or another backup's. Then the backup does nothing: after a kill
     * during a backup run, both backups may start in the new process, and the second must not stop
     * the first.
     */
    fun startBackup(jobId: Int): Boolean {
        if (registered.isNotEmpty()) return false
        registered += jobId
        runState.backupRun = true
        return true
    }

    /**
     * Whether a stop of the run of [jobId] with [stopReason] disarms the backup it kept armed. A
     * stop by the user paused the queue, so nothing should start again. A backup that the app
     * stopped, by arming or cancelling it for another run, disarms its own backup unless a
     * registered run keeps that one armed.
     */
    fun disarmsAfterStop(jobId: Int, stopReason: Int): Boolean {
        val cancelledBackup =
            jobId != QUEUE_JOB_ID && stopReason == JobParameters.STOP_REASON_CANCELLED_BY_APP
        return isStopByUser(stopReason) || (cancelledBackup && !keptByAnother(jobId))
    }

    /** Whether a registered run other than [jobId]'s keeps the backup that [jobId]'s run keeps. */
    fun keptByAnother(jobId: Int): Boolean {
        val backup = backupFor(jobId)
        return registered.any { it != jobId && backupFor(it) == backup }
    }

    /**
     * Writes the backup marker as a backup run begins, unless the user-initiated job's run is
     * registered, which deletes the marker and stops the backup. Call it on the main thread, where
     * runs register, so none registers between the check and the write.
     */
    fun backupBegins() {
        if (QUEUE_JOB_ID !in registered) marker.write()
    }

    /** Ends the run of [jobId]. */
    fun end(jobId: Int) {
        registered -= jobId
        if (jobId != QUEUE_JOB_ID) runState.backupRun = false
    }

    /**
     * After a backup run that finished the queue, cancels the queue's user-initiated job, which the
     * ended process left waiting, so it doesn't start later, find nothing and show its notification
     * for a moment. It cancels only while no run is scheduled or running, under the run state's
     * lock, so it never cancels a job that a tap scheduled since, and not while that job's run is
     * registered here, because JobScheduler may have started it before its run sets the phase.
     * DownloadJobService calls it on the main thread, where JobService's callbacks register runs,
     * and it cancels there too, so no run can register between the check and the cancel.
     */
    suspend fun afterBackupDone() {
        runState.mutex.withLock {
            if (runState.phase == RunPhase.IDLE && QUEUE_JOB_ID !in registered) {
                backups.cancelQueueJob()
            }
        }
    }
}

/**
 * Says whether the last run of the queue, in this process or an earlier one, was a backup run, in
 * an empty file in the app's no-backup folder. It holds nothing about a video.
 */
internal interface BackupMarker {
    fun write()

    fun delete()

    fun exists(): Boolean
}

/** [BackupMarker] as the file `download-backup-run` in noBackupFilesDir. */
internal class FileBackupMarker @Inject constructor(@ApplicationContext context: Context) :
    BackupMarker {
    private val file = File(context.noBackupFilesDir, "download-backup-run")

    override fun write() {
        file.createNewFile()
    }

    override fun delete() {
        file.delete()
    }

    override fun exists() = file.exists()
}
