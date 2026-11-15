package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class NewPipeYouTubeTest {
    @Test
    fun `the public class searches through the layer, and building it sends nothing`() = runTest {
        val lines = mutableListOf<String>()
        val youTube = NewPipeYouTube(recordingLogger(lines), StandardTestDispatcher(testScheduler))

        // A blank query sends no request, so the test stays off the network.
        val result = youTube.search(" ")

        assertEquals(
            YouTubeResult.Success(SearchResults(channels = emptyList(), videos = emptyList())),
            result,
        )
        assertEquals(emptyList(), lines)
    }
}
