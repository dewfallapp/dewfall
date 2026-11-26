package io.github.dewfallapp.core.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Starts the download queue after the phone boots, on Android 10 to 13 only, which the manifest's
 * `download_boot_receiver` sets. There the worker depends on WorkManager to schedule its work again
 * after a reboot, and a reboot within seconds of a tap can lose that, as a hand check on Android
 * 12L showed. Starting the queue here schedules a waiting download in any case. With none waiting
 * it reads and tidies the database and sends nothing. Android 14 and later persist the download's
 * job themselves. The maintainer chose this on November 26, 2026.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = EntryPointAccessors.fromApplication(context, BootParts::class.java)
        // goAsync keeps the process for the start, which reads the database and enqueues the work.
        val pending = goAsync()
        parts.scope().launch {
            try {
                startAfterBoot(intent.action, parts.logger()) { parts.scheduler().startNow() }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Runs [start] for Android's boot broadcast and returns whether it ran. A start that throws is
 * logged, because the next time the app shows starts the queue again.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun startAfterBoot(
    action: String?,
    logger: Logger,
    start: suspend () -> Unit,
): Boolean {
    if (action != Intent.ACTION_BOOT_COMPLETED) return false
    try {
        start()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.withTag("Downloads").warn("Download start after boot failed", error = e)
    }
    return true
}

/** What BootReceiver needs, which Android makes without Hilt's help. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BootParts {
    fun scheduler(): QueueScheduler

    @ApplicationScope fun scope(): CoroutineScope

    fun logger(): Logger
}
