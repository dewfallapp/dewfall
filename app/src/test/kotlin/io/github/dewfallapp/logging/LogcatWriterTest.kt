// The test compares priorities with android.util.Log's constants, which detekt's ForbiddenImport
// rejects outside LogcatWriter.kt and this test, as ADR 0007 says. detekt can suppress an import
// finding only for a whole file, because an import can't carry an annotation. That also lifts the
// rule's other entries, such as GlobalScope, in this file.
@file:Suppress("ForbiddenImport")

package io.github.dewfallapp.logging

import android.util.Log
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
        for (level in LogLevel.entries) {
            writer.write(level, "Dewfall", "Started")
        }

        assertEquals(listOf(Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR), entries.map { it.priority })
    }

    @Test
    fun `writes short text as one entry with its tag`() {
        writer.write(LogLevel.WARN, "Dewfall.YouTube", "Search failed")

        assertEquals(
            listOf(Entry(Log.WARN, "Dewfall.YouTube", "Search failed")),
            entries,
        )
    }

    @Test
    fun `writes long text as several entries with the same priority and tag, losing nothing`() {
        // 300 lines of 40 characters, about 12,300 bytes.
        val text = List(300) { "x".repeat(40) }.joinToString("\n")

        writer.write(LogLevel.ERROR, "Dewfall.YouTube", text)

        assertTrue(entries.size > 1)
        assertTrue(entries.all { it.priority == Log.ERROR })
        assertTrue(entries.all { it.tag == "Dewfall.YouTube" })
        assertEquals(text, entries.joinToString("\n") { it.text })
    }
}
