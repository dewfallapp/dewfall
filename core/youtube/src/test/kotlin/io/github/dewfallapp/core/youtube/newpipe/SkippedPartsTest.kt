package io.github.dewfallapp.core.youtube.newpipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.schabi.newpipe.extractor.exceptions.ParsingException

class SkippedPartsTest {
    private val lines = mutableListOf<String>()
    private val log = recordingLogger(lines).withTag(LOG_AREA)

    @Test
    fun `an optional part that NewPipeExtractor can't parse gets its default and is counted`() {
        val skipped = SkippedParts(YouTubeCall.VIDEO_DETAILS)

        val value = skipped.orDefault("default") { throw ParsingException("Changed") }
        skipped.warnIfAny(log)

        assertEquals("default", value)
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=VIDEO_DETAILS dropped=0 " +
                    "parseErrors=1",
            ),
            lines,
        )
    }

    @Test
    fun `an optional part that is simply absent isn't counted`() {
        val skipped = SkippedParts(YouTubeCall.VIDEO_DETAILS)

        val value = skipped.orDefault<String?>("default") { null }
        skipped.warnIfAny(log)

        assertEquals(null, value)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `an exception that isn't a parse error goes on to the call`() {
        val skipped = SkippedParts(YouTubeCall.VIDEO_DETAILS)

        assertFailsWith<IllegalStateException> {
            skipped.orDefault("default") { throw IllegalStateException("Bug") }
        }
    }

    @Test
    fun `everything that a call couldn't read goes into one warning`() {
        val skipped = SkippedParts(YouTubeCall.LATEST_UPLOADS)
        val kept = mutableListOf<String>()

        skipped.addOrDrop(kept, "kept")
        skipped.addOrDrop(kept, null)
        skipped.addOrDrop(kept, null)
        skipped.addParseErrors(3)
        skipped.orDefault(0) { throw ParsingException("Changed") }
        skipped.warnIfAny(log)

        assertEquals(listOf("kept"), kept)
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=LATEST_UPLOADS dropped=2 " +
                    "parseErrors=4",
            ),
            lines,
        )
    }
}
