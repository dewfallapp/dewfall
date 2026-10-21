package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class VideoIdTest {
    @Test
    fun `accepts 11 characters from letters, digits, dash and underscore`() {
        assertEquals(VALID, VideoId(VALID).value)
    }

    @Test
    fun `rejects text that isn't a video ID`() {
        for (text in INVALID) {
            assertFailsWith<IllegalArgumentException>(text) { VideoId(text) }
        }
    }

    @Test
    fun `parseOrNull returns the ID for a video ID`() {
        assertEquals(VALID, VideoId.parseOrNull(VALID)?.value)
    }

    @Test
    fun `parseOrNull returns null for text that isn't a video ID`() {
        for (text in INVALID) {
            assertNull(VideoId.parseOrNull(text), text)
        }
    }

    @Test
    fun `the error message leaves out the text`() {
        val text = "$VALID!"

        val error = assertFailsWith<IllegalArgumentException> { VideoId(text) }

        assertFalse(error.message.orEmpty().contains(VALID))
    }

    @Test
    fun `toString leaves out the ID`() {
        assertFalse(VideoId(VALID).toString().contains(VALID))
    }

    private companion object {
        /** A made-up ID, so the tests point to no real video. */
        const val VALID = "aB3_-xYz09Q"

        val INVALID =
            listOf(
                "",
                "aB3_-xYz09",
                "aB3_-xYz09QQ",
                "aB3_-xYz0 Q",
                "aB3_-xYz0+Q",
                "aB3_-xYz0/Q",
                "aB3_-xYz0=Q",
                // Letters and digits outside ASCII.
                "aB3_-xYz0éQ",
                "aB3_-xYz0٣Q",
            )
    }
}
