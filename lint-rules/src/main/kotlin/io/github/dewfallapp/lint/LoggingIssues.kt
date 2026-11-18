package io.github.dewfallapp.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.util.EnumSet

/** ADR 0007, the decision that every issue here enforces. */
private const val LOGGING_ADR_URL =
    "https://github.com/dewfallapp/dewfall/blob/main/docs/adr/0007-how-the-app-logs.md"

/** Lint ranks issues from 1 to 10, and these break the app's privacy rules. */
private const val HIGHEST_PRIORITY = 10

/**
 * Creates one of Dewfall's logging issues. Each is an error in main and test sources alike.
 *
 * Its suppress names are an empty list, so nothing can suppress it. Lint reports `@Suppress`,
 * `@SuppressLint`, `//noinspection` and a `disable` setting for it as errors of their own, and
 * ignores it in lint.xml files, baselines, severity settings and `checkOnly`. The only exceptions
 * are the two writers in LogWriterClass.kt, so an exception can't be added without changing this
 * module and its tests. See ADR 0007.
 */
internal fun loggingIssue(
    id: String,
    briefDescription: String,
    explanation: String,
    detector: Class<out Detector>,
): Issue =
    Issue.create(
        id = id,
        briefDescription = briefDescription,
        explanation = explanation,
        implementation = Implementation(detector, EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)),
        moreInfo = LOGGING_ADR_URL,
        category = Category.SECURITY,
        priority = HIGHEST_PRIORITY,
        severity = Severity.ERROR,
        suppressAnnotations = emptyList(),
    )
