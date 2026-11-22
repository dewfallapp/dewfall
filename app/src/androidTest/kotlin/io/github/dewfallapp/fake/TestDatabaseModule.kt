package io.github.dewfallapp.fake

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.dewfallapp.core.data.database.ApplicationScope
import io.github.dewfallapp.core.data.database.DatabaseModule
import io.github.dewfallapp.core.database.ChannelDao
import io.github.dewfallapp.core.database.DewfallDatabase
import io.github.dewfallapp.core.database.VideoDao
import io.github.dewfallapp.core.database.WatchStateDao
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Replaces DatabaseModule in every emulator test with an in-memory database, built as the app
 * builds its own, with the phone's SQLite, but in memory. Hilt makes new singletons for each test,
 * so each test starts with an empty database, and one test's saved position never changes where the
 * next one starts. It provides everything DatabaseModule provides. See
 * docs/testing.md#emulator-tests.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DewfallDatabase =
        Room.inMemoryDatabaseBuilder<DewfallDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides fun provideChannelDao(database: DewfallDatabase): ChannelDao = database.channelDao()

    @Provides fun provideVideoDao(database: DewfallDatabase): VideoDao = database.videoDao()

    @Provides
    fun provideWatchStateDao(database: DewfallDatabase): WatchStateDao = database.watchStateDao()

    @Provides fun provideClock(): Clock = Clock.System

    /** As DatabaseModule's: a SupervisorJob keeps one failed write from cancelling the others. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
