package io.github.dewfallapp.core.youtube.newpipe.live

import io.github.dewfallapp.core.youtube.newpipe.saved.SavedSet
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveResultsTest {
    @Test
    fun `a kind that passed is its name and PASSED`() {
        assertEquals("SEARCH_NASA PASSED", KindResult.passed(SavedSet.SEARCH_NASA).line)
    }

    @Test
    fun `a kind that couldn't be checked names why, skipped or failed`() {
        val skipped =
            KindResult.skipped(SavedSet.VIDEO_BIG_BUCK_BUNNY, UncheckedBecause.RUNNER_BLOCKED)
        val failed = KindResult.failed(SavedSet.VIDEO_BIG_BUCK_BUNNY, UncheckedBecause.NETWORK)

        assertEquals("VIDEO_BIG_BUCK_BUNNY SKIPPED RUNNER_BLOCKED", skipped.line)
        assertEquals("VIDEO_BIG_BUCK_BUNNY FAILED NETWORK", failed.line)
    }

    @Test
    fun `a kind that failed names its reason, then its fields in order`() {
        val result =
            KindResult.failed(
                LiveKind.STREAM_LINKS_BIG_BUCK_BUNNY,
                FailedBecause.LINK_NOT_PLAYABLE,
                ResultField.state("format", FormatKind.AUDIO_ONLY),
                ResultField.state("answer", FirstBytesAnswer.WRONG_STATUS),
                ResultField.count("status", 403),
            )

        assertEquals(
            "STREAM_LINKS_BIG_BUCK_BUNNY FAILED LINK_NOT_PLAYABLE " +
                "format=AUDIO_ONLY answer=WRONG_STATUS status=403",
            result.line,
        )
    }

    @Test
    fun `the results file has one line for each kind, in order, and ends with a newline`() {
        val results =
            listOf(
                KindResult.passed(SavedSet.SEARCH_NASA),
                KindResult.skipped(SavedSet.VIDEO_BIG_BUCK_BUNNY, UncheckedBecause.NETWORK),
                KindResult.failed(LiveKind.NOTHING_CHECKED, FailedBecause.SIMULATED),
            )

        assertEquals(
            "SEARCH_NASA PASSED\n" +
                "VIDEO_BIG_BUCK_BUNNY SKIPPED NETWORK\n" +
                "NOTHING_CHECKED FAILED SIMULATED\n",
            formatResults(results),
        )
    }

    @Test
    fun `every kind with every verdict and reason makes a line that the workflow accepts`() {
        val fields =
            arrayOf(
                ResultField.state("what", WrongContent.NO_HLS_MANIFEST),
                ResultField.count("status", 0),
                ResultField.count("expectedRequests", 7),
            )
        val lines = LIVE_KINDS.flatMap { kind ->
            listOf(KindResult.passed(kind).line) +
                UncheckedBecause.entries.map { KindResult.skipped(kind, it).line } +
                UncheckedBecause.entries.map { KindResult.failed(kind, it).line } +
                FailedBecause.entries.map { KindResult.failed(kind, it).line } +
                FailedBecause.entries.map { KindResult.failed(kind, it, *fields).line }
        }

        for (line in lines) {
            assertTrue(RESULT_LINE.matches(line), line)
        }
    }

    @Test
    fun `the pattern refuses a line that holds anything but names and numbers`() {
        val lines =
            listOf(
                "SEARCH_NASA FAILED WRONG_CONTENT what=https://www.youtube.com/",
                "SEARCH_NASA FAILED WRONG_CONTENT title=Big Buck Bunny",
                "SEARCH_NASA FAILED",
                "SEARCH_NASA PASSED RUNNER_BLOCKED",
                "search_nasa PASSED",
                "SEARCH_NASA PASSED\nOTHER PASSED",
                "SEARCH_NASA FAILED SIMULATED status=-1",
            )

        for (line in lines) {
            assertFalse(RESULT_LINE.matches(line), line)
        }
    }

    @Test
    fun `a count is never negative`() {
        assertFailsWith<IllegalArgumentException> { ResultField.count("status", -1) }
    }

    @Test
    fun `the results are written to their file, in a folder made for them`() {
        val folder = createTempDirectory().toFile()
        val file = folder.resolve("live-youtube-check/results.txt")
        val results = listOf(KindResult.passed(SavedSet.SEARCH_NASA))

        try {
            writeResults(file, results)

            assertEquals("SEARCH_NASA PASSED\n", file.readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `every kind has its own name`() {
        assertEquals(LIVE_KINDS.size, LIVE_KINDS.map { it.name }.toSet().size)
    }
}
