package io.github.dewfallapp

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkerFactory
import dagger.hilt.android.HiltAndroidApp
import io.github.dewfallapp.core.download.DownloadWorkerFactory
import io.github.dewfallapp.core.download.DownloadsAfterStop
import javax.inject.Inject

/**
 * The app's Application class. Hilt builds the app-wide dependency container from it. It gives
 * WorkManager its configuration, so WorkManager starts on demand with the download worker's
 * factory, and at the process's start it checks whether the user stopped the app while a download
 * ran, so the downloads pause. See ADR 0010.
 */
@HiltAndroidApp
class DewfallApplication : Application(), Configuration.Provider {
    @Inject internal lateinit var workerFactory: DownloadWorkerFactory
    @Inject internal lateinit var downloadsAfterStop: DownloadsAfterStop

    override val workManagerConfiguration: Configuration
        get() = downloadWorkConfiguration(workerFactory)

    override fun onCreate() {
        super.onCreate()
        downloadsAfterStop.check()
    }
}

/** The first JobScheduler job ID that WorkManager may use, above the download job's 54. */
private const val FIRST_WORK_JOB_ID = 1_000

/** The last JobScheduler job ID that WorkManager may use. */
private const val LAST_WORK_JOB_ID = 100_000

/**
 * WorkManager's configuration: the download worker's factory, and a range of JobScheduler job IDs
 * that leaves out the download job's own, as WorkManager's lint check asks of an app with a
 * JobService of its own. From Android 14, WorkManager's jobs also have a namespace of their own. It
 * sets no logging level: WorkManager 2.12.0 picks its logger before the configuration applies, so
 * its DEBUG lines go on, and they hold no user data, because the work carries none. See ADR 0010.
 */
internal fun downloadWorkConfiguration(factory: WorkerFactory): Configuration =
    Configuration.Builder()
        .setWorkerFactory(factory)
        .setJobSchedulerJobIdRange(FIRST_WORK_JOB_ID, LAST_WORK_JOB_ID)
        .build()
