package io.github.dewfallapp.core.data.channel

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

// Made-up IDs in a channel ID's form, never real channels'.
private val ASKED = ChannelId("UC" + "0".repeat(21) + "1")
private val ANSWERED = ChannelId("UC" + "0".repeat(21) + "2")

class LayerChannelUploadsRepositoryTest {
    private val asked = mutableListOf<ChannelId>()

    private fun repository(answer: YouTubeResult<ChannelUploads>) =
        LayerChannelUploadsRepository(
            ChannelUploadsRemoteDataSource { channelId ->
                asked += channelId
                answer
            },
        )

    @Test
    fun `the uploads come back from the layer unchanged, a moved channel's ID too`() = runTest {
        val answer =
            YouTubeResult.Success(
                ChannelUploads(
                    Channel(
                        id = ANSWERED,
                        name = "A channel",
                        avatars = emptyList(),
                        description = "",
                    ),
                    videos = emptyList(),
                ),
            )

        val result = repository(answer).latestUploads(ASKED)

        assertSame(answer, result)
        assertEquals(listOf(ASKED), asked)
    }

    @Test
    fun `a failure comes back as the layer gave it`() = runTest {
        val answer = YouTubeResult.Failure(YouTubeError.CHANNEL_CLOSED)

        assertSame(answer, repository(answer).latestUploads(ASKED))
    }
}
