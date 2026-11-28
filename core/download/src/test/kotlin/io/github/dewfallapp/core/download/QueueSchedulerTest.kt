package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

private val VIDEO = VideoId("aaaaaaaaaaa")

class QueueSchedulerTest {
    private val queue = FakeQueue()
    private val runState = DownloadRunState()
    private val launcher = CountingLauncher()

    private fun scheduler(scope: CoroutineScope) =
        QueueScheduler(queue, runState, launcher, FakeBackups(), StopCheck {}, scope, Logger.Silent)

    @Test
    fun `a waiting download schedules a run`() = runTest {
        queue.add(FakeRow(VIDEO, "f"))

        scheduler(backgroundScope).startNow()

        assertEquals(1, launcher.launches)
        assertEquals(RunPhase.SCHEDULED, runState.phase)
    }

    @Test
    fun `nothing waits, nothing is scheduled`() = runTest {
        queue.add(FakeRow(VIDEO, "f", state = QueueState.PAUSED))

        scheduler(backgroundScope).startNow()

        assertEquals(0, launcher.launches)
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `no run is scheduled while one is scheduled or running`() = runTest {
        queue.add(FakeRow(VIDEO, "f"))
        for (phase in listOf(RunPhase.SCHEDULED, RunPhase.RUNNING)) {
            runState.phase = phase

            scheduler(backgroundScope).startNow()

            assertEquals(0, launcher.launches)
            assertEquals(phase, runState.phase)
            // Only a running run marks the ask: a scheduled one claims everything anyway.
            assertEquals(phase == RunPhase.RUNNING, runState.askedDuringRun)
        }
    }

    @Test
    fun `a backup run is handed back to the user-initiated job once, with its rows as they are`() =
        runTest {
            queue.add(FakeRow(VIDEO, "f", state = QueueState.DOWNLOADING))
            runState.phase = RunPhase.RUNNING
            runState.backupRun = true
            val scheduler = scheduler(backgroundScope)

            scheduler.startNow()
            scheduler.startNow()

            assertEquals(1, launcher.launches)
            assertFalse(runState.backupRun)
            assertEquals(QueueState.DOWNLOADING, queue.row(VIDEO).state)
            assertEquals(RunPhase.RUNNING, runState.phase)
        }

    @Test
    fun `a refused hand-back is tried again at the next start`() = runTest {
        queue.add(FakeRow(VIDEO, "f", state = QueueState.DOWNLOADING))
        runState.phase = RunPhase.RUNNING
        runState.backupRun = true
        launcher.accepts = false
        val scheduler = scheduler(backgroundScope)

        scheduler.startNow()
        assertTrue(runState.backupRun)
        launcher.accepts = true
        scheduler.startNow()

        assertEquals(2, launcher.launches)
        assertFalse(runState.backupRun)
    }

    @Test
    fun `a running row left by an ended process waits again and is scheduled`() = runTest {
        queue.add(FakeRow(VIDEO, "f", state = QueueState.DOWNLOADING))

        scheduler(backgroundScope).startNow()

        assertEquals(QueueState.WAITING, queue.row(VIDEO).state)
        assertEquals(1, launcher.launches)
    }

    @Test
    fun `a refused schedule leaves the row waiting for the next start`() = runTest {
        queue.add(FakeRow(VIDEO, "f"))
        launcher.accepts = false
        val scheduler = scheduler(backgroundScope)

        scheduler.startNow()

        assertEquals(QueueState.WAITING, queue.row(VIDEO).state)
        assertEquals(RunPhase.IDLE, runState.phase)
        launcher.accepts = true
        scheduler.startNow()
        assertEquals(2, launcher.launches)
        assertEquals(RunPhase.SCHEDULED, runState.phase)
    }
}

class PausesAfterExitTest {
    @Test
    fun `a stop by the user while a download ran pauses`() {
        assertTrue(pausesAfterExit(LatestExit(stopped = true, atMillis = 2_000), true, 1_000))
    }

    @Test
    fun `a swipe from the recent apps while downloads only waited doesn't pause`() {
        assertFalse(pausesAfterExit(LatestExit(stopped = true, atMillis = 2_000), false, 1_000))
    }

    @Test
    fun `an update at or after the exit doesn't pause`() {
        assertFalse(pausesAfterExit(LatestExit(stopped = true, atMillis = 2_000), true, 2_000))
        assertFalse(pausesAfterExit(LatestExit(stopped = true, atMillis = 2_000), true, 3_000))
    }

    @Test
    fun `a swipe during a backup run with a backup scheduled doesn't pause`() {
        assertFalse(
            pausesAfterExit(
                LatestExit(stopped = true, atMillis = 2_000, foreground = false),
                true,
                1_000,
                backupRanLast = true,
                backupScheduled = true,
            ),
        )
    }

    @Test
    fun `Force stop during a backup run pauses, because it cancelled every backup`() {
        assertTrue(
            pausesAfterExit(
                LatestExit(stopped = true, atMillis = 2_000),
                true,
                1_000,
                backupRanLast = true,
                backupScheduled = false,
            ),
        )
    }

    @Test
    fun `a Stop beside a backup run, while the player's service ran, pauses`() {
        // The player's foreground service made the Task Manager list the app.
        assertTrue(
            pausesAfterExit(
                LatestExit(stopped = true, atMillis = 2_000, foreground = true),
                true,
                1_000,
                backupRanLast = true,
                backupScheduled = true,
            ),
        )
    }

    @Test
    fun `a Stop during a user-initiated run pauses, with its backup scheduled`() {
        assertTrue(
            pausesAfterExit(
                LatestExit(stopped = true, atMillis = 2_000),
                true,
                1_000,
                backupRanLast = false,
                backupScheduled = true,
            ),
        )
    }

    @Test
    fun `another reason or no record doesn't pause`() {
        assertFalse(pausesAfterExit(LatestExit(stopped = false, atMillis = 2_000), true, 1_000))
        assertFalse(pausesAfterExit(null, true, 1_000))
    }
}
