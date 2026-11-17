package io.github.dewfallapp.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Project
import org.gradle.kotlin.dsl.project

/**
 * The module that holds Dewfall's own lint checks. See docs/architecture.md#dewfalls-lint-checks.
 */
private const val LINT_RULES_MODULE = ":lint-rules"

/**
 * Lint checks that look up versions or library data on the network. They are off so that lint gives
 * the same result on every run. Renovate's pull requests bring new versions instead. See
 * docs/testing.md#dependency-updates.
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

/**
 * Runs Dewfall's own lint checks from :lint-rules when lint checks this module, its tests included.
 * The lintChecks configuration only feeds lint, so nothing from :lint-rules reaches the app.
 */
internal fun Project.addDewfallLintChecks() {
    dependencies.add("lintChecks", dependencies.project(LINT_RULES_MODULE))
}
