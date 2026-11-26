package io.github.dewfallapp.core.download

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.core.data.download.DownloadScheduler
import javax.inject.Provider
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Binds the download engine: the scheduler, which `:core:data`'s DownloadsRepository calls, and its
 * parts. The run launcher is the user-initiated data transfer job on Android 14 and later, and the
 * WorkManager worker below. See ADR 0010.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DownloadEngineModule {
    @Binds fun bindScheduler(scheduler: QueueScheduler): DownloadScheduler

    @Binds fun bindStopCheck(afterStop: DownloadsAfterStop): StopCheck

    @Binds fun bindHistory(history: AndroidAppHistory): AppHistory

    @Binds fun bindDecoding(decoding: PlayerFormatDecoding): FormatDecoding

    @Binds fun bindFreeSpace(freeSpace: StorageFreeSpace): FreeSpace

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
