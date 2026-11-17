package io.github.dewfallapp.lint

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DewfallIssueRegistryTest {
    @BeforeTest
    fun setUp() {
        // Lint's classes refuse to load issues until a client names itself, as lint-tests does.
        LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
    }

    @Test
    fun `uses the lint API level it was compiled against`() {
        val registry = DewfallIssueRegistry()

        assertEquals(CURRENT_API, registry.api)
        assertEquals(CURRENT_API, registry.minApi)
    }

    @Test
    fun `names Dewfall as the vendor`() {
        val vendor = DewfallIssueRegistry().vendor

        assertEquals("Dewfall", vendor.vendorName)
        assertEquals("https://github.com/dewfallapp/dewfall/issues", vendor.feedbackUrl)
    }

    @Test
    fun `makes every issue an error that nothing can suppress`() {
        for (issue in DewfallIssueRegistry().issues) {
            assertEquals(Severity.ERROR, issue.defaultSeverity, issue.id)
            assertTrue(issue.isEnabledByDefault(), issue.id)
            assertEquals(emptyList(), issue.suppressNames?.toList(), issue.id)
        }
    }

    @Test
    fun `checks test sources too`() {
        for (issue in DewfallIssueRegistry().issues) {
            assertTrue(Scope.TEST_SOURCES in issue.implementation.scope, issue.id)
        }
    }

    @Test
    fun `gives every issue a unique ID that starts with Dewfall`() {
        val ids = DewfallIssueRegistry().issues.map { it.id }

        assertTrue(ids.all { it.startsWith("Dewfall") }, ids.toString())
        assertEquals(ids.distinct(), ids)
    }
}
