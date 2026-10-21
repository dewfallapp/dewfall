package io.github.dewfallapp.logging

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.BuildConfig
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.Logger
import javax.inject.Singleton

/** Provides the app's one [Logger], which writes to logcat. See ADR 0007. */
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    @Provides
    @Singleton
    fun provideLogger(): Logger = Logger(LogcatWriter(), minLevel(debug = BuildConfig.DEBUG))
}

/**
 * The lowest level a build keeps. Debug builds keep every level. Release builds keep only warnings
 * and errors, which a user can read with adb and share when reporting a problem. See ADR 0007.
 */
internal fun minLevel(debug: Boolean): LogLevel = if (debug) LogLevel.DEBUG else LogLevel.WARN
