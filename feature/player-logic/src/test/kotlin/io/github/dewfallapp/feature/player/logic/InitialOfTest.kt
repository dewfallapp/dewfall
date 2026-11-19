package io.github.dewfallapp.feature.player.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The tests run on JDK 17, whose BreakIterator splits most emoji sequences, such as flags, into
 * code points, while a phone's ICU keeps them whole. So these cover only the cases on which both
 * agree. A check of flags and emoji sequences on an emulator is still open, as task 7.3 in
 * openspec/changes/archive/2026-11-19-video-screen/tasks.md says.
 */
class InitialOfTest {
    @Test
    fun `a name's first letter, as it is written`() {
        assertEquals("L", initialOf("Larkspur Lectures"))
        assertEquals("k", initialOf("kettle and crumb"))
    }

    @Test
    fun `a letter keeps its combining accent`() {
        // "e" followed by a combining acute accent, U+0301.
        assertEquals("e\u0301", initialOf("e\u0301tude"))
    }

    @Test
    fun `an emoji outside the Basic Multilingual Plane stays whole`() {
        // U+1F600, two UTF-16 code units.
        assertEquals("\uD83D\uDE00", initialOf("\uD83D\uDE00 Smiles"))
    }

    @Test
    fun `leading whitespace is skipped`() {
        assertEquals("L", initialOf("  Larkspur"))
    }

    @Test
    fun `a blank name has no initial`() {
        assertNull(initialOf(""))
        assertNull(initialOf("   "))
    }
}
