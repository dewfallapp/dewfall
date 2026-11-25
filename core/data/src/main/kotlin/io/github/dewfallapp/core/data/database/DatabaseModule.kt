package io.github.dewfallapp.core.data.database

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.core.database.ChannelDao
import io.github.dewfallapp.core.database.DewfallDatabase
import io.github.dewfallapp.core.database.DownloadDao
import io.github.dewfallapp.core.database.DownloadQueueDao
import io.github.dewfallapp.core.database.VideoDao
import io.github.dewfallapp.core.database.WatchStateDao
import io.github.dewfallapp.core.database.buildDewfallDatabase
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Provides the database, one for the whole app, its DAOs, the clock the repositories read "now"
 * from, and the [ApplicationScope] their writes run in. See docs/architecture.md#database-tables.
 *
 * It is public so that the emulator tests' TestDatabaseModule can replace it with an in-memory
 * database through Hilt's `@TestInstallIn`, which names the module it replaces, as the tests do
 * with YouTubeModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DewfallDatabase =
        buildDewfallDatabase(context)

    @Provides fun provideChannelDao(database: DewfallDatabase): ChannelDao = database.channelDao()

    @Provides fun provideVideoDao(database: DewfallDatabase): VideoDao = database.videoDao()

    @Provides
    fun provideWatchStateDao(database: DewfallDatabase): WatchStateDao = database.watchStateDao()

    @Provides
    fun provideDownloadDao(database: DewfallDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideDownloadQueueDao(database: DewfallDatabase): DownloadQueueDao =
        database.downloadQueueDao()

    @Provides fun provideClock(): Clock = Clock.System

    /**
     * Its SupervisorJob keeps one failed write from cancelling the others. Room moves each query to
     * its own context, so the dispatcher only runs the coroutines' start.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
