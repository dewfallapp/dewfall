package io.github.dewfallapp.core.playback

import androidx.media3.common.C
import io.github.dewfallapp.core.data.watch.WatchStateRepository
import io.github.dewfallapp.core.logging.LogLevel
import io.github.dewfallapp.core.logging.LogWriter
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private val VIDEO = VideoId("aqz-KE-bpKQ")
private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private val NOW = Instant.fromEpochMilliseconds(1_763_800_000_000)

/**
 * A repository that records each call in order. A test can hold the next write until it lets it go,
 * make the next write throw, or make reads throw.
 */
private class FakeWatchStates : WatchStateRepository {
    val calls = mutableListOf<String>()
    val state = MutableStateFlow<WatchState?>(null)
    var hold: CompletableDeferred<Unit>? = null
    var failNextWrite = false
    var failNextMark = false
    var failReads = false
    var failNextReads = 0

    override fun watchState(videoId: VideoId): Flow<WatchState?> =
        when {
            failReads -> flow { throw IllegalStateException("The database can't be read") }
            failNextReads > 0 -> {
                failNextReads--
                flow { throw IllegalStateException("The database can't be read") }
            }
            else -> state
        }

    override suspend fun savePosition(videoId: VideoId, position: Duration) {
        calls += "save $position"
        write()
        state.update { (it ?: WatchState(Duration.ZERO, null, NOW)).copy(position = position) }
    }

    override suspend fun markWatched(videoId: VideoId) {
        calls += "mark"
        write()
        if (failNextMark) {
            failNextMark = false
            throw IllegalStateException("The disk is full")
        }
        state.update {
            val saved = it ?: WatchState(Duration.ZERO, null, NOW)
            saved.copy(watchedAt = saved.watchedAt ?: NOW)
        }
    }

    override suspend fun markUnwatched(videoId: VideoId) {
        calls += "unmark"
        state.update { it?.copy(watchedAt = null) }
    }

    private suspend fun write() {
        hold?.let {
            hold = null
            it.await()
        }
        if (failNextWrite) {
            failNextWrite = false
            throw IllegalStateException("The disk is full")
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WatchProgressTest {
    private val repository = FakeWatchStates()
    private val lines = mutableListOf<String>()

    private fun TestScope.progress(): WatchProgress {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = Logger(LogWriter { _, _, text -> lines += text }, LogLevel.DEBUG)
        return WatchProgress(
            repository,
            PlaybackLog(logger),
            PlaybackDispatchers(dispatcher, dispatcher),
        )
    }

    @Test
    fun `writes land in the order they were asked for, one at a time`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        val held = CompletableDeferred<Unit>()
        repository.hold = held

        progress.save(10 * MINUTE_MS, HOUR_MS)
        progress.save(11 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()
        assertEquals(listOf("save 10m"), repository.calls)

        held.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("save 10m", "save 11m"), repository.calls)
    }

    @Test
    fun `a read waits for the writes asked for before it`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        val held = CompletableDeferred<Unit>()
        repository.hold = held

        progress.save(10 * MINUTE_MS, HOUR_MS)
        val read = async { progress.storedPosition(VIDEO) }
        advanceUntilIdle()
        assertFalse(read.isCompleted)

        held.complete(Unit)
        advanceUntilIdle()
        assertEquals(10.minutes, read.await())
    }

    @Test
    fun `a failed write logs one line with no video or position, and the next one lands`() =
        runTest {
            val progress = progress()
            progress.itemChanged("p1", VIDEO, live = false)
            repository.failNextWrite = true

            progress.save(10 * MINUTE_MS, HOUR_MS)
            progress.save(11 * MINUTE_MS, HOUR_MS)
            advanceUntilIdle()

            assertEquals(listOf("save 10m", "save 11m"), repository.calls)
            assertEquals(11.minutes, repository.state.value?.position)
            assertEquals(1, lines.size)
            assertTrue("Watch state failed" in lines.single())
            assertFalse(VIDEO.value in lines.single())
            assertFalse("600000" in lines.single())
        }

    @Test
    fun `a read that fails gives no position and logs one line`() = runTest {
        val progress = progress()
        repository.failReads = true

        val read = async { progress.storedPosition(VIDEO) }
        advanceUntilIdle()

        assertNull(read.await())
        assertEquals(1, lines.size)
        assertFalse(VIDEO.value in lines.single())
    }

    @Test
    fun `crossing 90 percent marks the video and keeps its place`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)

