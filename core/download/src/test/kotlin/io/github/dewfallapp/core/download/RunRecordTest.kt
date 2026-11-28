package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/** A history whose last exit was the user's Stop, with the [record] of the process that ended. */
private class StopHistory(private val record: RunRecord) : AppHistory {
    override fun latestExit() = LatestExit(stopped = true, atMillis = 2_000)

    override fun updatedAtMillis() = 1_000L

    override fun runWasActive() = record.wasActive()

    override fun forgetRun() = record.end()
}

/** How the run record lets a Stop before a run's first claim pause the queue. */
internal class RunRecordTest : RunnerFixture() {
    @Test
    fun `a run records itself only after the stop check, and clears it when it ends`() = runTest {
        waiting(FIRST)
        val checking = CompletableDeferred<Unit>()
        val decided = CompletableDeferred<Unit>()
        val stopCheck = StopCheck {
            checking.complete(Unit)
            decided.await()
        }
        var activeAtBegin = false

        val result = async {
            runner(stopCheck).run(begin = { activeAtBegin = record.active }) {}
        }
        checking.await()
        assertFalse(record.active)
        decided.complete(Unit)

        assertEquals(RunOutcome.DONE, result.await())
        assertFalse(activeAtBegin)
        assertFalse(record.active)
    }

    @Test
    fun `a run is recorded while it downloads`() = runTest {
        waiting(FIRST)
        var activeDuringDownload = false

        runner().run { activeDuringDownload = activeDuringDownload || record.active }

        assertTrue(activeDuringDownload)
        assertFalse(record.active)
    }

    @Test
    fun `a Stop before the run's first claim pauses the waiting downloads`() = runTest {
        // The ended run had begun but claimed nothing, so no row says DOWNLOADING.
        waiting(FIRST)
        record.active = true
        val afterStop =
            DownloadsAfterStop(queue, StopHistory(record), backgroundScope, Logger.Silent)

        afterStop.check()
        afterStop.await()

        assertEquals(QueueState.PAUSED, queue.row(FIRST).state)
        assertFalse(record.active)
    }

    @Test
    fun `a Stop with no run active leaves waiting downloads alone`() = runTest {
        waiting(FIRST)
        val afterStop =
            DownloadsAfterStop(queue, StopHistory(record), backgroundScope, Logger.Silent)

        afterStop.check()
        afterStop.await()

        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
    }

    @Test
    fun `a run that its job's stop cancels mid-download clears its record`() = runTest {
        waiting(FIRST)
        var run: Job? = null
        var activeWhenStopped = false
        // Stop the run once its first range is written, as Android stops a job on a lost
        // connection.
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { n, _ ->
                        if (n == 1) {
                            activeWhenStopped = record.active
                            run?.cancel()
                        }
                        null
                    },
            )

        run = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { runner().run {} }
        run.start()
        run.join()

        assertTrue(activeWhenStopped)
        assertFalse(record.active)
        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
    }
}
