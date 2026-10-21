package io.github.dewfallapp.logging

import io.github.dewfallapp.core.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AGP creates unit tests for the debug build only, so the release choice is tested through minLevel
 * instead of BuildConfig.
 */
class LoggingModuleTest {
    @Test
    fun `debug builds keep every level`() {
        assertEquals(LogLevel.DEBUG, minLevel(debug = true))
    }

    @Test
    fun `release builds keep warnings and errors`() {
        assertEquals(LogLevel.WARN, minLevel(debug = false))
    }
}
