package io.github.dewfallapp

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.core.download.DownloadWorkerFactory

/**
 * What the emulator tests' Application has of DewfallApplication: WorkManager's configuration, with
 * the download worker's factory, so WorkManager starts on demand in the tests as in the app, and
 * the download job runs on Android 10 to 13. It reads the factory through an entry point, because
 * Hilt injects only the generated test Application. The check after a stop by the user isn't
 * started here: the runner starts it the first time it waits for it. See ADR 0010.
 */
open class DewfallTestApplicationBase : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = downloadWorkConfiguration(CurrentTestWorkers(this))
}

/**
 * WorkManager keeps its configuration for the whole process, but each test has its own Hilt
 * objects, such as its own in-memory database. So this factory asks the current test's
 * DownloadWorkerFactory for each worker.
 */
private class CurrentTestWorkers(private val application: Application) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        EntryPointAccessors.fromApplication(application, WorkerFactoryEntryPoint::class.java)
            .workerFactory()
            .createWorker(appContext, workerClassName, workerParameters)
}

/** Gives the test Application the download worker's factory. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerFactoryEntryPoint {
    fun workerFactory(): DownloadWorkerFactory
}

/**
 * Hilt generates DewfallTestApplication, a test Application built on DewfallTestApplicationBase.
 */
@CustomTestApplication(DewfallTestApplicationBase::class) interface DewfallTestApplication

/**
 * Runs the emulator tests in DewfallTestApplication instead of DewfallApplication, so Hilt can
 * replace modules with fakes, as Hilt's testing guide describes, while WorkManager starts with the
 * app's own configuration. `:app`'s build names this runner. See docs/testing.md#emulator-tests.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application =
        super.newApplication(
            classLoader,
            DewfallTestApplication_Application::class.java.name,
            context,
        )
}