        progress.look(50 * MINUTE_MS, HOUR_MS)
        progress.save(54 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("save 54m", "mark"), repository.calls)
        assertEquals(54.minutes, repository.state.value?.position)
    }

    @Test
    fun `a look that crosses 90 percent marks without saving`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)

        progress.look(50 * MINUTE_MS, HOUR_MS)
        progress.look(57 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("mark"), repository.calls)
    }

    @Test
    fun `a playback that starts past 90 percent marks only at its end`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)

        progress.look(57 * MINUTE_MS, HOUR_MS)
        progress.save(58 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()
        assertEquals(listOf("save 58m"), repository.calls)

        progress.ended()
        advanceUntilIdle()
        assertEquals(listOf("save 58m", "save 0s", "mark"), repository.calls)
    }

    @Test
    fun `a mark by hand clears the crossing, so an unmark stays`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        advanceUntilIdle()

        // Crossed between two looks: the flag is set, and the user marks and unmarks by hand.
        progress.look(50 * MINUTE_MS, HOUR_MS)
        repository.markWatched(VIDEO)
        advanceUntilIdle()
        repository.markUnwatched(VIDEO)
        advanceUntilIdle()
        progress.look(55 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("mark", "unmark"), repository.calls)
    }

    @Test
    fun `the end saves zero and marks, and a stop after it saves zero too`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)

        progress.ended()
        progress.save(HOUR_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("save 0s", "mark", "save 0s"), repository.calls)
    }

    @Test
    fun `playing on after the end saves the position again`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)

        progress.ended()
        progress.movedOn()
        progress.save(2 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("save 0s", "mark", "save 2m"), repository.calls)
    }

    @Test
    fun `closing the mini player saves the position and marks nothing`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        progress.look(0, HOUR_MS)

        // The stop's save, then the items cleared.
        progress.save(3 * MINUTE_MS, HOUR_MS)
        progress.itemChanged(null, null, live = false)
        advanceUntilIdle()

        assertEquals(listOf("save 3m"), repository.calls)
        assertNull(progress.currentVideo())
    }

    @Test
    fun `a link refresh keeps the video and both flags`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        progress.look(50 * MINUTE_MS, HOUR_MS)

        progress.itemChanged("p1", VIDEO, live = false)
        progress.look(55 * MINUTE_MS, HOUR_MS)
        progress.ended()
        progress.itemChanged("p1", VIDEO, live = false)
        progress.save(HOUR_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(VIDEO, progress.currentVideo())
        assertEquals(listOf("mark", "save 0s", "mark", "save 0s"), repository.calls)
    }

    @Test
    fun `a live stream saves and marks nothing, even at its end`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = true)

        progress.look(0, HOUR_MS)
        progress.save(10 * MINUTE_MS, HOUR_MS)
        progress.ended()
        advanceUntilIdle()

        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun `an item with no video saves nothing`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", null, live = false)

        progress.save(10 * MINUTE_MS, HOUR_MS)
        progress.ended()
        advanceUntilIdle()

        assertEquals(emptyList(), repository.calls)
        assertNull(progress.currentVideo())
    }

    @Test
    fun `a replacement that fails leaves an empty, ended player that marks nothing`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        progress.save(8 * MINUTE_MS, HOUR_MS)

        // The new video couldn't play: the player has no item, and reports that it ended.
        progress.itemChanged(null, null, live = false)
        progress.ended()
        progress.save(0, C.TIME_UNSET)
        advanceUntilIdle()

        assertEquals(listOf("save 8m"), repository.calls)
    }

    @Test
    fun `the video ID goes once the item does, after its save was queued`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        assertEquals(VIDEO, progress.currentVideo())

        progress.save(5 * MINUTE_MS, HOUR_MS)
        progress.itemChanged(null, null, live = false)
        assertNull(progress.currentVideo())
        advanceUntilIdle()

        assertEquals(listOf("save 5m"), repository.calls)
    }

    @Test
    fun `a crossing whose write failed marks at the next save`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        progress.look(50 * MINUTE_MS, HOUR_MS)
        repository.failNextWrite = true

        progress.save(54 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()
        progress.save(55 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("save 54m", "save 55m", "mark"), repository.calls)
        assertEquals(55.minutes, repository.state.value?.position)
        assertTrue(repository.state.value?.watchedAt != null)
    }

    @Test
    fun `a mark that failed after its position was saved is asked again`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        progress.look(50 * MINUTE_MS, HOUR_MS)
        repository.failNextMark = true

        progress.save(54 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()
        progress.look(55 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("save 54m", "mark", "mark"), repository.calls)
        assertTrue(repository.state.value?.watchedAt != null)
    }

    @Test
    fun `an end mark that failed is asked again by the stop after it`() = runTest {
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        repository.failNextMark = true

        progress.ended()
        advanceUntilIdle()
        progress.save(HOUR_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("save 0s", "mark", "save 0s", "mark"), repository.calls)
        assertTrue(repository.state.value?.watchedAt != null)
    }

    @Test
    fun `an unmark of a watched video clears what playback saw`() = runTest {
        // Watched on Monday, played again from below 90 percent, then unmarked by hand at 57
        // minutes before playback looked again.
        repository.state.value = WatchState(10.minutes, watchedAt = NOW, updatedAt = NOW)
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        advanceUntilIdle()
        progress.look(50 * MINUTE_MS, HOUR_MS)
        repository.markUnwatched(VIDEO)
        advanceUntilIdle()

        progress.look(57 * MINUTE_MS, HOUR_MS)
        progress.save(58 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("unmark", "save 58m"), repository.calls)
        assertNull(repository.state.value?.watchedAt)
    }

    @Test
    fun `following the mark survives a failed read, so a later unmark still counts`() = runTest {
        repository.failNextReads = 1
        val progress = progress()
        progress.itemChanged("p1", VIDEO, live = false)
        advanceUntilIdle()
        progress.look(50 * MINUTE_MS, HOUR_MS)
        repository.markWatched(VIDEO)
        advanceUntilIdle()
        repository.markUnwatched(VIDEO)
        advanceUntilIdle()

        progress.look(57 * MINUTE_MS, HOUR_MS)
        advanceUntilIdle()

        assertEquals(listOf("mark", "unmark"), repository.calls)
        assertEquals(1, lines.size)
    }

    @Test
    fun `a failed mark stays with its own playback, not the next one of the same video`() =
        runTest {
            val progress = progress()
            progress.itemChanged("p1", VIDEO, live = false)
            val held = CompletableDeferred<Unit>()
            repository.hold = held
            repository.failNextMark = true
            progress.look(50 * MINUTE_MS, HOUR_MS)
            progress.save(54 * MINUTE_MS, HOUR_MS)

            // A new playback of the same video starts past 90 percent before the mark fails.
            progress.itemChanged("p2", VIDEO, live = false)
            held.complete(Unit)
            advanceUntilIdle()
            progress.look(57 * MINUTE_MS, HOUR_MS)
            progress.save(58 * MINUTE_MS, HOUR_MS)
            advanceUntilIdle()

            assertEquals(listOf("save 54m", "mark", "save 58m"), repository.calls)
        }
}
