package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SearchReplayTest {
    private val lines = mutableListOf<String>()

    @Test
    fun `a search reads channels and videos from YouTube's answer, and drops nothing`() = runTest {
        val results = savedYouTube().call { search(NASA_QUERY) }.valueOrFail()

        // Which of NASA's channels YouTube lists changes from one recording to the next.
        assertTrue(results.channels.any { "NASA" in it.name }, results.channels.toString())
        assertTrue(results.channels.all { it.avatars.isNotEmpty() })
        assertTrue(results.videos.isNotEmpty())
        // A search for NASA lists some of its Shorts, which the layer marks as Shorts.
        assertTrue(results.videos.any { it.kind == VideoKind.SHORT })
        assertTrue(results.videos.any { it.kind == VideoKind.VIDEO })
        // No item lacked a required field and nothing failed to parse, so the layer logged nothing.
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a search sends one request`() = runTest {
        val youTube = savedYouTube()

        youTube.call { search(NASA_QUERY) }

        assertEquals(SavedSet.SEARCH_NASA.requests, youTube.sentRequests)
        assertEquals(1, youTube.countedRequests)
    }

    private fun TestScope.savedYouTube() =
        SavedYouTube(
            SavedSet.SEARCH_NASA,
            recordingLogger(lines),
            StandardTestDispatcher(testScheduler),
        )
}
