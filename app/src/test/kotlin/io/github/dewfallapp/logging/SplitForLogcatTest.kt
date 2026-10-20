package io.github.dewfallapp.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitForLogcatTest {
    @Test
    fun `short text stays one entry`() {
        val text = "Search failed\njava.io.IOException"

        assertEquals(listOf(text), splitForLogcat(text))
    }

    @Test
    fun `splits ASCII text at line breaks`() {
        val text = (1..6).joinToString("\n") { "line $it" }

        // Each line is 6 bytes, so two lines and the break between them fill 13.
        assertEquals(
            listOf("line 1\nline 2", "line 3\nline 4", "line 5\nline 6"),
            splitForLogcat(text, maxBytes = 13),
        )
    }

    @Test
    fun `keeps every entry within 4000 bytes`() {
        // As many lines as an error summary at its limits: 11 errors, each a class name and 30
        // frames.
        val text = List(341) { "  at io.github.dewfallapp.a.B.run(B.kt:$it)" }.joinToString("\n")

        val entries = splitForLogcat(text)

        assertTrue(entries.size > 1)
        assertTrue(entries.all { it.encodeToByteArray().size <= 4000 })
        assertEquals(text, entries.joinToString("\n"))
    }

    @Test
    fun `keeps an empty line`() {
        val text = "aaaaa\n\nbbbbb"

        val entries = splitForLogcat(text, maxBytes = 6)

        assertEquals(listOf("aaaaa\n", "bbbbb"), entries)
        assertEquals(text, entries.joinToString("\n"))
    }

    @Test
    fun `splits an ASCII line longer than the limit by bytes`() {
        assertEquals(
            listOf("a".repeat(10), "a".repeat(10), "a".repeat(5)),
            splitForLogcat("a".repeat(25), maxBytes = 10),
        )
    }

    @Test
    fun `never splits inside a two-byte or three-byte character`() {
        // é takes 2 bytes in UTF-8, and 日 takes 3.
        assertEquals(listOf("ééééé", "éé"), splitForLogcat("é".repeat(7), maxBytes = 10))
        assertEquals(listOf("日日日", "日日"), splitForLogcat("日".repeat(5), maxBytes = 10))
    }

    @Test
    fun `counts a character outside the Basic Multilingual Plane as 6 bytes`() {
        // An emoji takes 4 bytes in UTF-8, which ART hands to liblog, and 6 in the modified UTF-8
        // that the JNI spec asks for. Counting 6 keeps each entry within the limit in both.
        val entries = splitForLogcat("😀".repeat(5), maxBytes = 12)

        assertEquals(listOf("😀😀", "😀😀", "😀"), entries)
        assertTrue(entries.all { it.encodeToByteArray().size <= 12 })
    }

    @Test
    fun `starts a new entry after a split line`() {
        val text = "a".repeat(12) + "\nbb\ncc"

        assertEquals(
            listOf("a".repeat(10), "aa", "bb\ncc"),
            splitForLogcat(text, maxBytes = 10),
        )
    }
}
