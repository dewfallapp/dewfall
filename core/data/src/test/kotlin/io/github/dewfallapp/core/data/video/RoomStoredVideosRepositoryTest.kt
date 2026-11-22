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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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

private fun row(id: String, publishedAt: Instant?, approximate: Boolean, durationMs: Long?) =
    VideoEntity(
        id = id,
        channelId = "UCaaaaaaaaaaaaaaaaaaaaaa",
        channelName = "Kettle and Crumb",
        title = "Title $id",
        durationMs = durationMs,
        publishedAt = publishedAt,
        publishedApproximate = approximate,
        kind = VideoKind.VIDEO,
        thumbnails = THUMBNAILS,
        firstSeenAt = NOW,
        listingNumber = 1,
        listPosition = 0,
    )

/** Records each save, and can hold it until the test lets it go. */
private class FakeVideoDao(private val gate: CompletableDeferred<Unit>? = null) : VideoDao() {
    val saves = mutableListOf<Pair<ChannelDetails, List<VideoEntity>>>()

    /**
     * The rows the inbox query answers with, and the kind it was asked for. A SharedFlow, because
     * Room emits again after every change, even when the rows are equal, and a StateFlow wouldn't.
     */
    val inbox = MutableSharedFlow<List<VideoEntity>>(replay = 1)
    var inboxKind: VideoKind? = null

    override fun inboxVideos(video: VideoKind): Flow<List<VideoEntity>> {
        inboxKind = video
        return inbox
    }

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

    @Test
    fun `the inbox asks for videos and maps each row in order`() = runTest {
        val dao = FakeVideoDao()
        dao.inbox.tryEmit(
            listOf(
                row("aaaaaaaaaaa", PUBLISHED, approximate = true, durationMs = 720_000),
                row("bbbbbbbbbbb", null, approximate = false, durationMs = null),
            ),
        )
        val repository = RoomStoredVideosRepository(dao, FixedClock(NOW), backgroundScope)

        val videos = repository.inboxVideos().first()

        assertEquals(VideoKind.VIDEO, dao.inboxKind)
        assertEquals(
            listOf(
                summary("aaaaaaaaaaa", PublishedTime(PUBLISHED, isApproximate = true)),
                summary("bbbbbbbbbbb", null).copy(duration = null),
            ),
            videos,
        )
        assertEquals(720_000.milliseconds, videos[0].duration)
    }

    @Test
    fun `the inbox doesn't emit the same videos twice`() = runTest {
        val dao = FakeVideoDao()
        val repository = RoomStoredVideosRepository(dao, FixedClock(NOW), backgroundScope)
        val seen = mutableListOf<List<VideoSummary>>()
        backgroundScope.launch { repository.inboxVideos().toList(seen) }
        runCurrent()

        val rows = listOf(row("aaaaaaaaaaa", PUBLISHED, approximate = false, durationMs = 1))
        dao.inbox.tryEmit(rows)
        runCurrent()
        dao.inbox.tryEmit(rows.map { it.copy() })
        runCurrent()

        assertEquals(1, seen.size)
    }
}
