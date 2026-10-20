package io.github.dewfallapp.core.logging

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LoggerTest {
    private data class Line(val level: LogLevel, val tag: String, val text: String)

    private val lines = mutableListOf<Line>()

    private fun logger(minLevel: LogLevel) =
        Logger({ level, tag, text -> lines += Line(level, tag, text) }, minLevel)

    @Test
    fun `release builds drop debug and info lines`() {
        logAtEveryLevel(logger(LogLevel.WARN))

        assertEquals(listOf(LogLevel.WARN, LogLevel.ERROR), lines.map { it.level })
    }

    @Test
    fun `debug builds keep every level`() {
        logAtEveryLevel(logger(LogLevel.DEBUG))

        assertEquals(LogLevel.entries, lines.map { it.level })
    }

    @Test
    fun `the tag is Dewfall by default`() {
        logger(LogLevel.DEBUG).info("Started")

        assertEquals("Dewfall", lines.single().tag)
    }

    @Test
    fun `withTag puts the area after Dewfall`() {
        logger(LogLevel.DEBUG).withTag("YouTube").info("Started")

        assertEquals("Dewfall.YouTube", lines.single().tag)
    }

    @Test
    fun `a second withTag replaces the area`() {
        logger(LogLevel.DEBUG).withTag("YouTube").withTag("Data").info("Started")

        assertEquals("Dewfall.Data", lines.single().tag)
    }

    @Test
    fun `fields follow the message, separated by spaces`() {
        logger(LogLevel.DEBUG)
            .info("Loaded uploads", LogField.count("videos", 12), LogField.flag("cached", true))

        assertEquals("Loaded uploads videos=12 cached=true", lines.single().text)
    }

    @Test
    fun `an error goes on the lines after the message, without its message`() {
        val error = IOException("Search for cats failed")

        logger(LogLevel.DEBUG).warn("Search failed", LogField.count("attempt", 2), error = error)

        val text = lines.single().text
        assertEquals(listOf("Search failed attempt=2", "java.io.IOException"), text.lines().take(2))
        assertFalse("cats" in text)
    }

    @Test
    fun `only a kept line builds the error summary`() {
        val logger = logger(LogLevel.ERROR)
        val error = CountingError()

        logger.warn("Dropped", error = error)
        assertEquals(0, error.stackTraceReads)

        logger.error("Kept", error = error)
        assertEquals(1, error.stackTraceReads)
    }

    @Test
    fun `Silent writes nothing and builds nothing`() {
        val error = CountingError()

        Logger.Silent.error("Not written", error = error)

        assertEquals(0, error.stackTraceReads)
    }

    private fun logAtEveryLevel(logger: Logger) {
        logger.debug("Debug line")
        logger.info("Info line")
        logger.warn("Warn line")
        logger.error("Error line")
    }
}

/** An error that counts how often its stack trace is read, which shows when a summary is built. */
private class CountingError : Exception() {
    var stackTraceReads = 0
        private set

    override fun getStackTrace(): Array<StackTraceElement> {
        stackTraceReads++
        return super.getStackTrace()
    }
}
