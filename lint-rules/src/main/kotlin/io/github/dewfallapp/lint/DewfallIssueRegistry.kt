package io.github.dewfallapp.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Dewfall's own lint checks. Lint finds this class through
 * META-INF/services/com.android.tools.lint.client.api.IssueRegistry, and runs the checks on every
 * module of the app, tests included. See docs/architecture.md#dewfalls-lint-checks.
 */
internal class DewfallIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> =
        listOf(
            AndroidLogDetector.ISSUE,
            ConsoleOutputDetector.ISSUE,
        )

    // The build compiles this module against the lint that AGP runs, so both API levels are the
    // lint API level of that version, and lint never reports the checks as out of date.
    override val api: Int = CURRENT_API

    override val minApi: Int = CURRENT_API

    override val vendor: Vendor =
        Vendor(
            vendorName = "Dewfall",
            identifier = "dewfall",
            feedbackUrl = "https://github.com/dewfallapp/dewfall/issues",
            contact = "https://github.com/dewfallapp/dewfall",
        )
}
