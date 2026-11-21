package io.github.dewfallapp

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs the emulator tests in HiltTestApplication instead of DewfallApplication, so Hilt can replace
 * modules with fakes, as Hilt's testing guide describes. DewfallApplication has no code of its own,
 * so the tests lose nothing. Once it gets some, such as WorkManager's Configuration.Provider, the
 * tests need Hilt's @CustomTestApplication instead, or they run without it. `:app`'s build names
 * this runner. See docs/testing.md#emulator-tests.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application =
        super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
