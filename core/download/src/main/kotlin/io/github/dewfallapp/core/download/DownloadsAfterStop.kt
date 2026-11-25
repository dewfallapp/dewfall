package io.github.dewfallapp.core.download

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.download.DownloadQueue
import io.github.dewfallapp.core.data.download.hasDownloading
import io.github.dewfallapp.core.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

/** The latest end of the app's process: whether the user [stopped] it, and when, in [atMillis]. */
internal data class LatestExit(val stopped: Boolean, val atMillis: Long)

/**
 * Whether a process that ended with [exit] paused its downloads: only when the user stopped the
 * app, a download was running then, as [downloading] says from the rows a run left, and the app
 * wasn't updated at or after that time, [updatedAtMillis]. Android gives the same reason for a
 * swipe away from the recent apps and, before Android 14, for an update. A swipe of an app that
 * only waits leaves no running row, and an update has its time.
 */
internal fun pausesAfterExit(exit: LatestExit?, downloading: Boolean, updatedAtMillis: Long) =
    exit != null && exit.stopped && downloading && updatedAtMillis < exit.atMillis

/** The app's own exit records and update time, which Android keeps. */
internal interface AppHistory {
    /** The latest end of the app's process, or null on Android 10, which keeps no records. */
    fun latestExit(): LatestExit?

    /** When the app was last installed or updated, in milliseconds since the epoch. */
    fun updatedAtMillis(): Long
}

/** Reads [AppHistory] from ActivityManager and PackageManager. */
internal class AndroidAppHistory @Inject constructor(@ApplicationContext context: Context) :
    AppHistory {
    private val packageName = context.packageName
    private val activities = context.getSystemService(ActivityManager::class.java)
    private val packages = context.packageManager

    override fun latestExit(): LatestExit? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val exit = activities.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
        return exit?.let {
            LatestExit(it.reason == ApplicationExitInfo.REASON_USER_REQUESTED, it.timestamp)
        }
    }

    override fun updatedAtMillis(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packages.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") packages.getPackageInfo(packageName, 0)
            }
            .lastUpdateTime
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
            if (pausesAfterExit(exit, queue.hasDownloading(), history.updatedAtMillis())) {
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
