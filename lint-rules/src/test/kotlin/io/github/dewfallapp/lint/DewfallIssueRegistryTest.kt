package io.github.dewfallapp.lint

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.CURRENT_API
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
