package io.github.dewfallapp.buildlogic

import com.android.build.api.dsl.Lint

/**
 * Lint checks that look up versions or library data on the network. They are off so that lint gives
 * the same result on every run. Dependency updates are a separate task, #31.
 */
private val networkLintChecks =
    setOf(
        "NewerVersionAvailable",
        "GradleDependency",
        "AndroidGradlePluginVersion",
        "OutdatedLibrary",
        "RiskyLibrary",
        "PlaySdkIndexNonCompliant",
        "PlaySdkIndexVulnerability",
        "PlaySdkIndexGenericIssues",
        "PlaySdkIndexDeprecated",
    )

/** Lint settings shared by every module. Any warning fails the build. */
internal fun Lint.configureDewfallLint() {
    abortOnError = true
    warningsAsErrors = true
    // Each module checks its own code, so no issue is reported twice.
    checkDependencies = false
    disable += networkLintChecks
}
