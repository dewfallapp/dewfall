package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.DownloadFileKind
import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.data.playback.StreamLinksRepository
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** How long a test of the run lock waits in real time before it fails. */
private val LOCK_TEST_LIMIT = 10.seconds

/** How a run of the queue stops: pauses, stops by Android or the user, and a second run. */
internal class RunnerStopsTest : RunnerFixture() {
    @Test
    fun `a second run waits until a stopped run has put its download back`() = runTest {
        waiting(FIRST)
        val writing = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { n, _ ->
                        if (n == 1) {
                            writing.complete(Unit)
                            // Bounded, so a request that is never cancelled can't block forever.
                            if (!release.await(LOCK_TEST_LIMIT.inWholeSeconds, TimeUnit.SECONDS)) {
                                throw IOException("Never released")
                            }
                        }
                        null
                    },
            )
        val secondStarted = CompletableDeferred<Unit>()

        try {
            // A real-time limit, so a run that never stops fails the test instead of hanging it.
            withContext(Dispatchers.Default) {
                withTimeout(LOCK_TEST_LIMIT) {
                    val first = launch { runner().run {} }
                    writing.await()
                    val second = async { runner(StopCheck { secondStarted.complete(Unit) }).run {} }
                    delay(200)
                    // The second run hasn't reached the queue while the first one holds its
                    // download.
                    assertFalse(secondStarted.isCompleted)
                    assertEquals(QueueState.DOWNLOADING, queue.row(FIRST).state)
                    first.cancel()
                    first.join()
                    assertEquals(RunOutcome.DONE, second.await())
                }
            }
        } finally {
            // Frees OkHttp's thread that the blocked request holds.
            release.countDown()
        }

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `a run cancelled while the lock is held still ends IDLE`() = runTest {
        waiting(FIRST)
        val writing = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { n, _ ->
                        if (n == 1) {
                            writing.complete(Unit)
                            // Bounded, so a request that is never cancelled can't block forever.
                            if (!release.await(LOCK_TEST_LIMIT.inWholeSeconds, TimeUnit.SECONDS)) {
                                throw IOException("Never released")
                            }
                        }
                        null
                    },
            )

        try {
            withContext(Dispatchers.Default) {
                withTimeout(LOCK_TEST_LIMIT) {
                    val run = launch { runner().run {} }
                    writing.await()
                    runState.mutex.lock()
                    try {
                        run.cancel()
                        delay(100)
                    } finally {
                        // Even when the time limit ends the test, so the run's cleanup can end.
                        runState.mutex.unlock()
                    }
                    run.join()
                }
            }
        } finally {
            // Frees OkHttp's thread that the blocked request holds.
            release.countDown()
        }

