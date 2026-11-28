package io.github.dewfallapp.core.download

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Binds the download engine's parts, which the scheduler that DownloadSchedulerModule binds uses.
 * The run launcher is the user-initiated data transfer job on Android 14 and later, and the
 * WorkManager worker below, with the backup jobs beside the user-initiated job. See ADR 0010.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DownloadEngineModule {
    @Binds fun bindStopCheck(afterStop: DownloadsAfterStop): StopCheck

    @Binds fun bindHistory(history: AndroidAppHistory): AppHistory

    @Binds fun bindDecoding(decoding: PlayerFormatDecoding): FormatDecoding

    @Binds fun bindFreeSpace(freeSpace: StorageFreeSpace): FreeSpace

    @Binds fun bindMarker(marker: FileBackupMarker): BackupMarker

    @Binds fun bindRunRecord(record: FileRunRecord): RunRecord

    companion object {
        @Provides
        fun provideLauncher(
            job: Provider<JobRunLauncher>,
            work: Provider<WorkRunLauncher>,
        ): RunLauncher =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                job.get()
            } else {
                work.get()
            }

        /** The backup jobs on Android 14 and later, where the user-initiated job runs. */
        @Provides
        fun provideBackups(jobs: Provider<JobSchedulerBackups>): BackupJobs =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                jobs.get()
            } else {
                NoBackups
            }

        @Provides @DownloadDispatcher fun provideDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @DownloadJobDispatcher
        fun provideJobDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

        @Provides
        fun provideWriter(@DownloadDispatcher dispatcher: CoroutineDispatcher): FormatWriter =
            FormatWriter(dispatcher)

        @Provides fun provideTimeSource(): TimeSource = TimeSource.Monotonic
    }
}
