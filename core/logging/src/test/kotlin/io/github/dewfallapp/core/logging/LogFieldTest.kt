package io.github.dewfallapp.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LogFieldTest {
    @Test
    fun `count writes the name and the number`() {
        assertEquals("videos=12", LogField.count("videos", 12).text)
        assertEquals("bytes=5000000000", LogField.count("bytes", 5_000_000_000L).text)
    }

    @Test
    fun `flag writes true or false`() {
        assertEquals("cached=true", LogField.flag("cached", true).text)
        assertEquals("cached=false", LogField.flag("cached", false).text)
    }

    @Test
    fun `duration is written in ISO 8601 form`() {
        assertEquals("took=PT1.500S", LogField.duration("took", 1500.milliseconds).text)
        assertEquals("took=PT2S", LogField.duration("took", 2.seconds).text)
    }

    @Test
    fun `state writes the constant's name, not its toString`() {
        assertEquals("source=CACHE", LogField.state("source", Source.CACHE).text)
    }

    private enum class Source {
        CACHE;

        override fun toString() = "a title the user saw"
    }
}
