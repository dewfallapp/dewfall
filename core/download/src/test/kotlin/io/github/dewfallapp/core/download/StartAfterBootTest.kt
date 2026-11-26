package io.github.dewfallapp.core.download

import android.content.Intent
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter
import io.github.dewfallapp.core.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StartAfterBootTest {
    private val lines = mutableListOf<String>()
    private val logger = Logger(LogWriter { _, _, text -> lines += text }, LogLevel.DEBUG)

    @Test
    fun `the boot broadcast starts the queue`() = runTest {
        var starts = 0

        assertTrue(startAfterBoot(Intent.ACTION_BOOT_COMPLETED, logger) { starts++ })

        assertEquals(1, starts)
    }

    @Test
    fun `another action starts nothing`() = runTest {
        var starts = 0

        assertFalse(
            startAfterBoot("android.intent.action.LOCKED_BOOT_COMPLETED", logger) { starts++ },
        )
        assertFalse(startAfterBoot(null, logger) { starts++ })

        assertEquals(0, starts)
    }

    @Test
    fun `a start that throws is logged with no user data`() = runTest {
        assertTrue(
            startAfterBoot(Intent.ACTION_BOOT_COMPLETED, logger) {
                error("The database can't be read")
            },
        )

        assertTrue(lines.any { "Download start after boot failed" in it }, lines.toString())
        assertTrue(lines.none { "The database can't be read" in it }, lines.toString())
    }
}
