package io.github.dewfallapp.core.logging

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorSummaryTest {
    @Test
    fun `writes the class name and frames, but not the message`() {
        val summary = errorSummary(IOException("Video $VIDEO_ID could not be loaded"))

        val lines = summary.lines()
        assertEquals("java.io.IOException", lines.first())
        assertTrue(lines[1].startsWith("  at ${ErrorSummaryTest::class.java.name}."))
        assertFalse(VIDEO_ID in summary)
    }

    @Test
    fun `writes each cause by class name, without its message`() {
        val error =
            IllegalStateException("Search for cats failed", IOException("Channel $CHANNEL_ID"))

        val summary = errorSummary(error)

        assertTrue("\nCaused by: java.io.IOException\n" in summary)
        assertFalse("cats" in summary)
        assertFalse(CHANNEL_ID in summary)
    }

    @Test
    fun `never uses the error's own toString`() {
        val summary = errorSummary(TalkativeError())

        assertFalse("cats" in summary)
    }

    @Test
    fun `writes each frame's file and line, or why it has none`() {
        val error =
            IOException().apply {
                stackTrace =
                    arrayOf(
                        StackTraceElement("a.B", "run", "B.kt", 12),
                        StackTraceElement("a.B", "load", null, -1),
                        StackTraceElement("a.B", "read", "B.kt", -1),
                        // A line number of -2 marks a native method.
                        StackTraceElement("a.B", "poll", null, -2),
                    )
            }

        assertEquals(
            listOf(
                "java.io.IOException",
                "  at a.B.run(B.kt:12)",
                "  at a.B.load(Unknown Source)",
                "  at a.B.read(B.kt)",
                "  at a.B.poll(Native Method)",
            ),
            errorSummary(error).lines(),
        )
    }

    @Test
    fun `writes at most 30 frames for each error, then how many it left out`() {
        val error =
            IOException().apply {
                stackTrace = Array(40) { StackTraceElement("a.B", "run$it", "B.kt", it + 1) }
            }

        val lines = errorSummary(error).lines()

        assertEquals(30, lines.count { it.startsWith("  at ") })
        assertEquals("  at a.B.run29(B.kt:30)", lines[30])
        assertEquals("  ... 10 more frames", lines.last())
    }

    @Test
    fun `writes at most 10 causes, then says that more were left out`() {
        // The error and 15 causes below it.
        val error =
            (1..15).fold<Int, Throwable>(IOException()) { cause, _ -> IllegalStateException(cause) }

        val summary = errorSummary(error)

        assertEquals(10, summary.lines().count { it.startsWith("Caused by: ") })
        assertTrue(summary.endsWith("\nMore causes not shown"))
    }

    @Test
    fun `stops at a loop in the causes`() {
        val first = IllegalStateException()
        val second = IOException(first)
        first.initCause(second)

        val summary = errorSummary(first)

        assertEquals(2, summary.lines().count { it.startsWith("Caused by: ") })
        assertTrue(summary.endsWith("\nCaused by: java.lang.IllegalStateException, shown above"))
    }

    /** An error whose toString holds user data, as some library errors do. */
    private class TalkativeError : Exception() {
        override fun toString() = "Search for cats failed"
    }

    private companion object {
        // Made-up IDs in YouTube's formats.
        const val VIDEO_ID = "aaaaaaaaaaa"
        const val CHANNEL_ID = "UCaaaaaaaaaaaaaaaaaaaaaa"
    }
}
