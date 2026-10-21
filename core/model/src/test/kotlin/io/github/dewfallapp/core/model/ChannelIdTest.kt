package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChannelIdTest {
    @Test
    fun `accepts UC and 22 characters from letters, digits, dash and underscore`() {
        assertEquals(VALID, ChannelId(VALID).value)
    }

    @Test
    fun `rejects text that isn't a channel ID`() {
        for (text in INVALID) {
            assertFailsWith<IllegalArgumentException>(text) { ChannelId(text) }
        }
    }

    @Test
    fun `parseOrNull returns the ID for a channel ID`() {
        assertEquals(VALID, ChannelId.parseOrNull(VALID)?.value)
    }

    @Test
    fun `parseOrNull returns null for text that isn't a channel ID`() {
        for (text in INVALID) {
            assertNull(ChannelId.parseOrNull(text), text)
        }
    }

    @Test
    fun `the error message leaves out the text`() {
        val text = "$VALID!"

        val error = assertFailsWith<IllegalArgumentException> { ChannelId(text) }

        assertFalse(error.message.orEmpty().contains(VALID))
    }

    @Test
    fun `toString leaves out the ID`() {
        assertFalse(ChannelId(VALID).toString().contains(VALID))
    }

    private companion object {
        /** A made-up ID, so the tests point to no real channel. */
        const val VALID = "UCabcdefghij-_ABCDEFGH12"

        val INVALID =
            listOf(
                "",
                // The wrong prefix.
                "UDabcdefghij-_ABCDEFGH12",
                "ucabcdefghij-_ABCDEFGH12",
                "abcdefghij-_ABCDEFGH1234",
                // One character too few, then one too many.
                "UCabcdefghij-_ABCDEFGH1",
                "UCabcdefghij-_ABCDEFGH123",
                // A character outside the ID alphabet.
                "UCabcdefghij-_ABCDEFGH1 ",
                "UCabcdefghij-_ABCDEFGH1é",
                // A handle, an old user name and a link path, which aren't channel IDs.
                "@kettleandcrumb",
                "kettleandcrumb",
                "channel/UCabcdefghij-_ABCDEFGH12",
            )
    }
}
