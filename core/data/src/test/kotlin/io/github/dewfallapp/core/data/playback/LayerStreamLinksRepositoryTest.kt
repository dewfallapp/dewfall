package io.github.dewfallapp.core.data.playback

import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

private val VIDEO = VideoId("aqz-KE-bpKQ")

class LayerStreamLinksRepositoryTest {
    private val asked = mutableListOf<VideoId>()

    private fun repository(answer: YouTubeResult<StreamLinks>) =
        LayerStreamLinksRepository(
            StreamLinksRemoteDataSource { videoId ->
                asked += videoId
                answer
            }
        )

    @Test
    fun `the links come back from the layer unchanged`() = runTest {
        val answer =
            YouTubeResult.Success(
                StreamLinks(
                    videoId = VIDEO,
                    audioFormats = emptyList(),
                    videoFormats = emptyList(),
                    hlsManifestUrl = null,
                    expiresAt = null,
                )
            )

        val result = repository(answer).streamLinks(VIDEO)

        assertSame(answer, result)
        assertEquals(listOf(VIDEO), asked)
    }

    @Test
    fun `a failure comes back as the layer gave it`() = runTest {
        val answer = YouTubeResult.Failure(YouTubeError.BLOCKED)

        assertSame(answer, repository(answer).streamLinks(VIDEO))
    }
}
