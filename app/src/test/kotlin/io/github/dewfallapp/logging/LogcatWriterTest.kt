package io.github.dewfallapp.logging

import io.github.dewfallapp.core.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogcatWriterTest {
    private data class Entry(val priority: Int, val tag: String, val text: String)

    private val entries = mutableListOf<Entry>()

    private val writer = LogcatWriter { priority, tag, text ->
        entries += Entry(priority, tag, text)
    }

    @Test
    fun `writes each level with its logcat priority`() {
        LogLevel.entries.forEach { writer.write(it, "Dewfall", "Started") }

        // android.util.Log is named in full, because detekt forbids importing it.
        assertEquals(
            listOf(
                android.util.Log.DEBUG,
                android.util.Log.INFO,
                android.util.Log.WARN,
                android.util.Log.ERROR,
            ),
            entries.map { it.priority },
        )
    }

    @Test
    fun `writes short text as one entry with its tag`() {
        writer.write(LogLevel.WARN, "Dewfall.YouTube", "Search failed")

        assertEquals(
            listOf(Entry(android.util.Log.WARN, "Dewfall.YouTube", "Search failed")),
            entries,
        )
    }

    @Test
    fun `writes long text as several entries with the same priority and tag`() {
        // 300 lines of 40 characters, about 12,300 bytes.
        val text = List(300) { "x".repeat(40) }.joinToString("\n")

        writer.write(LogLevel.ERROR, "Dewfall.YouTube", text)

        assertTrue(entries.size > 1)
        assertTrue(entries.all { it.priority == android.util.Log.ERROR })
        assertTrue(entries.all { it.tag == "Dewfall.YouTube" })
        assertEquals(text, entries.joinToString("\n") { it.text })
    }
}