        assertEquals(RunPhase.IDLE, runState.phase)
        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
    }

    @Test
    fun `the stop check decides once per process`() = runTest {
        var reads = 0
        val history =
            object : AppHistory {
                override fun latestExit(): LatestExit {
                    reads++
                    return LatestExit(stopped = true, atMillis = 2_000)
                }

                override fun updatedAtMillis() = 1_000L
            }
        val afterStop = DownloadsAfterStop(queue, history, backgroundScope, Logger.Silent)

        afterStop.check()
        afterStop.check()
        afterStop.await()
        afterStop.await()

        assertEquals(1, reads)
    }

    @Test
    fun `a run cancelled by its job's stop leaves the download waiting with its bytes`() = runTest {
        waiting(FIRST)
        var run: Job? = null
        // Stop the run once its first range is written, as Android stops a job.
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { n, _ ->
                        if (n == 1) run?.cancel()
                        null
                    },
            )

        // Started after it is assigned, so the request that stops it already sees it.
        run = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { runner().run {} }
        run.start()
        run.join()

        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
        assertTrue(part(FIRST, DownloadFileKind.VIDEO).length() >= 100L * 1024)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `a run stopped while it waits for the phase ends its schedule`() = runTest {
        stoppedBeforeItBegins(runState.mutex)

        assertEquals(RunPhase.IDLE, runState.phase)
        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
    }

    @Test
    fun `a run stopped while it waits for the run lock ends its schedule`() = runTest {
        stoppedBeforeItBegins(runState.runLock)

        assertEquals(RunPhase.IDLE, runState.phase)
        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
    }

    @Test
    fun `a run stopped while it waits for the stop check ends its schedule`() = runTest {
        waiting(FIRST)
        runState.phase = RunPhase.SCHEDULED
        val checking = CompletableDeferred<Unit>()

        withContext(Dispatchers.Default) {
            withTimeout(LOCK_TEST_LIMIT) {
                val run = launch {
                    runner(
                            StopCheck {
                                checking.complete(Unit)
                                awaitCancellation()
                            },
                        )
                        .run {}
                }
                checking.await()
                run.cancel()
                run.join()
            }
        }

        assertEquals(RunPhase.IDLE, runState.phase)
        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
    }

    /**
     * Starts a run that a start scheduled while the test holds [held], so the run waits for it, and
     * stops the run before it begins.
     */
    private suspend fun stoppedBeforeItBegins(held: Mutex) {
        waiting(FIRST)
        runState.phase = RunPhase.SCHEDULED
        withContext(Dispatchers.Default) {
            withTimeout(LOCK_TEST_LIMIT) {
                held.lock()
                val run =
                    try {
                        launch { runner().run {} }
                            .also {
                                delay(100)
                                it.cancel()
                            }
                    } finally {
                        held.unlock()
                    }
                run.join()
            }
        }
    }

    @Test
    fun `a stop by the user during the reset pauses the queue`() = runTest {
        queue.add(FakeRow(FIRST, FIRST.value + "-folder", state = QueueState.DOWNLOADING))
        waiting(SECOND)
        var run: Job? = null
        queue.afterReset = { run?.cancel() }
        var lockedAtPause: Boolean? = null
        queue.onPause = { lockedAtPause = runState.runLock.isLocked }

        // Started after it is assigned, so the reset already sees it.
        run =
            launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                runner().run(report = {}, stoppedByUser = { true })
            }
        run.start()
        run.join()

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
        assertEquals(QueueState.PAUSED, queue.row(SECOND).state)
        assertEquals(1, queue.pauses)
        // Under the run lock, so no later run claims a row that the pause would take.
        assertEquals(true, lockedAtPause)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `a stop by the user while the run waits for the run lock pauses under it`() = runTest {
        waiting(FIRST)
        var lockedAtPause: Boolean? = null
        queue.onPause = { lockedAtPause = runState.runLock.isLocked }

        withContext(Dispatchers.Default) {
            withTimeout(LOCK_TEST_LIMIT) {
                runState.runLock.lock()
                val run =
                    try {
                        launch { runner().run(report = {}, stoppedByUser = { true }) }
                            .also {
                                delay(100)
                                it.cancel()
                                delay(100)
                                // It waits for the lock to pause.
                                assertEquals(QueueState.WAITING, queue.row(FIRST).state)
                            }
                    } finally {
                        runState.runLock.unlock()
                    }
                run.join()
            }
        }

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
        assertEquals(true, lockedAtPause)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `a stop during the claim puts the claimed download back`() = runTest {
        val row = stoppedDuringClaim(byUser = false)

        assertEquals(QueueState.WAITING, row.state)
        assertEquals(1, row.youtubeTries)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `a stop by the user during the claim pauses the claimed download`() = runTest {
        val row = stoppedDuringClaim(byUser = true)

        assertEquals(QueueState.PAUSED, row.state)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    /** Stops a run right after its claim's write, and returns the claimed row. */
    private suspend fun CoroutineScope.stoppedDuringClaim(byUser: Boolean): FakeRow {
        queue.add(FakeRow(FIRST, FIRST.value + "-folder", youtubeTries = 1))
        var run: Job? = null
        queue.afterClaim = { run?.cancel() }

        // Started after it is assigned, so the claim already sees it.
        run =
            launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                runner().run(report = {}, stoppedByUser = { byUser })
            }
        run.start()
        run.join()

        return queue.row(FIRST)
    }

    @Test
    fun `a job's stop after new bytes starts the tries again`() = runTest {
        assertEquals(0, triesAfterJobStop(atRequest = 1))
    }

    @Test
    fun `a job's stop counts a buffer that was on its way`() = runTest {
        queue.add(FakeRow(FIRST, FIRST.value + "-folder", youtubeTries = 2))
        var run: Job? = null
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { n, chain ->
                        if (n == 0) {
                            lateBuffer(chain, video.copyOfRange(0, 1_000), video.size.toLong()) {
                                run?.cancel()
                            }
                        } else {
                            null
                        }
                    },
            )

        // Started after it is assigned, so the request that stops it already sees it.
        run = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { runner().run {} }
        run.start()
        run.join()

        val row = queue.row(FIRST)
        assertEquals(QueueState.WAITING, row.state)
        assertEquals(1_000, part(FIRST, DownloadFileKind.VIDEO).length())
        assertEquals(0, row.youtubeTries)
    }

    @Test
    fun `a job's stop before any new bytes keeps the tries`() = runTest {
        assertEquals(2, triesAfterJobStop(atRequest = 0))
    }

    /**
     * Runs a download whose tries are at 2 and stops the run as Android stops a job, at the media
     * request numbered [atRequest], and returns the tries that the waiting row keeps.
     */
    private suspend fun CoroutineScope.triesAfterJobStop(atRequest: Int): Int {
        queue.add(FakeRow(FIRST, FIRST.value + "-folder", youtubeTries = 2))
        var run: Job? = null
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { n, _ ->
                        if (n == atRequest) run?.cancel()
                        null
                    },
            )

        // Started after it is assigned, so the first request already sees it.
        run = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { runner().run {} }
        run.start()
        run.join()

        val row = queue.row(FIRST)
        assertEquals(QueueState.WAITING, row.state)
        return row.youtubeTries
    }

    @Test
    fun `a run cancelled by the user's stop pauses every waiting and running download`() = runTest {
        waiting(FIRST)
        waiting(SECOND)
        var run: Job? = null
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { n, _ ->
                        if (n == 1) run?.cancel()
                        null
                    },
            )

        // Started after it is assigned, so the request that stops it already sees it.
        run =
            launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                runner().run(report = {}, stoppedByUser = { true })
            }
        run.start()
        run.join()

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
        assertEquals(QueueState.PAUSED, queue.row(SECOND).state)
        // Once, so no second pause can undo a download that the user went on with in between.
        assertEquals(1, queue.pauses)
        assertTrue(part(FIRST, DownloadFileKind.VIDEO).length() >= 100L * 1024)
    }

    @Test
    fun `a stop check whose pause throws lets the run go on`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))
        queue.failPauseQueued = true
        val history =
            object : AppHistory {
                override fun latestExit() = LatestExit(stopped = true, atMillis = 2_000)

                override fun updatedAtMillis() = 1_000L
            }
        val afterStop = DownloadsAfterStop(queue, history, backgroundScope, Logger.Silent)

        assertEquals(RunOutcome.DONE, run(afterStop))

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
    }

    @Test
    fun `a pause that lands before a failure keeps the row and its files`() = runTest {
        waiting(FIRST)
        part(FIRST, DownloadFileKind.VIDEO)
            .apply { parentFile?.mkdirs() }
            .writeBytes(video.copyOfRange(0, 10))
        val pausedThenPrivate = StreamLinksRepository {
            queue.pause(FIRST)
            YouTubeResult.Failure(YouTubeError.PRIVATE)
        }

        runner(links = pausedThenPrivate).run {}

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
        assertEquals(10, part(FIRST, DownloadFileKind.VIDEO).length())
    }

    @Test
    fun `a pause stops the download with its bytes and the next one runs`() = runTest {
        waiting(FIRST)
        waiting(SECOND)
        val videoMedia =
            FakeMedia(video) { n, _ ->
                if (n == 1) queue.pause(FIRST)
                null
            }
        // The second video's requests come after, so they are answered in full.
        routed = RoutedMedia(mapOf(link(136) to videoMedia, link(251) to FakeMedia(audio)))

        assertEquals(RunOutcome.DONE, run())

        val first = queue.row(FIRST)
        assertEquals(QueueState.PAUSED, first.state)
        assertTrue(part(FIRST, DownloadFileKind.VIDEO).length() >= 100L * 1024)
        assertEquals(QueueState.DOWNLOADED, queue.row(SECOND).state)
    }

    @Test
    fun `a stop by the user decided first leaves the run nothing to do`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))
        val history =
            object : AppHistory {
                override fun latestExit() = LatestExit(stopped = true, atMillis = 2_000)

                override fun updatedAtMillis() = 1_000L
            }
        val afterStop = DownloadsAfterStop(queue, history, backgroundScope, Logger.Silent)

        assertEquals(RunOutcome.DONE, run(afterStop))

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
        assertEquals(0, linkCalls)
    }

    @Test
    fun `a stop check that throws lets the run go on`() = runTest {
        waiting(FIRST)
        val history =
            object : AppHistory {
                override fun latestExit(): LatestExit = error("No records")

                override fun updatedAtMillis() = 0L
            }
        val afterStop = DownloadsAfterStop(queue, history, backgroundScope, Logger.Silent)

        assertEquals(RunOutcome.DONE, run(afterStop))

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
    }
}
