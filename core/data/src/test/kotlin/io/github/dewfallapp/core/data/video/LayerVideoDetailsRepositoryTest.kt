package io.github.dewfallapp.core.data.video

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

private val VIDEO = VideoId("aqz-KE-bpKQ")

class LayerVideoDetailsRepositoryTest {
    private val asked = mutableListOf<VideoId>()

    private fun repository(answer: YouTubeResult<VideoDetails>) =
        LayerVideoDetailsRepository(
            VideoDetailsRemoteDataSource { videoId ->
                asked += videoId
                answer
            }
        )

    @Test
    fun `the details come back from the layer unchanged`() = runTest {
        val answer =
            YouTubeResult.Success(
                VideoDetails(
                    id = VIDEO,
                    title = "A title",
                    channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
                    channelName = "A channel",
                    channelAvatars = emptyList(),
                    description = "",
                    duration = null,
                    published = null,
                    thumbnails = emptyList(),
                    isLive = false,
                )
            )

        val result = repository(answer).videoDetails(VIDEO)

        assertSame(answer, result)
        assertEquals(listOf(VIDEO), asked)
    }

    @Test
    fun `a failure comes back as the layer gave it`() = runTest {
        val answer = YouTubeResult.Failure(YouTubeError.UNAVAILABLE)

        assertSame(answer, repository(answer).videoDetails(VIDEO))
    }
}
