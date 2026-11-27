package io.github.dewfallapp.core.download

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.core.data.download.DownloadScheduler

/**
 * Binds the scheduler, which `:core:data`'s DownloadsRepository and MainActivity call, to
 * QueueScheduler. It is its own public module, apart from DownloadEngineModule, so that an emulator
 * test in `:app` can remove it with Hilt's `@UninstallModules`, which names the module, and bind a
 * scheduler that starts no run. Kotlin's `internal` would hide it from `:app`'s tests, so the class
 * is public and its binding internal, which keeps QueueScheduler internal. Nothing else names it.
 * BootReceiver gets QueueScheduler straight from its entry point, so a test that removes this
 * module doesn't hold that path, which runs only after a boot. See docs/testing.md#the-fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadSchedulerModule {
    @Binds internal abstract fun bindScheduler(scheduler: QueueScheduler): DownloadScheduler
}
