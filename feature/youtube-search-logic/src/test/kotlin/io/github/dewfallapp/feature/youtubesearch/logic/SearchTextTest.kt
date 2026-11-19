package io.github.dewfallapp.feature.youtubesearch.logic

import kotlin.test.Test
import kotlin.test.assertEquals

/** Replaces each line break with a space, last first, as the search field's input does. */
private fun withSpaces(text: String): String {
    val result = StringBuilder(text)
    for (range in lineBreaksFromLast(text)) {
        result.replace(range.first, range.last + 1, " ")
    }
    return result.toString()
}

class SearchTextTest {
    @Test
    fun `each kind of line break becomes one space`() {
        assertEquals("sourdough starter", withSpaces("sourdough\nstarter"))
        assertEquals("sourdough starter", withSpaces("sourdough\r\nstarter"))
        assertEquals("sourdough starter", withSpaces("sourdough\rstarter"))
        assertEquals("a b c", withSpaces("a\nb\r\nc"))
    }

    @Test
    fun `line breaks come last first, a CR LF pair as one`() {
        assertEquals(
            listOf(IntRange(8, 8), IntRange(4, 5), IntRange(1, 1)),
            lineBreaksFromLast("a\nbc\r\nde\rf"),
        )
    }

    @Test
    fun `text with no line break has none to replace`() {
        assertEquals(emptyList(), lineBreaksFromLast("sourdough starter"))
        assertEquals(emptyList(), lineBreaksFromLast(""))
    }
}
