package io.github.dewfallapp.core.download

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.download.DownloadQueue
import io.github.dewfallapp.core.data.download.hasDownloading
import io.github.dewfallapp.core.logging.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

/**
 * The latest end of the app's process: whether the user [stopped] it, and when, in [atMillis].
 * [foreground] says whether the process was more important than a perceptible one at its end, as a
 * process with a user-initiated job or a foreground service, such as the player's, is. The Task
 * Manager lists the app only while it runs one of those.
 */
internal data class LatestExit(
    val stopped: Boolean,
    val atMillis: Long,
    val foreground: Boolean = true,
)

/**
 * Whether a process that ended with [exit] paused its downloads: only when the user stopped the
 * app, a download was running then, as [downloading] says from the rows a run left or from the
 * [RunRecord] of a run that hadn't claimed one yet, and the app wasn't updated at or after that
 * time, [updatedAtMillis]. Android gives the same reason for a swipe away from the recent apps and,
 * before Android 14, for an update. A swipe of an app that only waits leaves no running row and no
 * active run, and an update has its time. On Android 14 and later a swipe ends a backup job's
 * process at once, because it runs in the background, so it doesn't pause when [backupRanLast] and
 * [backupScheduled] say a backup run ran last and a backup job is still scheduled, and the process
 * wasn't in the foreground at its end: the Task Manager lists the app only while it runs a
 * user-initiated job or a foreground service, and Force stop cancels every job of the app, so then
 * the exit can only be a swipe. A Stop while the player's foreground service runs beside a backup
 * run still pauses. See ADR 0010's note of November 28, 2026.
 */
internal fun pausesAfterExit(
    exit: LatestExit?,
    downloading: Boolean,
    updatedAtMillis: Long,
    backupRanLast: Boolean = false,
    backupScheduled: Boolean = false,
) =
    exit != null &&
        exit.stopped &&
        downloading &&
        updatedAtMillis < exit.atMillis &&
        !(backupRanLast && backupScheduled && !exit.foreground)

/** The app's own exit records and update time, which Android keeps. */
internal interface AppHistory {
    /** The latest end of the app's process, or null on Android 10, which keeps no records. */
    fun latestExit(): LatestExit?

    /** When the app was last installed or updated, in milliseconds since the epoch. */
    fun updatedAtMillis(): Long

    /** Whether the last run of the queue was a backup job's, from the marker it writes. */
    fun backupRanLast(): Boolean = false

    /** Whether a backup job is scheduled or runs. */
    fun backupScheduled(): Boolean = false

    /** Whether a run of the queue had begun and not ended when the process ended. */
    fun runWasActive(): Boolean = false

    /** Forgets [runWasActive], once the decision has read it. */
    fun forgetRun() = Unit
}

/**
 * Says whether a run of the queue has begun and not ended, in an empty file in the app's no-backup
 * folder, which outlives the process. A Stop from the Task Manager ends the process before the
 * run's end can delete it, so the next start can tell that a run was active even before it claimed
 * its first download. It holds nothing about a video.
 */
internal interface RunRecord {
    /** Records that a run has begun. */
    fun begin()

    /** Records that no run is active. */
    fun end()

    /** Whether a run had begun and not ended. */
    fun wasActive(): Boolean
}

/** [RunRecord] as the file `download-run` in noBackupFilesDir. */
internal class FileRunRecord @Inject constructor(@ApplicationContext context: Context) : RunRecord {
    private val file = File(context.noBackupFilesDir, "download-run")

    override fun begin() {
        file.createNewFile()
    }

    override fun end() {
        file.delete()
    }

    override fun wasActive() = file.exists()
}

/** Reads [AppHistory] from ActivityManager, PackageManager, the backup marker and JobScheduler. */
internal class AndroidAppHistory
@Inject
constructor(
    @ApplicationContext context: Context,
    private val marker: BackupMarker,
    private val backups: BackupJobs,
    private val record: RunRecord,
) : AppHistory {
    private val packageName = context.packageName
    private val activities = context.getSystemService(ActivityManager::class.java)
    private val packages = context.packageManager

    override fun latestExit(): LatestExit? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val exit = activities.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
        // Android 14 recorded 125 for a Stop with the player's foreground service, 200 for one
        // during a user-initiated job, and 230 for a swipe during a backup run.
        return exit?.let {
            LatestExit(
                stopped = it.reason == ApplicationExitInfo.REASON_USER_REQUESTED,
                atMillis = it.timestamp,
                foreground = it.importance < RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            )
        }
    }

    override fun updatedAtMillis(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packages.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") packages.getPackageInfo(packageName, 0)
            }
            .lastUpdateTime

    override fun backupRanLast() = marker.exists()

    override fun backupScheduled() = backups.anyScheduled()

    override fun runWasActive() = record.wasActive()

    override fun forgetRun() = record.end()
}

/** Something to wait for before a run or a start reads the queue. */
internal fun interface StopCheck {
    suspend fun await()
}

/**
 * Pauses every waiting and running download when the user stopped the app while one ran, such as
 * with the Stop button of Android's Task Manager, as the maintainer chose on November 26, 2026. It
 * decides once per process, from the app's latest exit record, before anything in the process can
 * start a run: DewfallApplication's `onCreate` calls [check], and the runner and the scheduler
 * [await] the decision, which starts it if nothing did. A decision that throws is logged and lets
 * the queue go on, so a database that throws never holds it forever. See ADR 0010.
 */
@Singleton
class DownloadsAfterStop
@Inject
internal constructor(
    private val queue: DownloadQueue,
    private val history: AppHistory,
    @ApplicationScope scope: CoroutineScope,
    logger: Logger,
) : StopCheck {
    private val log = logger.withTag("Downloads")

    private val decision = scope.async(start = CoroutineStart.LAZY) { decide() }

    /** Starts the decision, at the process's start. */
    fun check() {
        decision.start()
    }

    override suspend fun await() {
        decision.await()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun decide() {
        try {
            val exit = history.latestExit()
            // A run that hadn't claimed its first download yet left no running row.
            val downloading = queue.hasDownloading() || history.runWasActive()
            history.forgetRun()
            val pauses =
                pausesAfterExit(
                    exit,
                    downloading,
                    history.updatedAtMillis(),
                    history.backupRanLast(),
                    history.backupScheduled(),
                )
            if (pauses) {
                queue.pauseQueued()
                log.info("Downloads paused after the app was stopped")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Any failure, such as a database that throws, lets the queue go on.
            log.warn("Downloads' stop check failed", error = e)
        }
    }
}
