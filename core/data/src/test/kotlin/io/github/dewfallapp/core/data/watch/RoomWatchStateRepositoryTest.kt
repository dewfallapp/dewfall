package io.github.dewfallapp.core.data.watch

import io.github.dewfallapp.core.data.database.FixedClock
import io.github.dewfallapp.core.database.WatchStateDao
import io.github.dewfallapp.core.database.WatchStateEntity
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val NOW = Instant.parse("2026-11-22T12:00:00Z")
private val EARLIER = Instant.parse("2026-11-20T12:00:00Z")
private val VIDEO = VideoId("aaaaaaaaaaa")

/** Records each call as text, and can hold a call until the test lets it go. */
private class FakeWatchStateDao(
    private val states: Flow<WatchStateEntity?> = flowOf(null),
    private val gate: CompletableDeferred<Unit>? = null,
) : WatchStateDao() {
    val calls = mutableListOf<String>()

    override fun watchState(videoId: String): Flow<WatchStateEntity?> {
        calls += "read $videoId"
        return states
    }

    override suspend fun savePosition(videoId: String, positionMs: Long, now: Instant) {
        gate?.await()
        calls += "position $videoId $positionMs $now"
    }

    override suspend fun markWatched(videoId: String, now: Instant) {
        gate?.await()
        calls += "watched $videoId $now"
    }

    override suspend fun markUnwatched(videoId: String, now: Instant) {
        gate?.await()
        calls += "unwatched $videoId $now"
    }

    override suspend fun insertIfMissing(state: WatchStateEntity) = error("Not used")

    override suspend fun updatePosition(videoId: String, positionMs: Long, now: Instant) =
        error("Not used")

    override suspend fun updateWatched(videoId: String, now: Instant) = error("Not used")
}

// runCurrent is still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class RoomWatchStateRepositoryTest {
    @Test
    fun `writes pass the ID, the milliseconds and the clock's time`() = runTest {
        val dao = FakeWatchStateDao()
        val repository = RoomWatchStateRepository(dao, FixedClock(NOW), backgroundScope)

        repository.savePosition(VIDEO, 750.seconds + 250.milliseconds)
        repository.markWatched(VIDEO)
        repository.markUnwatched(VIDEO)

        assertEquals(
            listOf(
                "position aaaaaaaaaaa 750250 $NOW",
                "watched aaaaaaaaaaa $NOW",
                "unwatched aaaaaaaaaaa $NOW",
            ),
            dao.calls,
        )
    }

    @Test
    fun `rows come back as watch states, without repeats`() = runTest {
        val row = WatchStateEntity("aaaaaaaaaaa", 90_000, EARLIER, NOW)
        val dao = FakeWatchStateDao(states = flowOf(null, row, row, null))
        val repository = RoomWatchStateRepository(dao, FixedClock(NOW), backgroundScope)

        assertEquals(
            listOf(null, WatchState(90.seconds, EARLIER, NOW), null),
            repository.watchState(VIDEO).toList(),
        )
    }

    @Test
    fun `a position save finishes when the caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = FakeWatchStateDao(gate = gate)
        val repository = RoomWatchStateRepository(dao, FixedClock(NOW), backgroundScope)
        val caller = launch { repository.savePosition(VIDEO, 5.seconds) }
        runCurrent()

        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("position aaaaaaaaaaa 5000 $NOW"), dao.calls)
    }
}
