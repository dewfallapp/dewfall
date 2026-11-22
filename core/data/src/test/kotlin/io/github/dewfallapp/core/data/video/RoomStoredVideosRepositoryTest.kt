package io.github.dewfallapp.core.data.video

import io.github.dewfallapp.core.data.database.FixedClock
import io.github.dewfallapp.core.database.ChannelDetails
import io.github.dewfallapp.core.database.ChannelEntity
import io.github.dewfallapp.core.database.VideoDao
import io.github.dewfallapp.core.database.VideoEntity
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val NOW = Instant.parse("2026-11-22T12:00:00Z")
private val PUBLISHED = Instant.parse("2026-11-19T12:00:00Z")
private val THUMBNAILS = listOf(Thumbnail("https://i.example.invalid/vi/x/hq.jpg", 480, 360))
private val CHANNEL =
    Channel(ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"), "Kettle and Crumb", THUMBNAILS, "Bread.")

private fun summary(id: String, published: PublishedTime?, kind: VideoKind = VideoKind.VIDEO) =
    VideoSummary(
        id = VideoId(id),
        title = "Title $id",
        channelId = CHANNEL.id,
        channelName = CHANNEL.name,
        duration = if (kind == VideoKind.VIDEO) 12.minutes else null,
        published = published,
        thumbnails = THUMBNAILS,
        kind = kind,
    )

/** Records each save, and can hold it until the test lets it go. */
private class FakeVideoDao(private val gate: CompletableDeferred<Unit>? = null) : VideoDao() {
    val saves = mutableListOf<Pair<ChannelDetails, List<VideoEntity>>>()

    override suspend fun saveChannelUploads(channel: ChannelDetails, videos: List<VideoEntity>) {
        gate?.await()
        saves += channel to videos
    }

    override suspend fun video(id: String): VideoEntity? = error("Not used")

    override fun channelVideos(channelId: String): Flow<List<VideoEntity>> = emptyFlow()

    override suspend fun highestListingNumber(): Long? = error("Not used")

    override suspend fun videosWithIds(ids: List<String>): List<VideoEntity> = error("Not used")

    override suspend fun insertVideos(videos: List<VideoEntity>) = error("Not used")

    override suspend fun updateVideos(videos: List<VideoEntity>) = error("Not used")

    override suspend fun channelWithId(id: String) = error("Not used")

    override suspend fun updateChannel(channel: ChannelEntity) = error("Not used")
}

// runCurrent is still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class RoomStoredVideosRepositoryTest {
    private val uploads =
        ChannelUploads(
            CHANNEL,
            listOf(
                summary("aaaaaaaaaaa", PublishedTime(PUBLISHED, isApproximate = true)),
                summary("bbbbbbbbbbb", null, VideoKind.UPCOMING),
            ),
        )

    @Test
    fun `a save passes the channel's details and each video at its place`() = runTest {
        val dao = FakeVideoDao()
        val repository = RoomStoredVideosRepository(dao, FixedClock(NOW), backgroundScope)

        repository.saveUploads(uploads)

        val (details, videos) = dao.saves.single()
        assertEquals(
            ChannelDetails("UCaaaaaaaaaaaaaaaaaaaaaa", "Kettle and Crumb", "Bread.", THUMBNAILS),
            details,
        )
        assertEquals(
            listOf(
                VideoEntity(
                    id = "aaaaaaaaaaa",
                    channelId = "UCaaaaaaaaaaaaaaaaaaaaaa",
                    channelName = "Kettle and Crumb",
                    title = "Title aaaaaaaaaaa",
                    durationMs = 720_000,
                    publishedAt = PUBLISHED,
                    publishedApproximate = true,
                    kind = VideoKind.VIDEO,
                    thumbnails = THUMBNAILS,
                    firstSeenAt = NOW,
                    listingNumber = null,
                    listPosition = 0,
                ),
                VideoEntity(
                    id = "bbbbbbbbbbb",
                    channelId = "UCaaaaaaaaaaaaaaaaaaaaaa",
                    channelName = "Kettle and Crumb",
                    title = "Title bbbbbbbbbbb",
                    durationMs = null,
                    publishedAt = null,
                    publishedApproximate = false,
                    kind = VideoKind.UPCOMING,
                    thumbnails = THUMBNAILS,
                    firstSeenAt = NOW,
                    listingNumber = null,
                    listPosition = 1,
                ),
            ),
            videos,
        )
    }

    @Test
    fun `a save finishes when the caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = FakeVideoDao(gate)
        val repository = RoomStoredVideosRepository(dao, FixedClock(NOW), backgroundScope)
        val caller = launch { repository.saveUploads(uploads) }
        runCurrent()

        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, dao.saves.size)
    }
}
